package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record StopTrackPayload(long sessionId, StopReason reason) implements CustomPacketPayload {
    public static final Type<StopTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "stop_track"));
    public static final StreamCodec<FriendlyByteBuf, StopTrackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, StopTrackPayload::sessionId,
            ByteBufCodecs.idMapper(id -> StopReason.values()[id], StopReason::ordinal), StopTrackPayload::reason,
            StopTrackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
