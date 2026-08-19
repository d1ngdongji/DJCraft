package otto.djgun.djcraft.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.DJCraft;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端 -> 客户端：同步服务端已知的曲目包哈希表
 * 客户端用此表与本地哈希做交集，得到双端共同拥有的曲目列表
 */
public record SyncTrackHashesPayload(Map<String, String> packHashes) implements CustomPacketPayload {
    private static final int MAX_PACKS = 4096;

    public static final Type<SyncTrackHashesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, "sync_track_hashes"));

    // Map<String, String> 的 StreamCodec：先写条目数量，再依次写 key/value
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTrackHashesPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                Map<String, String> map = payload.packHashes();
                if (map.size() > MAX_PACKS) {
                    throw new IllegalArgumentException("Too many TrackPack hashes");
                }
                buf.writeVarInt(map.size());
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    buf.writeUtf(entry.getKey(), 128);
                    buf.writeUtf(entry.getValue(), 64);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_PACKS) {
                    throw new IllegalArgumentException("Invalid TrackPack hash count: " + size);
                }
                Map<String, String> map = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    String key = buf.readUtf(128);
                    String value = buf.readUtf(64);
                    map.put(key, value);
                }
                return new SyncTrackHashesPayload(map);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
