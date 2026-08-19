package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupReadyPayload(UUID groupId, boolean ready, String detail) implements CustomPacketPayload {
    public static final Type<DJGroupReadyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_ready"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupReadyPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.groupId());
                buf.writeBoolean(value.ready());
                buf.writeUtf(value.detail(), 256);
            },
            buf -> new DJGroupReadyPayload(buf.readUUID(), buf.readBoolean(), buf.readUtf(256)));

    public DJGroupReadyPayload {
        detail = detail == null ? "" : detail;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
