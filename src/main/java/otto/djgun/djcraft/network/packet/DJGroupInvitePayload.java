package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupInvitePayload(UUID targetPlayerId) implements CustomPacketPayload {
    public static final Type<DJGroupInvitePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_invite"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupInvitePayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUUID(value.targetPlayerId()),
            buf -> new DJGroupInvitePayload(buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
