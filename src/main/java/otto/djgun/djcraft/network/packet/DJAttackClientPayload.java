package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import otto.djgun.djcraft.DJCraft;

/**
 * DJ 模式开火数据包 (客户端 -> 服务器)
 */
public record DJAttackClientPayload(boolean isHit, float damageModifier, long exactTimeMs, int beatIndex,
                InteractionHand hand) implements CustomPacketPayload {

        public static final Type<DJAttackClientPayload> TYPE = new Type<>(
                        ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dj_attack"));

        public static final StreamCodec<FriendlyByteBuf, DJAttackClientPayload> CODEC = StreamCodec.composite(
                        ByteBufCodecs.BOOL, DJAttackClientPayload::isHit,
                        ByteBufCodecs.FLOAT, DJAttackClientPayload::damageModifier,
                        ByteBufCodecs.VAR_LONG, DJAttackClientPayload::exactTimeMs,
                        ByteBufCodecs.VAR_INT, DJAttackClientPayload::beatIndex,
                        ByteBufCodecs.idMapper(i -> InteractionHand.values()[i], InteractionHand::ordinal),
                        DJAttackClientPayload::hand,
                        DJAttackClientPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
                return TYPE;
        }
}
