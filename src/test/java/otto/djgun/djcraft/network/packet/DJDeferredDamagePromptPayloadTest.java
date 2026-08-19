package otto.djgun.djcraft.network.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class DJDeferredDamagePromptPayloadTest {
    @Test
    void roundTripsSessionIdentity() {
        DJDeferredDamagePromptPayload expected = new DJDeferredDamagePromptPayload(73L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            DJDeferredDamagePromptPayload.CODEC.encode(buffer, expected);
            assertEquals(expected, DJDeferredDamagePromptPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
