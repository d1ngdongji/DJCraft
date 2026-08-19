package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindSpawnWarningPayload(UUID warningId, double x, double y, double z,
        float radius, int durationTicks) implements CustomPacketPayload {
    public static final Type<CyberGrindSpawnWarningPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_spawn_warning"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindSpawnWarningPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.warningId());
                buf.writeDouble(value.x());
                buf.writeDouble(value.y());
                buf.writeDouble(value.z());
                buf.writeFloat(value.radius());
                buf.writeVarInt(value.durationTicks());
            },
            buf -> new CyberGrindSpawnWarningPayload(buf.readUUID(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
