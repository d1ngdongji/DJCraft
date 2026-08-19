package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferStartPayload(long transferId, String packId, long totalSize, String sha256)
        implements CustomPacketPayload {
    public static final Type<TrackPackTransferStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_start"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferStartPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.transferId());
                buf.writeUtf(value.packId(), 128);
                buf.writeVarLong(value.totalSize());
                buf.writeUtf(value.sha256(), 64);
            },
            buf -> new TrackPackTransferStartPayload(buf.readLong(), buf.readUtf(128), buf.readVarLong(),
                    buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
