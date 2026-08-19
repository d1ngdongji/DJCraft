package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;

public record DJShieldParryWindowPayload(long sessionId, InteractionHand hand, long expiresAtMs)
        implements CustomPacketPayload {
    public static final Type<DJShieldParryWindowPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "shield_parry_window"));
    public static final StreamCodec<FriendlyByteBuf, DJShieldParryWindowPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DJShieldParryWindowPayload::sessionId,
            ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], InteractionHand::ordinal),
            DJShieldParryWindowPayload::hand,
            ByteBufCodecs.VAR_LONG, DJShieldParryWindowPayload::expiresAtMs,
            DJShieldParryWindowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
