package otto.djgun.djcraft.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record CyberGrindPreparePayload(UUID runId, String profileName, String ownerName,
        List<String> members, List<TrackRequirement> tracks) implements CustomPacketPayload {
    private static final int MAX_MEMBERS = 256;
    private static final int MAX_TRACKS = 54;

    public record TrackRequirement(String trackId, String contentHash, boolean downloadable) {
    }

    public static final Type<CyberGrindPreparePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "cyber_grind_prepare"));
    public static final StreamCodec<FriendlyByteBuf, CyberGrindPreparePayload> CODEC = StreamCodec.of(
            CyberGrindPreparePayload::encode, CyberGrindPreparePayload::decode);

    private static void encode(FriendlyByteBuf buf, CyberGrindPreparePayload value) {
        buf.writeUUID(value.runId());
        buf.writeUtf(value.profileName(), 256);
        buf.writeUtf(value.ownerName(), 64);
        if (value.members().size() > MAX_MEMBERS || value.tracks().size() > MAX_TRACKS) {
            throw new IllegalArgumentException("Cyber Grind preparation list too large");
        }
        buf.writeVarInt(value.members().size());
        value.members().forEach(member -> buf.writeUtf(member, 64));
        buf.writeVarInt(value.tracks().size());
        for (TrackRequirement track : value.tracks()) {
            buf.writeUtf(track.trackId(), 128);
            buf.writeUtf(track.contentHash(), 64);
            buf.writeBoolean(track.downloadable());
        }
    }

    private static CyberGrindPreparePayload decode(FriendlyByteBuf buf) {
        UUID runId = buf.readUUID();
        String profileName = buf.readUtf(256);
        String ownerName = buf.readUtf(64);
        int memberCount = buf.readVarInt();
        if (memberCount < 0 || memberCount > MAX_MEMBERS) {
            throw new IllegalArgumentException("Invalid Cyber Grind member count");
        }
        List<String> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            members.add(buf.readUtf(64));
        }
        int trackCount = buf.readVarInt();
        if (trackCount < 0 || trackCount > MAX_TRACKS) {
            throw new IllegalArgumentException("Invalid Cyber Grind track count");
        }
        List<TrackRequirement> tracks = new ArrayList<>(trackCount);
        for (int index = 0; index < trackCount; index++) {
            tracks.add(new TrackRequirement(buf.readUtf(128), buf.readUtf(64), buf.readBoolean()));
        }
        return new CyberGrindPreparePayload(runId, profileName, ownerName, members, tracks);
    }

    public CyberGrindPreparePayload {
        members = List.copyOf(members);
        tracks = List.copyOf(tracks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
