package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindReadyPayload(UUID runId, boolean ready, String detail)
        implements CustomPacketPayload {
    public static final Type<CyberGrindReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_ready"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindReadyPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.runId());
                buf.writeBoolean(value.ready());
                buf.writeUtf(value.detail() == null ? "" : value.detail(), 256);
            },
            buf -> new CyberGrindReadyPayload(buf.readUUID(), buf.readBoolean(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
