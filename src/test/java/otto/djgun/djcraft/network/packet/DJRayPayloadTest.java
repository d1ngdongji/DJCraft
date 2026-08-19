package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.combat.DJRayWeaponProfile;
import otto.djgun.djcraft.combat.DJRayExplosionProfile;

class DJRayPayloadTest {
    @Test
    void roundTripsProfileSnapshot() {
        var payload = new SyncRayWeaponProfilesPayload(Map.of(
                ResourceLocation.parse("djcraft:laser_crossbow"),
                new DJRayWeaponProfile(64.0, 10.0, true,
                        ResourceLocation.parse("djcraft:laser_crossbow"), 5.5, 8.0, 1,
                        new DJRayExplosionProfile(4.0, 16.0, 6.0, 32.0, true))));
        assertEquals(payload, roundTrip(SyncRayWeaponProfilesPayload.CODEC, payload));
    }

    @Test
    void roundTripsAuthoritativeEffect() {
        var payload = new DJRayEffectPayload(42L, UUID.randomUUID(), InteractionHand.OFF_HAND,
                ResourceLocation.parse("djcraft:laser_crossbow"),
                new Vec3(1.0, 2.0, 3.0), new Vec3(1.0, 2.0, 67.0),
                List.of(new Vec3(1.0, 2.0, 8.0), new Vec3(1.0, 2.0, 12.0)), 6.0);
        assertEquals(payload, roundTrip(DJRayEffectPayload.CODEC, payload));
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, payload);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }
}
