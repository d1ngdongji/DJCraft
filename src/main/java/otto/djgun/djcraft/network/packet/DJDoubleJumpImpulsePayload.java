package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.DJCraft;

public record DJDoubleJumpImpulsePayload(long sessionId, Vec3 velocity, int dashMomentumTicks)
        implements CustomPacketPayload {
    public static final Type<DJDoubleJumpImpulsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_double_jump_impulse"));
    public static final StreamCodec<FriendlyByteBuf, DJDoubleJumpImpulsePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.sessionId());
                buf.writeVec3(value.velocity());
                buf.writeVarInt(value.dashMomentumTicks());
            },
            buf -> new DJDoubleJumpImpulsePayload(buf.readVarLong(), buf.readVec3(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
