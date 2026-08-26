package otto.djgun.djcraft.combat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.network.packet.DJTriggerFirePayload;
import otto.djgun.djcraft.network.packet.DJAutoChargeStartPayload;
import otto.djgun.djcraft.combat.DJRayWeaponManager;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.client.render.DJRayEffectRenderer;

/** Handles every data-driven trigger weapon through one client input path. */
@OnlyIn(Dist.CLIENT)
public final class ClientTriggerWeaponHandler {
    private ClientTriggerWeaponHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        var context = DJClientEventContext.resolveTrigger(event);
        if (context.isEmpty()) {
            return;
        }
        var action = context.get();
        var profile = DJRayWeaponManager.resolve(action.stack()).orElse(null);
        if (profile != null && profile.autoChargeBeats() > 0) {
            handleAutoCharge(event, action, profile);
            return;
        }
        DJTriggerWeaponHelper.handlePress(event, action.player(), action.stack(), action.hand(), action.session(),
                result -> {
                    var proof = DJClientJudgmentProofs.create(action.session(), result);
                    if (result.isHit() || action.session().allowsOffBeatAttack()) {
                        DJRayWeaponManager.resolve(action.stack()).ifPresent(rayProfile -> {
                        var origin = action.player().getEyePosition();
                        var maximumEnd = origin.add(action.player().getLookAngle().normalize().scale(rayProfile.range()));
                        var blockHit = action.player().level().clip(new net.minecraft.world.level.ClipContext(
                                origin, maximumEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                net.minecraft.world.level.ClipContext.Fluid.NONE, action.player()));
                        var end = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                                ? maximumEnd : blockHit.getLocation();
                        DJRayEffectRenderer.predict(proof.actionSequence(), action.player().getUUID(),
                                action.hand(), rayProfile.effect(), origin, end);
                        });
                    }
                    PacketDistributor.sendToServer(new DJTriggerFirePayload(
                            proof, action.hand(), action.source()));
                });
    }

    private static void handleAutoCharge(InputEvent.InteractionKeyMappingTriggered event,
            DJClientEventContext action, otto.djgun.djcraft.combat.DJRayWeaponProfile profile) {
        event.setCanceled(true);
        event.setSwingHand(false);
        if (action.player().getCooldowns().isOnCooldown(action.stack().getItem())
                || action.session().getEnergy() < DJItemCooldownManager.getUseEnergyCost(action.stack())) {
            return;
        }
        var judged = BeatJudgeFacade.judgeAttackAndNotify(action.session(), action.stack());
        var effective = judged;
        if (judged.isHit()) {
            var beats = action.session().getTrackPack().timeline().combatLine();
            var schedule = otto.djgun.djcraft.combat.DJAutoChargeTiming.schedule(
                    judged.judgedAtMs(), beats, profile.autoChargeBeats());
            double currentVirtualBeat = otto.djgun.djcraft.util.BeatGridUtil.getVirtualBeat(
                    action.session().getCurrentTimeMs(), beats);
            if (!otto.djgun.djcraft.combat.DJAutoChargeTiming.isSchedulable(schedule, currentVirtualBeat,
                    action.session().getTrackPack().getTotalDurationMs(),
                    action.session().getTrackPack().getOffsetMs())) {
                effective = new otto.djgun.djcraft.combat.HitResult(false, judged.beatData(), judged.beatEvent(),
                        judged.beatIndex(), judged.judgedAtMs());
            }
        }
        var proof = DJClientJudgmentProofs.create(action.session(), effective);
        if (effective.isHit() || action.session().allowsOffBeatAttack()) {
            DJAutoChargeClientState.begin(action.hand(), action.source(), action.stack(), action.session(),
                    profile, effective, proof.actionSequence());
        }
        int cooldownBeats = DJItemCooldownManager.getUseBeatCooldown(action.stack());
        DJTriggerWeaponHelper.applyCooldownBeats(action.player(), action.stack(), action.session(),
                Math.max(0.0, cooldownBeats - 0.4));
        PacketDistributor.sendToServer(new DJAutoChargeStartPayload(proof, action.hand(), action.source()));
    }
}
