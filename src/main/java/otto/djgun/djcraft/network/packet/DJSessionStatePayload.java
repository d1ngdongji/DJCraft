package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJSessionStatePayload(long sessionId, int combo, int currentTrackCombo, double energy, double maxEnergy,
        int toleranceChances, int maxToleranceChances, int offBeatAttackDamagePercent)
        implements CustomPacketPayload {
    public static final Type<DJSessionStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "session_state"));
    public static final StreamCodec<FriendlyByteBuf, DJSessionStatePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.sessionId());
                buf.writeVarInt(value.combo());
                buf.writeVarInt(value.currentTrackCombo());
                buf.writeDouble(value.energy());
                buf.writeDouble(value.maxEnergy());
                buf.writeVarInt(value.toleranceChances());
                buf.writeVarInt(value.maxToleranceChances());
                buf.writeVarInt(value.offBeatAttackDamagePercent());
            },
            buf -> new DJSessionStatePayload(buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
