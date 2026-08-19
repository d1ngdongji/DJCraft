package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record TrackPackTransferCancelPayload(long transferId) implements CustomPacketPayload {
    public static final Type<TrackPackTransferCancelPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "trackpack_transfer_cancel"));
    public static final StreamCodec<FriendlyByteBuf, TrackPackTransferCancelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, TrackPackTransferCancelPayload::transferId,
            TrackPackTransferCancelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
