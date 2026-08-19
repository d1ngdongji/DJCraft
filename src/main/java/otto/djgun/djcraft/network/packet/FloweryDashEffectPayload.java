package otto.djgun.djcraft.network.packet;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record FloweryDashEffectPayload(UUID playerId, int durationTicks) implements CustomPacketPayload {
    public static final Type<FloweryDashEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "flowery_dash_effect"));
    public static final StreamCodec<FriendlyByteBuf, FloweryDashEffectPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FloweryDashEffectPayload::playerId,
            ByteBufCodecs.VAR_INT, FloweryDashEffectPayload::durationTicks,
            FloweryDashEffectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
