package otto.djgun.djcraft.network.packet;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJDashAfterimagePayload(UUID playerId) implements CustomPacketPayload {
    public static final Type<DJDashAfterimagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "dash_afterimage"));
    public static final StreamCodec<FriendlyByteBuf, DJDashAfterimagePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DJDashAfterimagePayload::playerId,
            DJDashAfterimagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
