package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferAckPayload(long transferId, long nextOffset) implements CustomPacketPayload {
    public static final Type<TrackPackTransferAckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_ack"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferAckPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TrackPackTransferAckPayload::transferId,
            ByteBufCodecs.VAR_LONG, TrackPackTransferAckPayload::nextOffset,
            TrackPackTransferAckPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
