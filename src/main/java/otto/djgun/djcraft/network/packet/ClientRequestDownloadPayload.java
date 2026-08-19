package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record ClientRequestDownloadPayload(String packId) implements CustomPacketPayload {
    public static final Type<ClientRequestDownloadPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_request_download"));
    public static final StreamCodec<FriendlyByteBuf, ClientRequestDownloadPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.packId(), 128),
            buf -> new ClientRequestDownloadPayload(buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
