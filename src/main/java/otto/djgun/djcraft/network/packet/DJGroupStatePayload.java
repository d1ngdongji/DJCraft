package otto.djgun.djcraft.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record DJGroupStatePayload(boolean present, UUID groupId, UUID ownerId, String ownerName,
        List<String> members, List<String> pending, int mode, int currentIndex, long playbackId,
        String currentTrack) implements CustomPacketPayload {
    private static final int MAX_PLAYERS = 256;
    public static final Type<DJGroupStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "group_state"));
    public static final StreamCodec<FriendlyByteBuf, DJGroupStatePayload> CODEC = StreamCodec.of(
            DJGroupStatePayload::encode, DJGroupStatePayload::decode);

    public static DJGroupStatePayload empty() {
        return new DJGroupStatePayload(false, new UUID(0L, 0L), new UUID(0L, 0L), "",
                List.of(), List.of(), 0, -1, 0L, "");
    }

    private static void encode(FriendlyByteBuf buf, DJGroupStatePayload value) {
        buf.writeBoolean(value.present());
        buf.writeUUID(value.groupId());
        buf.writeUUID(value.ownerId());
        buf.writeUtf(value.ownerName(), 64);
        writeStrings(buf, value.members());
        writeStrings(buf, value.pending());
        buf.writeVarInt(value.mode());
        buf.writeVarInt(value.currentIndex());
        buf.writeVarLong(value.playbackId());
        buf.writeUtf(value.currentTrack(), 128);
    }

    private static DJGroupStatePayload decode(FriendlyByteBuf buf) {
        return new DJGroupStatePayload(buf.readBoolean(), buf.readUUID(), buf.readUUID(), buf.readUtf(64),
                readStrings(buf), readStrings(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(),
                buf.readUtf(128));
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        if (values.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Too many DJ group players");
        }
        buf.writeVarInt(values.size());
        values.forEach(value -> buf.writeUtf(value, 64));
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid DJ group player count");
        }
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(64));
        }
        return List.copyOf(values);
    }

    public DJGroupStatePayload {
        members = List.copyOf(members);
        pending = List.copyOf(pending);
        ownerName = ownerName == null ? "" : ownerName;
        currentTrack = currentTrack == null ? "" : currentTrack;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
