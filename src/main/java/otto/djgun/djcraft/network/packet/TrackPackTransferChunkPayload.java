package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferChunkPayload(long transferId, long offset, byte[] data, boolean windowEnd)
        implements CustomPacketPayload {
    public static final int MAX_CHUNK_BYTES = 256 * 1024;
    public static final Type<TrackPackTransferChunkPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_chunk"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferChunkPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.transferId());
                buf.writeVarLong(value.offset());
                buf.writeByteArray(value.data());
                buf.writeBoolean(value.windowEnd());
            },
            buf -> new TrackPackTransferChunkPayload(buf.readLong(), buf.readVarLong(),
                    buf.readByteArray(MAX_CHUNK_BYTES), buf.readBoolean()));

    public TrackPackTransferChunkPayload {
        if (data.length > MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("TrackPack chunk exceeds 256 KiB");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
