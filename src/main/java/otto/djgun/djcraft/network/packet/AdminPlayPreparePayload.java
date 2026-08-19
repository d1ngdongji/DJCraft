package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record AdminPlayPreparePayload(long requestId, String trackId, String contentHash,
        boolean downloadable) implements CustomPacketPayload {
    public static final Type<AdminPlayPreparePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "admin_play_prepare"));
    public static final StreamCodec<FriendlyByteBuf, AdminPlayPreparePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.requestId());
                buf.writeUtf(value.trackId(), 128);
                buf.writeUtf(value.contentHash(), 64);
                buf.writeBoolean(value.downloadable());
            },
            buf -> new AdminPlayPreparePayload(buf.readLong(), buf.readUtf(128),
                    buf.readUtf(64), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
