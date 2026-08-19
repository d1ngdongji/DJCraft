package otto.djgun.djcraft.network.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.loader.TrackPackIdValidator;
import otto.djgun.djcraft.network.packet.ClientTrackStatusPayload;

public final class ClientTrackStatusService {
    private static final Map<UUID, Map<String, String>> HASHES = new ConcurrentHashMap<>();

    private ClientTrackStatusService() {
    }

    public static void handle(ClientTrackStatusPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Map<String, String> valid = payload.contentHashes().entrySet().stream()
                .filter(entry -> TrackPackIdValidator.isValid(entry.getKey()))
                .filter(entry -> entry.getValue().matches("[0-9a-fA-F]{64}"))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> entry.getValue().toLowerCase(java.util.Locale.ROOT)));
        HASHES.put(player.getUUID(), valid);
    }

    public static boolean isVerified(ServerPlayer player, String trackId, String expectedHash) {
        return expectedHash != null && expectedHash.equals(
                HASHES.getOrDefault(player.getUUID(), Map.of()).get(trackId));
    }

    public static void recordVerified(ServerPlayer player, String trackId, String hash) {
        HASHES.compute(player.getUUID(), (id, previous) -> {
            Map<String, String> updated = new java.util.HashMap<>(
                    previous == null ? Map.of() : previous);
            updated.put(trackId, hash.toLowerCase(java.util.Locale.ROOT));
            return Map.copyOf(updated);
        });
    }

    public static void cleanup(UUID playerId) {
        HASHES.remove(playerId);
    }
}
