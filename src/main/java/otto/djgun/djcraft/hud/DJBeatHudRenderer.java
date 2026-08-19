package otto.djgun.djcraft.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import otto.djgun.djcraft.client.config.BeatPresentationMode;
import otto.djgun.djcraft.client.config.DJClientConfig;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.session.DJSessionClient;

/** Selects the client-local falling or legacy chart without changing judgment semantics. */
@OnlyIn(Dist.CLIENT)
public final class DJBeatHudRenderer {
    private static BeatPresentationMode lastMode;

    private DJBeatHudRenderer() {
    }

    public static void render(GuiGraphics gui) {
        BeatPresentationMode mode = DJClientConfig.beatPresentationMode();
        ensureMode(mode);
        if (mode == BeatPresentationMode.FALLING) {
            DJFallingBeatRenderer.render(gui);
        } else {
            DJCrosshairRenderer.render(gui);
        }
    }

    public static void notifyJudgment(DJSessionClient session, HitResult result) {
        notifyJudgment(session, result, false);
    }

    public static void notifyJudgment(
            DJSessionClient session, HitResult result, boolean categoryMatched) {
        BeatPresentationMode mode = DJClientConfig.beatPresentationMode();
        ensureMode(mode);
        if (mode == BeatPresentationMode.FALLING) {
            DJCrosshairRenderer.notifyCenterCrosshairAnimation();
            DJFallingBeatRenderer.notifyJudgment(session, result, categoryMatched);
        } else {
            DJCrosshairRenderer.notifyJudgment(result.isHit(), result.beatEvent(), result.judgedAtMs());
        }
    }

    public static void reset() {
        DJFallingBeatRenderer.reset();
        DJCrosshairRenderer.reset();
        lastMode = null;
    }

    private static void ensureMode(BeatPresentationMode mode) {
        if (mode == lastMode) {
            return;
        }
        DJFallingBeatRenderer.reset();
        DJCrosshairRenderer.reset();
        lastMode = mode;
    }
}
