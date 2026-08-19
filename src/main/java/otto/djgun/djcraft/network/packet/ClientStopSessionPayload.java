package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record ClientStopSessionPayload(long sessionId, long groupPlaybackId,
        StopReason reason) implements CustomPacketPayload {
    public ClientStopSessionPayload(long sessionId, StopReason reason) {
        this(sessionId, 0L, reason);
    }
    public static final Type<ClientStopSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_stop_session"));
    public static final StreamCodec<FriendlyByteBuf, ClientStopSessionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ClientStopSessionPayload::sessionId,
            ByteBufCodecs.VAR_LONG, ClientStopSessionPayload::groupPlaybackId,
            ByteBufCodecs.idMapper(id -> StopReason.values()[id], StopReason::ordinal),
            ClientStopSessionPayload::reason,
            ClientStopSessionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
