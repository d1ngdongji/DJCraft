package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJActionSource;

public record DJAutoChargeStartPayload(DJJudgmentProof proof, InteractionHand hand, DJActionSource source)
        implements CustomPacketPayload {
    public static final Type<DJAutoChargeStartPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_auto_charge_start"));
    public static final StreamCodec<FriendlyByteBuf, DJAutoChargeStartPayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJAutoChargeStartPayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJAutoChargeStartPayload::hand,
            DJActionSource.CODEC, DJAutoChargeStartPayload::source,
            DJAutoChargeStartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
