package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJActionSource;

public record DJTridentFirePayload(DJJudgmentProof proof, InteractionHand hand, DJActionSource source)
        implements CustomPacketPayload {
    public static final Type<DJTridentFirePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_trident_fire"));
    public static final StreamCodec<FriendlyByteBuf, DJTridentFirePayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJTridentFirePayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJTridentFirePayload::hand,
            DJActionSource.CODEC, DJTridentFirePayload::source,
            DJTridentFirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
