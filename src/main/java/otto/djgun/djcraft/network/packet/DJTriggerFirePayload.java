package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJActionSource;

public record DJTriggerFirePayload(DJJudgmentProof proof, InteractionHand hand, DJActionSource source)
        implements CustomPacketPayload {
    public static final Type<DJTriggerFirePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_trigger_fire"));
    public static final StreamCodec<FriendlyByteBuf, DJTriggerFirePayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJTriggerFirePayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJTriggerFirePayload::hand,
            DJActionSource.CODEC, DJTriggerFirePayload::source,
            DJTriggerFirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
