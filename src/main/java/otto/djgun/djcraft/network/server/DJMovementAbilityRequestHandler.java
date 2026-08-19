package otto.djgun.djcraft.network.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.combat.DJJudgmentVerifier;
import otto.djgun.djcraft.combat.DJAirMovement;
import otto.djgun.djcraft.combat.DJFallDamageImmunity;
import otto.djgun.djcraft.combat.DJMovementAbilityRules;
import otto.djgun.djcraft.network.packet.DJMovementAbilityPayload;
import otto.djgun.djcraft.network.packet.DJDoubleJumpImpulsePayload;
import otto.djgun.djcraft.network.packet.DJDashAfterimagePayload;
import otto.djgun.djcraft.network.packet.DJDashMomentumPayload;
import otto.djgun.djcraft.network.packet.FloweryDashEffectPayload;
import otto.djgun.djcraft.network.packet.DJDashDirection;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public final class DJMovementAbilityRequestHandler {
    private static final ResourceLocation FLOWERY_SOUND_PROFILE =
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "flowery");

    private DJMovementAbilityRequestHandler() {
    }

    public static void handle(DJMovementAbilityPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var verification = DJJudgmentVerifier.verify(
                player, payload.proof(), disabledByCanAttack(payload));
        DJSession session = verification.session();
        if (session == null || !verification.accepted()) {
            return;
        }

        switch (payload.ability()) {
            case DASH -> handleDash(player, session, verification.result(), payload.proof().actionSequence(),
                    payload.dashDirection());
            case GROUND_JUMP -> handleGroundJump(player, session,
                    payload.proof().actionSequence(), payload.dashDirection());
            case DOUBLE_JUMP -> handleDoubleJump(player, session, verification.result(),
                    payload.proof().actionSequence(), payload.dashDirection());
            case GROUND_SLAM -> handleGroundSlam(player, session, verification.result(),
                    payload.proof().actionSequence());
        }
        session.sendMovementState();
        stopAfterAction(player, verification);
    }

    private static void handleDash(ServerPlayer player, DJSession session, HitResult result,
            long actionSequence, DJDashDirection direction) {
        long gameTime = player.level().getGameTime();
        boolean floweryEquipped = otto.djgun.djcraft.combat.FloweryDashController.isEquipped(player);
        double energyCost = floweryEquipped
                ? DJMovementAbilityRules.FLOWERY_DASH_ENERGY_COST
                : DJMovementAbilityRules.NORMAL_DASH_ENERGY_COST;
        if (!DJMovementAbilityRules.canDash(player) || !session.getMovementState().canDash(gameTime)
                || direction == DJDashDirection.NONE
                || !session.tryConsumeEnergy(energyCost)) {
            return;
        }
        DJMovementAbilityRules.consumeDashHunger(player);

        double directionalImpulse = Config.DASH_HORIZONTAL_SPEED.get()
                * (floweryEquipped ? otto.djgun.djcraft.combat.FloweryDashController.SPEED_MULTIPLIER : 1.0)
                * (floweryEquipped
                        ? DJMovementAbilityRules.FLOWERY_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER
                        : DJMovementAbilityRules.NORMAL_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER);
        Vec3 momentum = otto.djgun.djcraft.combat.DJDashMomentum.compose(
                player.getDeltaMovement(), direction, player.getYRot(), directionalImpulse);
        int momentumLockTicks = floweryEquipped
                ? DJMovementAbilityRules.FLOWERY_DASH_MOMENTUM_LOCK_TICKS
                : DJMovementAbilityRules.NORMAL_DASH_MOMENTUM_LOCK_TICKS;
        otto.djgun.djcraft.combat.DJDashMomentumController.activate(
                player, momentum, momentumLockTicks);
        PacketDistributor.sendToPlayer(player,
                new DJDashMomentumPayload(momentum, momentumLockTicks));

        if (floweryEquipped) {
            otto.djgun.djcraft.combat.FloweryDashController.activate(
                    player, session.getSessionId(), actionSequence);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new FloweryDashEffectPayload(player.getUUID(),
                            otto.djgun.djcraft.combat.FloweryDashController.EFFECT_DURATION_TICKS));
        } else {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new DJDashAfterimagePayload(player.getUUID()));
        }
        session.getMovementState().recordDashAndMaybeStartCooldown(gameTime,
                DJMovementAbilityRules.MAX_CONSECUTIVE_DASHES,
                DJMovementAbilityRules.DASH_COOLDOWN_TICKS);
        DJGameplaySoundBroadcaster.broadcast(player, actionSequence, net.minecraft.world.InteractionHand.MAIN_HAND,
                DJWeaponSoundSemantic.DASH,
                floweryEquipped ? FLOWERY_SOUND_PROFILE
                        : otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry.GENERIC,
                result.isHit() ? BeatOutcome.HIT : BeatOutcome.MISS,
                TargetOutcome.NOT_APPLICABLE);
    }

    private static boolean disabledByCanAttack(DJMovementAbilityPayload payload) {
        return switch (payload.ability()) {
            case DASH -> DJItemBehaviorRegistry.DASH.disabledByCanAttack();
            case GROUND_JUMP -> DJItemBehaviorRegistry.GROUND_JUMP.disabledByCanAttack();
            case DOUBLE_JUMP -> DJItemBehaviorRegistry.DOUBLE_JUMP.disabledByCanAttack();
            case GROUND_SLAM -> DJItemBehaviorRegistry.GROUND_SLAM.disabledByCanAttack();
        };
    }

    private static void handleDoubleJump(ServerPlayer player, DJSession session, HitResult result,
            long actionSequence, DJDashDirection direction) {
        if (!result.isHit() || !DJMovementAbilityRules.canDoubleJump(player)
                || session.getMovementState().remainingAirJumps() <= 0) {
            return;
        }
        if (!session.getMovementState().tryUseAirJump()) {
            return;
        }

        applyJump(player, session, actionSequence, direction, BeatOutcome.HIT);
    }

    private static void handleGroundJump(ServerPlayer player, DJSession session,
            long actionSequence, DJDashDirection direction) {
        if (!DJMovementAbilityRules.canGroundJump(player)) {
            return;
        }

        applyJump(player, session, actionSequence, direction, BeatOutcome.NOT_APPLICABLE);
    }

    private static void applyJump(ServerPlayer player, DJSession session, long actionSequence,
            DJDashDirection direction, BeatOutcome beatOutcome) {
        Vec3 airImpulse = DJAirMovement.doubleJump(
                direction, player.getYRot(), DJMovementAbilityRules.doubleJumpPower(player));
        var movement = otto.djgun.djcraft.combat.DJDashMomentumController.addAirImpulse(player, airImpulse);
        DJFallDamageImmunity.arm(player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new DJDoubleJumpImpulsePayload(session.getSessionId(), movement.velocity(),
                        movement.remainingTravelTicks()));
        DJGameplaySoundBroadcaster.broadcast(player, actionSequence, net.minecraft.world.InteractionHand.MAIN_HAND,
                DJWeaponSoundSemantic.DOUBLE_JUMP, beatOutcome, TargetOutcome.NOT_APPLICABLE);
    }

    private static void handleGroundSlam(ServerPlayer player, DJSession session, HitResult result,
            long actionSequence) {
        if (!DJMovementAbilityRules.canGroundSlam(player)
                || !otto.djgun.djcraft.combat.DJGroundSlamController.activate(
                        player, session, actionSequence, result.isHit())) {
            return;
        }

        Vec3 velocity = DJAirMovement.groundSlam();
        otto.djgun.djcraft.combat.DJDashMomentumController.cancel(player.getUUID());
        player.setDeltaMovement(velocity);
        player.resetFallDistance();
        player.hasImpulse = true;
        player.hurtMarked = true;
        DJFallDamageImmunity.arm(player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new DJDoubleJumpImpulsePayload(session.getSessionId(), velocity, 0));
    }

    private static void stopAfterAction(ServerPlayer player, DJJudgmentVerifier.Verification verification) {
        if (!verification.stopAfterAction()) {
            return;
        }
        player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
        DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
        DJCraft.LOGGER.warn("Stopped DJ session for {} after five clock anomalies",
                player.getName().getString());
    }
}
