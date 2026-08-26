package otto.djgun.djcraft.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import otto.djgun.djcraft.combat.DJItemTimingProfile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncItemTimingPayloadTest {
    @Test
    void roundTripsProfilesWithOptionalFields() {
        SyncItemTimingPayload expected = new SyncItemTimingPayload(Map.of(
                ResourceLocation.parse("minecraft:bow"), new DJItemTimingProfile(2, 1, 0, null, 4.5),
                ResourceLocation.parse("example:weapon"), new DJItemTimingProfile(null, 3, 2.25, null)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncItemTimingPayload.CODEC.encode(buffer, expected);
            assertEquals(expected, SyncItemTimingPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
