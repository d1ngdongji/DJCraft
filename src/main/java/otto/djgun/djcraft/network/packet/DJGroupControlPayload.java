package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupControlPayload(Action action, int value, long playbackId) implements CustomPacketPayload {
    public enum Action {
        PLAY_INDEX,
        STOP,
        SET_MODE,
        RETRY,
        LEAVE,
        DISBAND
    }

    public static final Type<DJGroupControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_control"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(id -> Action.values()[id], Action::ordinal),
            DJGroupControlPayload::action,
            ByteBufCodecs.VAR_INT, DJGroupControlPayload::value,
            ByteBufCodecs.VAR_LONG, DJGroupControlPayload::playbackId,
            DJGroupControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
