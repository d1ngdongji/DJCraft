package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupInvitationPayload(UUID groupId, UUID ownerId, String ownerName)
        implements CustomPacketPayload {
    public static final Type<DJGroupInvitationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_invitation"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupInvitationPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.groupId());
                buf.writeUUID(value.ownerId());
                buf.writeUtf(value.ownerName(), 64);
            },
            buf -> new DJGroupInvitationPayload(buf.readUUID(), buf.readUUID(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
