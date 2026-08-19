package otto.djgun.djcraft.network.packet;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

public record ClientTrackStatusPayload(Map<String, String> contentHashes) implements CustomPacketPayload {
    private static final int MAX_PACKS = 4096;
    public static final Type<ClientTrackStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "client_track_status"));
    public static final StreamCodec<FriendlyByteBuf, ClientTrackStatusPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                if (payload.contentHashes().size() > MAX_PACKS) {
                    throw new IllegalArgumentException("Too many client TrackPack hashes");
                }
                buf.writeVarInt(payload.contentHashes().size());
                payload.contentHashes().forEach((id, hash) -> {
                    buf.writeUtf(id, 128);
                    buf.writeUtf(hash, 64);
                });
            },
            buf -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_PACKS) {
                    throw new IllegalArgumentException("Invalid client TrackPack hash count");
                }
                Map<String, String> hashes = new HashMap<>(size);
                for (int index = 0; index < size; index++) {
                    hashes.put(buf.readUtf(128), buf.readUtf(64));
                }
                return new ClientTrackStatusPayload(Map.copyOf(hashes));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
