package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJActionSource;

public record DJMaceThrowPayload(DJJudgmentProof proof, InteractionHand hand, DJActionSource source)
        implements CustomPacketPayload {
    public static final Type<DJMaceThrowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_mace_throw"));
    public static final StreamCodec<FriendlyByteBuf, DJMaceThrowPayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJMaceThrowPayload::proof,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJMaceThrowPayload::hand,
            DJActionSource.CODEC, DJMaceThrowPayload::source,
            DJMaceThrowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
