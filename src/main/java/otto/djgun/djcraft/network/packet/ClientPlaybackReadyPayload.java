package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record ClientPlaybackReadyPayload(long sessionId, long clientTimeMs) implements CustomPacketPayload {
    public static final Type<ClientPlaybackReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_playback_ready"));
    public static final StreamCodec<FriendlyByteBuf, ClientPlaybackReadyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ClientPlaybackReadyPayload::sessionId,
            ByteBufCodecs.VAR_LONG, ClientPlaybackReadyPayload::clientTimeMs,
            ClientPlaybackReadyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
