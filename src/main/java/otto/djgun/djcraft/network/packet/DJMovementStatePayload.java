package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJMovementStatePayload(long sessionId, int dashCooldownTicks, int consecutiveDashes,
        int remainingAirJumps)
        implements CustomPacketPayload {
    public static final Type<DJMovementStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_movement_state"));
    public static final StreamCodec<FriendlyByteBuf, DJMovementStatePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.sessionId());
                buf.writeVarInt(value.dashCooldownTicks());
                buf.writeVarInt(value.consecutiveDashes());
                buf.writeVarInt(value.remainingAirJumps());
            },
            buf -> new DJMovementStatePayload(
                    buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
