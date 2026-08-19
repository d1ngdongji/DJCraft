package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupInviteResponsePayload(UUID groupId, boolean accepted) implements CustomPacketPayload {
    public static final Type<DJGroupInviteResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_invite_response"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupInviteResponsePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.groupId());
                buf.writeBoolean(value.accepted());
            },
            buf -> new DJGroupInviteResponsePayload(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
