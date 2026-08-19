package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.DiscPlaybackReference;

import java.util.UUID;

public record ClientRequestPlayPayload(DiscPlaybackReference disc) implements CustomPacketPayload {
    public static final Type<ClientRequestPlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_request_play"));
    public static final StreamCodec<FriendlyByteBuf, ClientRequestPlayPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.disc().trackId(), 128);
                buf.writeBoolean(value.disc().discId() != null);
                if (value.disc().discId() != null) {
                    buf.writeUUID(value.disc().discId());
                }
                buf.writeVarInt(value.disc().jukeboxInventorySlot());
                buf.writeVarInt(value.disc().discSlot());
            },
            buf -> {
                String trackId = buf.readUtf(128);
                UUID discId = buf.readBoolean() ? buf.readUUID() : null;
                return new ClientRequestPlayPayload(new DiscPlaybackReference(
                        trackId, discId, buf.readVarInt(), buf.readVarInt()));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
