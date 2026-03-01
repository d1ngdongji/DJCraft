package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

/**
 * 播放曲目数据包 (服务器 -> 客户端)
 */
public record PlayTrackPayload(String trackId) implements CustomPacketPayload {

    public static final Type<PlayTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "play_track"));

    public static final StreamCodec<FriendlyByteBuf, PlayTrackPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlayTrackPayload::trackId,
            PlayTrackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
