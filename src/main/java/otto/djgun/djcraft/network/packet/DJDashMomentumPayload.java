package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.DJCraft;

public record DJDashMomentumPayload(Vec3 momentum, int durationTicks) implements CustomPacketPayload {
    public static final Type<DJDashMomentumPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dash_momentum"));
    public static final StreamCodec<FriendlyByteBuf, DJDashMomentumPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeDouble(value.momentum().x);
                buf.writeDouble(value.momentum().y);
                buf.writeDouble(value.momentum().z);
                buf.writeVarInt(value.durationTicks());
            },
            buf -> new DJDashMomentumPayload(
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
