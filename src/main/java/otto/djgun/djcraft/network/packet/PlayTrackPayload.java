package otto.djgun.djcraft.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

import java.util.UUID;

/**
 * 播放曲目数据包 (服务器 -> 客户端)
 */
public record PlayTrackPayload(long sessionId, String trackId, UUID discId, UUID groupId,
        long groupPlaybackId, long initialPositionMs, long estimatedTransitMs,
        boolean groupOwner) implements CustomPacketPayload {

    public PlayTrackPayload(long sessionId, String trackId) {
        this(sessionId, trackId, null, null, 0L, 0L, 0L, false);
    }

    public PlayTrackPayload(long sessionId, String trackId, UUID discId) {
        this(sessionId, trackId, discId, null, 0L, 0L, 0L, false);
    }

    public static final Type<PlayTrackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "play_track"));

    public static final StreamCodec<FriendlyByteBuf, PlayTrackPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarLong(value.sessionId());
                buf.writeUtf(value.trackId(), 128);
                buf.writeBoolean(value.discId() != null);
                if (value.discId() != null) {
                    buf.writeUUID(value.discId());
                }
                buf.writeVarLong(value.estimatedTransitMs());
                buf.writeBoolean(value.groupId() != null);
                if (value.groupId() != null) {
                    buf.writeUUID(value.groupId());
                    buf.writeVarLong(value.groupPlaybackId());
                    buf.writeVarLong(value.initialPositionMs());
                    buf.writeBoolean(value.groupOwner());
                }
            },
            buf -> {
                long sessionId = buf.readVarLong();
                String trackId = buf.readUtf(128);
                UUID discId = buf.readBoolean() ? buf.readUUID() : null;
                long estimatedTransitMs = buf.readVarLong();
                if (!buf.readBoolean()) {
                    return new PlayTrackPayload(sessionId, trackId, discId, null,
                            0L, 0L, estimatedTransitMs, false);
                }
                return new PlayTrackPayload(sessionId, trackId, discId, buf.readUUID(),
                        buf.readVarLong(), buf.readVarLong(), estimatedTransitMs, buf.readBoolean());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
