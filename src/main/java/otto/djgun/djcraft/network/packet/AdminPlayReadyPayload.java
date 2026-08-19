package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record AdminPlayReadyPayload(long requestId, boolean ready, String contentHash,
        String detail) implements CustomPacketPayload {
    public static final Type<AdminPlayReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "admin_play_ready"));
    public static final StreamCodec<FriendlyByteBuf, AdminPlayReadyPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.requestId());
                buf.writeBoolean(value.ready());
                buf.writeUtf(value.contentHash(), 64);
                buf.writeUtf(value.detail(), 256);
            },
            buf -> new AdminPlayReadyPayload(buf.readLong(), buf.readBoolean(),
                    buf.readUtf(64), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
