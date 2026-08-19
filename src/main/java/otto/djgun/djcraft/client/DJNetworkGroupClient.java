package otto.djgun.djcraft.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.DJGroupInvitationPayload;
import otto.djgun.djcraft.network.packet.DJGroupInviteResponsePayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload;
import otto.djgun.djcraft.network.packet.DJGroupReadyPayload;
import otto.djgun.djcraft.network.packet.DJGroupStatePayload;
import otto.djgun.djcraft.network.packet.DJGroupAudioRecoveryPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.StopReason;

/**
 * Client group/readiness state. Playback timing remains owned by OpenAL.
 */
public final class DJNetworkGroupClient {
    private static final DJNetworkGroupClient INSTANCE = new DJNetworkGroupClient();
    private final Map<UUID, DJGroupInvitationPayload> invitations = new LinkedHashMap<>();
    private DJGroupStatePayload state = DJGroupStatePayload.empty();
    private long localSessionId;
    private long groupPlaybackId;
    private boolean localPlaybackOwner;
    private LocalAudioState localAudioState = LocalAudioState.NONE;

    private DJNetworkGroupClient() {
    }

    public static DJNetworkGroupClient getInstance() {
        return INSTANCE;
    }

    public DJGroupStatePayload state() {
        return state;
    }

    public boolean isInGroup() {
        return state.present();
    }

    public boolean isOwner() {
        var player = Minecraft.getInstance().player;
        return state.present() && player != null && state.ownerId().equals(player.getUUID());
    }

    public List<DJGroupInvitationPayload> invitations() {
        return List.copyOf(invitations.values());
    }

    public void handleInvitation(DJGroupInvitationPayload payload) {
        invitations.put(payload.groupId(), payload);
        show(Component.translatable("message.djcraft.group.invited", payload.ownerName()));
    }

    public void respondToFirstInvitation(boolean accept) {
        Optional<DJGroupInvitationPayload> first = invitations.values().stream().findFirst();
        first.ifPresent(invitation -> {
            PacketDistributor.sendToServer(new DJGroupInviteResponsePayload(invitation.groupId(), accept));
            invitations.remove(invitation.groupId());
        });
    }

    public void handleState(DJGroupStatePayload payload) {
        state = payload;
        if (!payload.present()) {
            localSessionId = 0L;
            groupPlaybackId = 0L;
            localPlaybackOwner = false;
            localAudioState = LocalAudioState.NONE;
        } else if (payload.playbackId() == 0L) {
            localSessionId = 0L;
            groupPlaybackId = 0L;
            localPlaybackOwner = false;
            localAudioState = LocalAudioState.NONE;
        } else if (payload.playbackId() != 0L && payload.playbackId() == groupPlaybackId) {
            var player = Minecraft.getInstance().player;
            localPlaybackOwner = player != null && payload.ownerId().equals(player.getUUID());
        }
    }

    public void handlePrepare(DJGroupPreparePayload payload) {
        TrackPackManager manager = TrackPackManager.getInstance();
        List<String> downloads = new ArrayList<>();
        for (var requirement : payload.tracks()) {
            if (requirement.contentHash().equals(manager.getContentHash(requirement.trackId()).orElse(null))) {
                continue;
            }
            if (!requirement.downloadable()) {
                PacketDistributor.sendToServer(new DJGroupReadyPayload(payload.groupId(), false,
                        requirement.trackId() + ": unavailable"));
                return;
            }
            downloads.add(requirement.trackId());
        }
        if (downloads.isEmpty()) {
            PacketDistributor.sendToServer(new DJGroupReadyPayload(payload.groupId(), true, ""));
            return;
        }
        ClientTrackPackTransferService.requestBatch(downloads,
                () -> verifyAndReport(payload),
                reason -> PacketDistributor.sendToServer(new DJGroupReadyPayload(payload.groupId(), false,
                        reason.name().toLowerCase(java.util.Locale.ROOT))));
    }

    private void verifyAndReport(DJGroupPreparePayload payload) {
        TrackPackManager manager = TrackPackManager.getInstance();
        for (var requirement : payload.tracks()) {
            if (!requirement.contentHash().equals(manager.getContentHash(requirement.trackId()).orElse(null))) {
                PacketDistributor.sendToServer(new DJGroupReadyPayload(payload.groupId(), false,
                        requirement.trackId() + ": hash mismatch"));
                return;
            }
        }
        PacketDistributor.sendToServer(new DJGroupReadyPayload(payload.groupId(), true, ""));
    }

    public void onGroupTrackStarted(PlayTrackPayload payload) {
        localSessionId = payload.sessionId();
        groupPlaybackId = payload.groupPlaybackId();
        localPlaybackOwner = payload.groupOwner();
        localAudioState = LocalAudioState.PLAYING;
    }

    public boolean onConfiguredPlaybackEnd(long sessionId) {
        if (sessionId == 0L || sessionId != localSessionId || groupPlaybackId == 0L) {
            return false;
        }
        localSessionId = 0L;
        localAudioState = LocalAudioState.AWAITING_SERVER;
        return true;
    }

    public long onAudioUnavailable(long sessionId) {
        if (sessionId == 0L || sessionId != localSessionId || groupPlaybackId == 0L) {
            return 0L;
        }
        localSessionId = 0L;
        localAudioState = LocalAudioState.RECOVERING;
        return groupPlaybackId;
    }

    public boolean onStopped(long sessionId, StopReason reason) {
        if (sessionId == 0L || sessionId != localSessionId) {
            return false;
        }
        localSessionId = 0L;
        if (groupPlaybackId != 0L && (reason == StopReason.AUDIO_ENDED
                || reason == StopReason.CLOCK_DESYNC || reason == StopReason.AUDIO_UNAVAILABLE)) {
            localAudioState = reason == StopReason.AUDIO_ENDED
                    ? LocalAudioState.AWAITING_SERVER : LocalAudioState.RECOVERING;
            return true;
        }
        groupPlaybackId = 0L;
        localPlaybackOwner = false;
        localAudioState = LocalAudioState.NONE;
        return true;
    }

    public void handleAudioRecovery(DJGroupAudioRecoveryPayload payload) {
        if (!state.present() || !state.groupId().equals(payload.groupId())
                || groupPlaybackId != payload.playbackId()) {
            return;
        }
        localAudioState = payload.status() == DJGroupAudioRecoveryPayload.Status.RETRYING
                ? LocalAudioState.RECOVERING : LocalAudioState.QUARANTINED;
        Component message = payload.status() == DJGroupAudioRecoveryPayload.Status.RETRYING
                ? Component.translatable("message.djcraft.group.audio_retry",
                        payload.attempt(), payload.maxAttempts())
                : Component.translatable("message.djcraft.group.audio_quarantined");
        show(message);
    }

    public boolean shouldSuppressVanillaCombat() {
        return state.present() && groupPlaybackId != 0L
                && localAudioState != LocalAudioState.PLAYING;
    }

    public void reset() {
        invitations.clear();
        state = DJGroupStatePayload.empty();
        localSessionId = 0L;
        groupPlaybackId = 0L;
        localPlaybackOwner = false;
        localAudioState = LocalAudioState.NONE;
    }

    private enum LocalAudioState {
        NONE,
        PLAYING,
        RECOVERING,
        QUARANTINED,
        AWAITING_SERVER
    }

    private static void show(Component message) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(message, false);
            }
        });
    }
}
