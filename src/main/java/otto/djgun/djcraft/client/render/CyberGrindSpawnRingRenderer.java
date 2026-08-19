package otto.djgun.djcraft.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import otto.djgun.djcraft.client.CyberGrindClientState;

/** Emits a coherent client-side ground ring for each authoritative pending spawn. */
public final class CyberGrindSpawnRingRenderer {
    private static long lastEmissionNanos;

    private CyberGrindSpawnRingRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastEmissionNanos < 50_000_000L) {
            return;
        }
        lastEmissionNanos = now;
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        for (var warning : CyberGrindClientState.getInstance().warnings()) {
            float progress = warning.progress(now);
            double radius = warning.radius() * (0.85 + 0.15 * Math.sin(progress * Math.PI * 8.0));
            for (int index = 0; index < 32; index++) {
                double angle = Math.PI * 2.0 * index / 32.0;
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        warning.x() + Math.cos(angle) * radius,
                        warning.y(), warning.z() + Math.sin(angle) * radius,
                        0.0, 0.015 + progress * 0.01, 0.0);
            }
        }
    }
}
