package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJMovementAbilityPayload(DJJudgmentProof proof, DJMovementAbility ability, DJDashDirection dashDirection)
        implements CustomPacketPayload {
    public static final Type<DJMovementAbilityPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_movement_ability"));
    public static final StreamCodec<FriendlyByteBuf, DJMovementAbilityPayload> CODEC = StreamCodec.composite(
            DJJudgmentProof.CODEC, DJMovementAbilityPayload::proof,
            ByteBufCodecs.idMapper(id -> DJMovementAbility.values()[id], DJMovementAbility::ordinal),
            DJMovementAbilityPayload::ability,
            ByteBufCodecs.idMapper(id -> DJDashDirection.values()[id], DJDashDirection::ordinal),
            DJMovementAbilityPayload::dashDirection,
            DJMovementAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
