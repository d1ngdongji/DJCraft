package otto.djgun.djcraft.network.server;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.ClientPlaybackReadyPayload;
import otto.djgun.djcraft.network.packet.ClientRequestPlayPayload;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.session.DiscStatisticsService;
import otto.djgun.djcraft.session.DJNetworkGroupManager;

public final class DJSessionRequestHandler {
    private DJSessionRequestHandler() {
    }

    public static void handlePlay(ClientRequestPlayPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.djcraft.cyber_grind.playback_locked"));
            return;
        }
        if (DJNetworkGroupManager.getInstance().isParticipant(player.getUUID())) {
            DJNetworkGroupManager.getInstance().playDisc(player, payload.disc());
            return;
        }
        String packId = payload.disc().trackId();
        var pack = TrackPackManager.getInstance().getTrackPack(packId).orElse(null);
        String expectedHash = TrackPackManager.getInstance().getContentHash(packId).orElse(null);
        if (!ClientTrackStatusService.isVerified(player, packId, expectedHash)) {
            player.sendSystemMessage(Component.translatable(
                    "message.djcraft.play_unverified", packId));
            return;
        }
        var resolved = pack == null ? null : DiscStatisticsService.resolveForPlayback(player, payload.disc());
        if (pack == null || resolved == null) {
            player.sendSystemMessage(Component.translatable("message.djcraft.play_not_available", packId));
            return;
        }

        if (DJModeManager.getInstance().getSession(player).isPresent()) {
            DJModeManager.getInstance().stopSession(player, StopReason.REQUESTED);
            resolved = DiscStatisticsService.resolveForPlayback(player, resolved.reference());
            if (resolved == null) {
                player.sendSystemMessage(Component.translatable("message.djcraft.play_not_available", packId));
                return;
            }
        }

        DJSession session = DJModeManager.getInstance().startSession(
                player, pack, resolved.reference().discId(), resolved.statistics());
        PacketDistributor.sendToPlayer(player, new PlayTrackPayload(
                session.getSessionId(), packId, resolved.reference().discId()));
        session.sendResourceState();
        session.sendMovementState();
        DJCraft.LOGGER.info("Started DJ session {} for {} from portable jukebox",
                session.getSessionId(), player.getName().getString());
    }

    public static void handleStop(ClientStopSessionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
        if (session == null || session.getSessionId() != payload.sessionId()) {
            return;
        }
        if (otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(player.getUUID())) {
            if (payload.reason() == StopReason.AUDIO_UNAVAILABLE) {
                otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance().onAudioFailure(player);
            }
            return;
        }
        if (payload.reason() == StopReason.AUDIO_UNAVAILABLE) {
            if (DJNetworkGroupManager.getInstance().isParticipant(player.getUUID())) {
                DJNetworkGroupManager.getInstance().recoverMemberAudio(
                        player, payload.sessionId(), payload.groupPlaybackId());
                return;
            }
            DJCraft.LOGGER.warn("Stopping DJ session {} for {} because client audio became unavailable",
                    payload.sessionId(), player.getName().getString());
            DJModeManager.getInstance().stopSession(player, StopReason.AUDIO_UNAVAILABLE);
            return;
        }
        if (DJNetworkGroupManager.getInstance().isParticipant(player.getUUID())) {
            DJNetworkGroupManager.getInstance().stopPlayback(player);
            return;
        }
        StopReason reason = payload.reason() == StopReason.AUDIO_ENDED
                ? StopReason.AUDIO_ENDED
                : StopReason.REQUESTED;
        DJModeManager.getInstance().stopSession(player, reason);
    }

    public static void handlePlaybackReady(ClientPlaybackReadyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
        if (session != null && session.getSessionId() == payload.sessionId()) {
            long clientTimeMs = Math.max(0L, payload.clientTimeMs());
            if (player.getServer().isSingleplayer()) {
                session.alignToLocalClientClock(clientTimeMs);
            }
            session.synchronizeClientClock(clientTimeMs);
            DJCraft.LOGGER.debug("Aligned DJ client clock for {} at {}ms",
                    player.getName().getString(), clientTimeMs);
        }
    }

}
