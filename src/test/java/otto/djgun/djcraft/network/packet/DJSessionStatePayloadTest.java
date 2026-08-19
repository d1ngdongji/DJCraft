package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class DJSessionStatePayloadTest {
    @Test
    void roundTripsOffBeatDamageAndToleranceMaximum() {
        DJSessionStatePayload payload = new DJSessionStatePayload(
                42L, 7, 3, 12.5, 50.0, 1, 3, 25);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DJSessionStatePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, DJSessionStatePayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
