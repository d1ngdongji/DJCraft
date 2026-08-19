package otto.djgun.djcraft.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.client.animation.DJAnimationEvent;
import otto.djgun.djcraft.client.animation.DJAnimationRuntime;
import otto.djgun.djcraft.client.render.DJRayEffectRenderer;
import otto.djgun.djcraft.combat.DJActionSource;
import otto.djgun.djcraft.combat.DJAutoChargeTiming;
import otto.djgun.djcraft.combat.DJRayWeaponManager;
import otto.djgun.djcraft.combat.DJRayWeaponProfile;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.session.DJModeManagerClient;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.client.sound.DJWeaponSoundRuntime;
import otto.djgun.djcraft.util.BeatGridUtil;

/** Local prediction, animation, and staged-model state for an automatic charge. */
@OnlyIn(Dist.CLIENT)
public final class DJAutoChargeClientState {
    private static Pending pending;

    private DJAutoChargeClientState() {
    }

    public static boolean begin(InteractionHand hand, DJActionSource source, ItemStack stack,
            DJSessionClient session, DJRayWeaponProfile profile, HitResult result, long actionSequence) {
        cancel(Minecraft.getInstance());
        var beats = session.getTrackPack().timeline().combatLine();
        DJAutoChargeTiming.Schedule schedule = DJAutoChargeTiming.schedule(
                result.judgedAtMs(), beats, profile.autoChargeBeats());
        double currentVirtualBeat = BeatGridUtil.getVirtualBeat(session.getCurrentTimeMs(), beats);
        if (result.beatEvent() == null || !DJAutoChargeTiming.isSchedulable(schedule, currentVirtualBeat,
                session.getTrackPack().getTotalDurationMs(), session.getTrackPack().getOffsetMs())) {
            return false;
        }
        pending = new Pending(session.getSessionId(), hand, source, stack.copy(), profile,
                result, actionSequence, schedule.startVirtualBeat(), schedule.targetVirtualBeat());
        DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.CHARGE_START, hand, stack, session,
                profile.autoChargeBeats(), DJActionOutcome.judged(result, false),
                actionSequence, result.judgedAtMs(), result.beatIndex());
        return true;
    }

    public static void tick(Minecraft minecraft) {
        if (pending == null) {
            return;
        }
        if (!isValid(minecraft)) {
            cancel(minecraft);
            return;
        }
        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null) {
            cancel(minecraft);
            return;
        }
        double currentVirtualBeat = BeatGridUtil.getVirtualBeat(session.getCurrentTimeMs(),
                session.getTrackPack().timeline().combatLine());
        if (DJAutoChargeTiming.isDue(currentVirtualBeat, pending.targetVirtualBeat())) {
            fire(minecraft, session);
        }
    }

    private static void fire(Minecraft minecraft, DJSessionClient session) {
        Pending firing = pending;
        pending = null;
        ItemStack stack = firing.source().resolve(minecraft.player, firing.hand());
        if (stack.isEmpty()) {
            return;
        }
        Vec3 origin = minecraft.player.getEyePosition();
        Vec3 maximumEnd = origin.add(minecraft.player.getLookAngle().normalize().scale(firing.profile().range()));
        var blockHit = minecraft.player.level().clip(new ClipContext(origin, maximumEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        Vec3 end = blockHit.getType() == Type.MISS ? maximumEnd : blockHit.getLocation();
        double predictedRadius = firing.profile().explosion() == null
                ? 0.0 : firing.profile().explosion().radius();
        DJRayEffectRenderer.predict(firing.actionSequence(), minecraft.player.getUUID(), firing.hand(),
                firing.profile().effect(), origin, end, predictedRadius);
        DJAnimationRuntime.getInstance().emit(DJAnimationEvent.Kind.TRIGGER_IMPACT, firing.hand(), stack,
                session, 1.0, DJActionOutcome.judged(firing.result(), false), firing.actionSequence(),
                firing.result().judgedAtMs(), firing.result().beatIndex());
    }

    public static float progress(ItemStack stack) {
        Pending current = pending;
        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (current == null || session == null || session.getSessionId() != current.sessionId()
                || !ItemStack.isSameItemSameComponents(stack, current.stackSnapshot())) {
            return 0.0F;
        }
        double currentVirtualBeat = BeatGridUtil.getVirtualBeat(session.getCurrentTimeMs(),
                session.getTrackPack().timeline().combatLine());
        return DJAutoChargeTiming.progress(currentVirtualBeat,
                current.startVirtualBeat(), current.targetVirtualBeat());
    }

    public static void reset() {
        cancel(Minecraft.getInstance());
    }

    private static void cancel(Minecraft minecraft) {
        Pending canceled = pending;
        pending = null;
        if (canceled != null && minecraft.player != null) {
            DJWeaponSoundRuntime.getInstance().stopCharge(minecraft.player.getUUID(),
                    canceled.actionSequence(), canceled.hand(), canceled.stackSnapshot());
        }
    }

    private static boolean isValid(Minecraft minecraft) {
        if (pending == null || minecraft.player == null) {
            return false;
        }
        DJSessionClient session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        if (session == null || session.getSessionId() != pending.sessionId()) {
            return false;
        }
        if (pending.hand() == InteractionHand.MAIN_HAND
                && minecraft.player.getInventory().selected != pending.source().slot()) {
            return false;
        }
        ItemStack live = pending.source().resolve(minecraft.player, pending.hand());
        return !live.isEmpty() && ItemStack.isSameItemSameComponents(live, pending.stackSnapshot())
                && DJRayWeaponManager.resolve(live).map(profile -> profile.autoChargeBeats() > 0).orElse(false);
    }

    private record Pending(long sessionId, InteractionHand hand, DJActionSource source,
            ItemStack stackSnapshot, DJRayWeaponProfile profile, HitResult result, long actionSequence,
            double startVirtualBeat, double targetVirtualBeat) {
        private Pending {
            stackSnapshot = stackSnapshot.copy();
        }
    }
}
