package otto.djgun.djcraft.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJMiningPayload(DJJudgmentProof proof, BlockPos pos) implements CustomPacketPayload {
    public static final Type<DJMiningPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_mining"));
    public static final StreamCodec<FriendlyByteBuf, DJMiningPayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJMiningPayload::proof,
            BlockPos.STREAM_CODEC, DJMiningPayload::pos,
            DJMiningPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
