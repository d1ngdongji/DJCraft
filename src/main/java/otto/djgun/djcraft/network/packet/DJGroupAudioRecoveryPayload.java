package otto.djgun.djcraft.network.packet;

import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupAudioRecoveryPayload(UUID groupId, long playbackId, Status status,
        int attempt, int maxAttempts) implements CustomPacketPayload {
    public enum Status {
        RETRYING,
        QUARANTINED
    }

    public static final Type<DJGroupAudioRecoveryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_audio_recovery"));

    public static final StreamCodec<FriendlyByteBuf, DJGroupAudioRecoveryPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.groupId());
                buf.writeVarLong(value.playbackId());
                buf.writeVarInt(value.status().ordinal());
                buf.writeVarInt(value.attempt());
                buf.writeVarInt(value.maxAttempts());
            },
            buf -> new DJGroupAudioRecoveryPayload(buf.readUUID(), buf.readVarLong(),
                    ByteBufCodecs.idMapper(id -> Status.values()[id], Status::ordinal).decode(buf),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
