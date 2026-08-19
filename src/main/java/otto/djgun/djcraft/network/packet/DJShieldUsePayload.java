package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJActionSource;

public record DJShieldUsePayload(DJJudgmentProof proof, InteractionHand hand, DJActionSource source)
        implements CustomPacketPayload {
    public static final Type<DJShieldUsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_shield_use"));
    public static final StreamCodec<FriendlyByteBuf, DJShieldUsePayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJShieldUsePayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJShieldUsePayload::hand,
            DJActionSource.CODEC, DJShieldUsePayload::source,
            DJShieldUsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
