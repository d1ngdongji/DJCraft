package otto.djgun.djcraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;

class DJRayEffectRendererMathTest {
    @Test
    void predictionAndAuthorityAreIdempotentInEitherArrivalOrder() {
        UUID shooter = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        ResourceLocation effect = ResourceLocation.fromNamespaceAndPath("djcraft", "explosive_bow");
        Vec3 origin = new Vec3(0.0, 1.0, 0.0);
        Vec3 end = new Vec3(10.0, 1.0, 0.0);
        var payload = new DJRayEffectPayload(42L, shooter, InteractionHand.MAIN_HAND,
                effect, origin, end, List.of(), 4.0);

        DJRayEffectRenderer.clear();
        DJRayEffectRenderer.predict(42L, shooter, InteractionHand.MAIN_HAND, effect, origin, end, 4.0);
        DJRayEffectRenderer.acceptAuthoritative(payload);
        assertEquals(1, DJRayEffectRenderer.queuedEffectCount());

        DJRayEffectRenderer.clear();
        DJRayEffectRenderer.acceptAuthoritative(payload);
        DJRayEffectRenderer.predict(42L, shooter, InteractionHand.MAIN_HAND, effect, origin, end, 4.0);
        DJRayEffectRenderer.acceptAuthoritative(payload);
        assertEquals(1, DJRayEffectRenderer.queuedEffectCount());
        DJRayEffectRenderer.clear();
    }

    @Test
    void positiveHandOffsetMapsToCameraRight() {
        Vec3 result = DJRayEffectRenderer.applyCameraOffset(
                Vec3.ZERO, new Vec3(0.0, 0.0, 10.0), new Vec3(0.25, -0.1, 0.5));

        assertEquals(-0.25, result.x, 1.0E-9);
        assertEquals(-0.1, result.y, 1.0E-9);
        assertEquals(0.5, result.z, 1.0E-9);
    }

    @Test
    void centeredMuzzleHasNoHorizontalDisplacement() {
        Vec3 result = DJRayEffectRenderer.applyCameraOffset(
                Vec3.ZERO, new Vec3(0.0, 0.0, 10.0), new Vec3(0.0, -0.1, 0.5));

        assertEquals(0.0, result.x, 1.0E-9);
    }

    @Test
    void burstExpandsToPeakThenContracts() {
        assertEquals(0.12F, DJRayEffectRenderer.burstRadius(0.12F, 0.75F, 0.0F), 1.0E-6F);
        assertEquals(0.75F, DJRayEffectRenderer.burstRadius(0.12F, 0.75F, 0.5F), 1.0E-6F);
        assertEquals(0.12F, DJRayEffectRenderer.burstRadius(0.12F, 0.75F, 1.0F), 1.0E-6F);
        assertEquals(
                DJRayEffectRenderer.burstRadius(0.12F, 0.75F, 0.25F),
                DJRayEffectRenderer.burstRadius(0.12F, 0.75F, 0.75F),
                1.0E-6F);
    }

    @Test
    void shockwaveExpandsMonotonicallyWithEaseOut() {
        assertEquals(0.25F, DJRayEffectRenderer.shockwaveRadius(0.25F, 4.0F, 0.0F), 1.0E-6F);
        assertEquals(4.0F, DJRayEffectRenderer.shockwaveRadius(0.25F, 4.0F, 1.0F), 1.0E-6F);
        float quarter = DJRayEffectRenderer.shockwaveRadius(0.25F, 4.0F, 0.25F);
        float half = DJRayEffectRenderer.shockwaveRadius(0.25F, 4.0F, 0.5F);
        assertEquals(true, quarter > 0.25F && half > quarter && half < 4.0F);
    }
}
