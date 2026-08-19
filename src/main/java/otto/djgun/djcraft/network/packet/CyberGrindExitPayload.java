package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindExitPayload(UUID runId) implements CustomPacketPayload {
    public static final Type<CyberGrindExitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_exit"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindExitPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUUID(value.runId()),
            buf -> new CyberGrindExitPayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
