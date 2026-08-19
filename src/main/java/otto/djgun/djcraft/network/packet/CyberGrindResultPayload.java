package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindResultPayload(String profileName, int completedWaves,
        int personalBest, int groupBest) implements CustomPacketPayload {
    public static final Type<CyberGrindResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_result"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindResultPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.profileName(), 256);
                buf.writeVarInt(value.completedWaves());
                buf.writeVarInt(value.personalBest());
                buf.writeVarInt(value.groupBest());
            },
            buf -> new CyberGrindResultPayload(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
