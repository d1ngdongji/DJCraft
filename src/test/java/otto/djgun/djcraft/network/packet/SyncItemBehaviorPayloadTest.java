package otto.djgun.djcraft.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncItemBehaviorPayloadTest {
    @Test
    void roundTripsAllBuiltInBehaviors() {
        SyncItemBehaviorPayload payload = new SyncItemBehaviorPayload(Map.of(
                ResourceLocation.parse("example:bow"), ResourceLocation.parse("djcraft:bow"),
                ResourceLocation.parse("example:crossbow"), ResourceLocation.parse("djcraft:crossbow"),
                ResourceLocation.parse("example:shield"), ResourceLocation.parse("djcraft:shield"),
                ResourceLocation.parse("example:charge"), ResourceLocation.parse("djcraft:charge"),
                ResourceLocation.parse("example:trigger"), ResourceLocation.parse("djcraft:trigger"),
                ResourceLocation.parse("example:trident"), ResourceLocation.parse("djcraft:trident"),
                ResourceLocation.parse("example:mace"), ResourceLocation.parse("djcraft:mace"),
                ResourceLocation.parse("example:none"), ResourceLocation.parse("djcraft:none")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SyncItemBehaviorPayload.CODEC.encode(buffer, payload);
        SyncItemBehaviorPayload decoded = SyncItemBehaviorPayload.CODEC.decode(buffer);

        assertEquals(payload, decoded);
    }

    @Test
    void transportsAddonBehaviorIdsWithoutOwningTheirRegistry() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeResourceLocation(ResourceLocation.parse("example:item"));
        buffer.writeResourceLocation(ResourceLocation.parse("example:script"));

        assertEquals(ResourceLocation.parse("example:script"),
                SyncItemBehaviorPayload.CODEC.decode(buffer).overrides()
                        .get(ResourceLocation.parse("example:item")));
    }
}
