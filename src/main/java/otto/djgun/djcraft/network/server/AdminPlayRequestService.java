package otto.djgun.djcraft.network.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.AdminPlayPreparePayload;
import otto.djgun.djcraft.network.packet.AdminPlayReadyPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJNetworkGroupManager;

public final class AdminPlayRequestService {
    private static final long TIMEOUT_MS = 120_000L;
    private static final Map<Long, Pending> PENDING = new ConcurrentHashMap<>();

    private AdminPlayRequestService() {
    }

    public static boolean request(CommandSourceStack source, ServerPlayer player, String trackId) {
        String expectedHash = TrackPackManager.getInstance().getContentHash(trackId).orElse(null);
        if (expectedHash == null) {
            return false;
        }
        if (ClientTrackStatusService.isVerified(player, trackId, expectedHash)) {
            start(player, trackId);
            return true;
        }

        PENDING.values().removeIf(pending -> pending.playerId.equals(player.getUUID()));
        long requestId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        boolean downloadable = TrackPackManager.getInstance().getArchiveDescriptor(trackId).isPresent();
        PENDING.put(requestId, new Pending(requestId, player.getUUID(), trackId,
                expectedHash, source, System.currentTimeMillis()));
        PacketDistributor.sendToPlayer(player,
                new AdminPlayPreparePayload(requestId, trackId, expectedHash, downloadable));
        source.sendSuccess(() -> Component.translatable(
                "message.djcraft.admin_play_checking", player.getDisplayName(), trackId), false);
        return true;
    }

    public static void handleReady(AdminPlayReadyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Pending pending = PENDING.remove(payload.requestId());
        if (pending == null || !pending.playerId.equals(player.getUUID())) {
            return;
        }
        if (!payload.ready() || !pending.expectedHash.equalsIgnoreCase(payload.contentHash())) {
            pending.source.sendFailure(Component.translatable(
                    "message.djcraft.admin_play_failed", player.getDisplayName(),
                    pending.trackId, payload.detail()));
            return;
        }
        ClientTrackStatusService.recordVerified(player, pending.trackId, pending.expectedHash);
        if (start(player, pending.trackId)) {
            pending.source.sendSuccess(() -> Component.translatable(
                    "message.djcraft.admin_play_started", pending.trackId, player.getDisplayName()), true);
        } else {
            pending.source.sendFailure(Component.translatable(
                    "message.djcraft.admin_play_failed", player.getDisplayName(),
                    pending.trackId, "server pack unavailable"));
        }
    }

    public static void cleanupExpired() {
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(pending -> {
            if (now - pending.createdAtMs <= TIMEOUT_MS) {
                return false;
            }
            pending.source.sendFailure(Component.translatable(
                    "message.djcraft.admin_play_failed",
                    pending.playerId.toString(), pending.trackId, "timeout"));
            return true;
        });
    }

    public static void cleanupPlayer(UUID playerId) {
        PENDING.values().removeIf(pending -> pending.playerId.equals(playerId));
    }

    private static boolean start(ServerPlayer player, String trackId) {
        var pack = TrackPackManager.getInstance().getTrackPack(trackId).orElse(null);
        if (pack == null) {
            return false;
        }
        if (DJNetworkGroupManager.getInstance().isParticipant(player.getUUID())) {
            DJNetworkGroupManager.getInstance().leave(player, false);
        }
        DJModeManager.getInstance().getSession(player)
                .ifPresent(session -> DJModeManager.getInstance().stopSession(player));
        var session = DJModeManager.getInstance().startSession(player, pack);
        PacketDistributor.sendToPlayer(player,
                new PlayTrackPayload(session.getSessionId(), trackId));
        session.sendResourceState();
        session.sendMovementState();
        return true;
    }

    private record Pending(long requestId, UUID playerId, String trackId, String expectedHash,
            CommandSourceStack source, long createdAtMs) {
    }
}
