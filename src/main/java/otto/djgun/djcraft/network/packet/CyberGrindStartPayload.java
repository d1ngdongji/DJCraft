package otto.djgun.djcraft.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindStartPayload(String profileId, int jukeboxSlot, int mode,
        int startDiscSlot, BlockPos tablePos) implements CustomPacketPayload {
    public static final Type<CyberGrindStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_start"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindStartPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.profileId(), 256);
                buf.writeVarInt(value.jukeboxSlot());
                buf.writeVarInt(value.mode());
                buf.writeVarInt(value.startDiscSlot());
                buf.writeBlockPos(value.tablePos());
            },
            buf -> new CyberGrindStartPayload(buf.readUtf(256), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
