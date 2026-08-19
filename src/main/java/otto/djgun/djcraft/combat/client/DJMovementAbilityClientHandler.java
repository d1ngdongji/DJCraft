package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.client.KeyBindings;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.combat.DJAirMovement;
import otto.djgun.djcraft.combat.DJDashMomentum;
import otto.djgun.djcraft.combat.DJMovementAbilityRules;
import otto.djgun.djcraft.network.packet.DJMovementAbility;
import otto.djgun.djcraft.network.packet.DJMovementAbilityPayload;
import otto.djgun.djcraft.network.packet.DJDashDirection;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;

@OnlyIn(Dist.CLIENT)
public final class DJMovementAbilityClientHandler {
    private static boolean jumpWasDown;
    private static boolean shiftWasDown;
    private static boolean wasAirborneLastTick;
    private static long trackedSessionId = -1L;

    private DJMovementAbilityClientHandler() {
    }

    public static void tick(Minecraft minecraft) {
        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !jumpWasDown;
        jumpWasDown = jumpDown;
        boolean shiftDown = minecraft.options.keyShift.isDown();
        boolean shiftPressed = shiftDown && !shiftWasDown;
        shiftWasDown = shiftDown;

        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (minecraft.player == null) {
            wasAirborneLastTick = false;
            drainDashClicks();
            return;
        }
        boolean wasAlreadyAirborne = wasAirborneLastTick;
        wasAirborneLastTick = !minecraft.player.onGround();
        if (session == null) {
            trackedSessionId = -1L;
            drainDashClicks();
            return;
        }
        session.tickMovementState();
        if (minecraft.screen != null) {
            drainDashClicks();
            return;
        }
        if (trackedSessionId != session.getSessionId()) {
            trackedSessionId = session.getSessionId();
        }

        while (KeyBindings.DASH.consumeClick()) {
            tryDash(minecraft, session);
        }
        if (jumpPressed) {
            if (minecraft.player.onGround()) {
                tryGroundJump(minecraft, session);
            } else if (wasAlreadyAirborne) {
                tryDoubleJump(minecraft, session);
            }
        }
        if (shiftPressed && wasAlreadyAirborne) {
            tryGroundSlam(minecraft, session);
        }
    }

    public static void reset() {
        jumpWasDown = false;
        shiftWasDown = false;
        wasAirborneLastTick = false;
        trackedSessionId = -1L;
        DJClientDashMomentumState.reset();
        drainDashClicks();
    }

    private static void tryDash(Minecraft minecraft, DJSessionClient session) {
        DJDashDirection direction = DJDashDirection.fromInput(
                minecraft.player.input.up, minecraft.player.input.down,
                minecraft.player.input.left, minecraft.player.input.right);
        boolean floweryEquipped = otto.djgun.djcraft.combat.FloweryDashController.isEquipped(minecraft.player);
        double energyCost = floweryEquipped
                ? DJMovementAbilityRules.FLOWERY_DASH_ENERGY_COST
                : DJMovementAbilityRules.NORMAL_DASH_ENERGY_COST;
        if (session.getDashCooldownTicks() > 0
                || session.getEnergy() < energyCost
                || !DJMovementAbilityRules.canDash(minecraft.player)
                || direction == DJDashDirection.NONE) {
            return;
        }
        var result = BeatJudgeFacade.judgeAndNotify(session, DJItemBehaviorRegistry.DASH);
        session.predictDash(DJMovementAbilityRules.MAX_CONSECUTIVE_DASHES,
                DJMovementAbilityRules.DASH_COOLDOWN_TICKS);
        double directionalImpulse = Config.DASH_HORIZONTAL_SPEED.get()
                * (floweryEquipped
                        ? otto.djgun.djcraft.combat.FloweryDashController.SPEED_MULTIPLIER : 1.0)
                * (floweryEquipped
                        ? DJMovementAbilityRules.FLOWERY_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER
                        : DJMovementAbilityRules.NORMAL_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER);
        int momentumLockTicks = floweryEquipped
                ? DJMovementAbilityRules.FLOWERY_DASH_MOMENTUM_LOCK_TICKS
                : DJMovementAbilityRules.NORMAL_DASH_MOMENTUM_LOCK_TICKS;
        DJClientDashMomentumState.predict(minecraft.player,
                DJDashMomentum.compose(minecraft.player.getDeltaMovement(), direction,
                        minecraft.player.getYRot(), directionalImpulse),
                momentumLockTicks);
        PacketDistributor.sendToServer(new DJMovementAbilityPayload(
                DJClientJudgmentProofs.createNonOffensive(session, result), DJMovementAbility.DASH, direction));
    }

    private static void tryDoubleJump(Minecraft minecraft, DJSessionClient session) {
        if (session.getRemainingAirJumps() <= 0
                || !DJMovementAbilityRules.canDoubleJump(minecraft.player)) {
            return;
        }
        var result = BeatJudgeFacade.judgeAndNotify(session, DJItemBehaviorRegistry.DOUBLE_JUMP);
        DJDashDirection direction = currentDirection(minecraft);
        if (result.isHit()) {
            session.predictAirJumpUsed();
            DJClientDashMomentumState.addAirImpulse(minecraft.player, DJAirMovement.doubleJump(
                    direction, minecraft.player.getYRot(),
                    DJMovementAbilityRules.doubleJumpPower(minecraft.player)));
        }
        PacketDistributor.sendToServer(new DJMovementAbilityPayload(
                DJClientJudgmentProofs.createNonOffensive(session, result), DJMovementAbility.DOUBLE_JUMP,
                direction));
    }

    private static void tryGroundJump(Minecraft minecraft, DJSessionClient session) {
        if (!DJMovementAbilityRules.canGroundJump(minecraft.player)) {
            return;
        }
        DJDashDirection direction = currentDirection(minecraft);
        DJClientDashMomentumState.addAirImpulse(minecraft.player, DJAirMovement.doubleJump(
                direction, minecraft.player.getYRot(),
                DJMovementAbilityRules.doubleJumpPower(minecraft.player)));
        PacketDistributor.sendToServer(new DJMovementAbilityPayload(
                DJClientJudgmentProofs.createUnjudged(session), DJMovementAbility.GROUND_JUMP,
                direction));
    }

    private static void tryGroundSlam(Minecraft minecraft, DJSessionClient session) {
        if (!DJMovementAbilityRules.canGroundSlam(minecraft.player)) {
            return;
        }
        var result = BeatJudgeFacade.judgeAndNotify(session, DJItemBehaviorRegistry.GROUND_SLAM);
        PacketDistributor.sendToServer(new DJMovementAbilityPayload(
                DJClientJudgmentProofs.createNonOffensive(session, result), DJMovementAbility.GROUND_SLAM,
                DJDashDirection.NONE));
    }

    private static DJDashDirection currentDirection(Minecraft minecraft) {
        return DJDashDirection.fromInput(
                minecraft.player.input.up, minecraft.player.input.down,
                minecraft.player.input.left, minecraft.player.input.right);
    }

    private static void drainDashClicks() {
        while (KeyBindings.DASH.consumeClick()) {
            // Prevent clicks made outside an active DJ session from being replayed later.
        }
    }
}
