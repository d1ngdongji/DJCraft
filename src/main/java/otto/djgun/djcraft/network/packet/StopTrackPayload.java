package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

/**
 * 停止播放数据包 (服务器 -> 客户端)
 */
public record StopTrackPayload() implements CustomPacketPayload {

    public static final Type<StopTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "stop_track"));

    public static final StreamCodec<FriendlyByteBuf, StopTrackPayload> CODEC = StreamCodec.unit(new StopTrackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
