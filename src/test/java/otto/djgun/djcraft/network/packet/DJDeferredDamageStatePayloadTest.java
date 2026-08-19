package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class DJDeferredDamageStatePayloadTest {
    @Test
    void roundTripsPendingState() {
        DJDeferredDamageStatePayload expected = new DJDeferredDamageStatePayload(73L, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DJDeferredDamageStatePayload.CODEC.encode(buffer, expected);
            assertEquals(expected, DJDeferredDamageStatePayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
