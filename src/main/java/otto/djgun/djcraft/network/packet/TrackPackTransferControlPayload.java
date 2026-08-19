package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferControlPayload(long transferId, boolean paused)
        implements CustomPacketPayload {
    public static final Type<TrackPackTransferControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_control"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferControlPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.transferId());
                buf.writeBoolean(value.paused());
            },
            buf -> new TrackPackTransferControlPayload(buf.readLong(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
