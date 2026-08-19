package otto.djgun.djcraft.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupPreparePayload(UUID groupId, List<TrackRequirement> tracks)
        implements CustomPacketPayload {
    private static final int MAX_TRACKS = 54;

    public record TrackRequirement(String trackId, String contentHash, boolean downloadable) {
    }

    public static final Type<DJGroupPreparePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_prepare"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupPreparePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.groupId());
                if (value.tracks().size() > MAX_TRACKS) {
                    throw new IllegalArgumentException("Too many DJ group tracks");
                }
                buf.writeVarInt(value.tracks().size());
                for (TrackRequirement track : value.tracks()) {
                    buf.writeUtf(track.trackId(), 128);
                    buf.writeUtf(track.contentHash(), 64);
                    buf.writeBoolean(track.downloadable());
                }
            },
            buf -> {
                UUID groupId = buf.readUUID();
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_TRACKS) {
                    throw new IllegalArgumentException("Invalid DJ group track count");
                }
                List<TrackRequirement> tracks = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    tracks.add(new TrackRequirement(buf.readUtf(128), buf.readUtf(64), buf.readBoolean()));
                }
                return new DJGroupPreparePayload(groupId, List.copyOf(tracks));
            });

    public DJGroupPreparePayload {
        tracks = List.copyOf(tracks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
