package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferFailedPayload(long transferId, TrackPackTransferFailure reason)
        implements CustomPacketPayload {
    public static final Type<TrackPackTransferFailedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_failed"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferFailedPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TrackPackTransferFailedPayload::transferId,
            ByteBufCodecs.idMapper(id -> TrackPackTransferFailure.values()[id], TrackPackTransferFailure::ordinal),
            TrackPackTransferFailedPayload::reason,
            TrackPackTransferFailedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
