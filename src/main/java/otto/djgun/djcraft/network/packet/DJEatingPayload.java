package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;

public record DJEatingPayload(DJJudgmentProof proof, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<DJEatingPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_eating"));
    public static final StreamCodec<FriendlyByteBuf, DJEatingPayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJEatingPayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJEatingPayload::hand,
            DJEatingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
