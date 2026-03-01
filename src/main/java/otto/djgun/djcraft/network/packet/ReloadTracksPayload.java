package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

/**
 * 重新加载数据包 (服务器 -> 客户端)
 */
public record ReloadTracksPayload() implements CustomPacketPayload {

    public static final Type<ReloadTracksPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "reload_tracks"));

    public static final StreamCodec<FriendlyByteBuf, ReloadTracksPayload> CODEC = StreamCodec
            .unit(new ReloadTracksPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
