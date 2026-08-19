package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindStatePayload(boolean active, UUID runId, String profileName,
        int wave, int completedWaves, int livingWeight, int advanceThreshold, int countdownTicks)
        implements CustomPacketPayload {
    public static final Type<CyberGrindStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_state"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindStatePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeBoolean(value.active());
                buf.writeUUID(value.runId());
                buf.writeUtf(value.profileName(), 256);
                buf.writeVarInt(value.wave());
                buf.writeVarInt(value.completedWaves());
                buf.writeVarInt(value.livingWeight());
                buf.writeVarInt(value.advanceThreshold());
                buf.writeVarInt(value.countdownTicks());
            },
            buf -> new CyberGrindStatePayload(buf.readBoolean(), buf.readUUID(), buf.readUtf(256),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    public static CyberGrindStatePayload empty() {
        return new CyberGrindStatePayload(false, new UUID(0L, 0L), "", 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
