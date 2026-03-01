package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

/**
 * 客户端主动通知服务端停止 DJ 会话的数据包 (客户端 -> 服务端)
 * 用于当客户端检测到 OpenAL source 意外停止时，同步通知服务端终止该玩家的 DJ 会话，
 * 防止服务端继续持有活跃会话导致攻击被错误地按 DJ 模式判定。
 */
public record ClientStopSessionPayload() implements CustomPacketPayload {

    public static final Type<ClientStopSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_stop_session"));

    public static final StreamCodec<FriendlyByteBuf, ClientStopSessionPayload> CODEC = StreamCodec
            .unit(new ClientStopSessionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
