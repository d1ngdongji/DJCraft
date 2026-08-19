package otto.djgun.djcraft.client.playback;

import java.util.List;
import java.util.concurrent.TimeUnit;

import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.playback.DJPlaybackMode;
import otto.djgun.djcraft.network.packet.ClientRequestPlayPayload;
import otto.djgun.djcraft.network.packet.ClientStopSessionPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import java.util.UUID;

public final class DJPlaybackController {
    private static final long PENDING_TIMEOUT_MS = 5_000L;
    private static final DJPlaybackController INSTANCE = new DJPlaybackController();

    private final DJPlaylistState state = new DJPlaylistState();

    private DJPlaybackController() {
    }

    public static DJPlaybackController getInstance() {
        return INSTANCE;
    }

    public DJPlaybackMode getMode() {
        return state.mode();
    }

    public List<DiscPlaybackReference> getPlaylist() {
        return state.entries();
    }

    public int getCurrentIndex() {
        return state.currentIndex();
    }

    public void setMode(DJPlaybackMode mode) {
        state.setMode(mode);
        if (otto.djgun.djcraft.client.DJNetworkGroupClient.getInstance().isOwner()) {
            PacketDistributor.sendToServer(new otto.djgun.djcraft.network.packet.DJGroupControlPayload(
                    otto.djgun.djcraft.network.packet.DJGroupControlPayload.Action.SET_MODE,
                    mode.ordinal(), 0L));
        }
    }

    public void requestPlay(List<DiscPlaybackReference> playlist, int index) {
        send(state.configureAndPlan(playlist, index, nowMs()));
    }

    public void onTrackStarted(long sessionId, String trackId, UUID discId) {
        if (!state.confirmStarted(sessionId, trackId, discId)) {
            DJCraft.LOGGER.debug("DJ playback {} started outside the active client playlist", trackId);
        }
    }

    public void onNaturalEnd(long sessionId) {
        state.naturalEnd(sessionId, nowMs()).ifPresent(this::send);
    }

    public void stopCurrent(long sessionId) {
        state.stop(sessionId);
        PacketDistributor.sendToServer(new ClientStopSessionPayload(sessionId, StopReason.REQUESTED));
    }

    public void onStopped(long sessionId) {
        state.stop(sessionId);
    }

    public void resetPlayback() {
        state.clearPlayback();
    }

    public void tick() {
        if (state.expirePending(nowMs(), PENDING_TIMEOUT_MS)) {
            DJCraft.LOGGER.warn("DJ playlist play request timed out");
        }
    }

    private void send(DJPlaylistState.Selection selection) {
        PacketDistributor.sendToServer(new ClientRequestPlayPayload(selection.disc()));
    }

    private static long nowMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
