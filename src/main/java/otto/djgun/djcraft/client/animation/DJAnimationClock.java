package otto.djgun.djcraft.client.animation;

import java.util.List;

import net.minecraft.client.Minecraft;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.session.DJSessionClient;
import otto.djgun.djcraft.util.BeatGridUtil;

/** Samples the session clock once and shares the immutable result for the whole rendered frame. */
public final class DJAnimationClock {
    private static final long REWIND_TOLERANCE_MS = 2L;

    private DJSessionClient lastSession;
    private long lastSessionTimeMs = -1L;
    private long generation;
    private long cachedFrameTimeNs = Long.MIN_VALUE;
    private ClockSnapshot cachedSnapshot;

    public ClockSnapshot sample(DJSessionClient session) {
        long frameTimeNs = Minecraft.getInstance().getFrameTimeNs();
        if (session == lastSession && frameTimeNs == cachedFrameTimeNs && cachedSnapshot != null) {
            return cachedSnapshot;
        }

        long sessionTimeMs = session.getCurrentTimeMs();
        boolean reset = session != lastSession
                || (lastSessionTimeMs >= 0L && sessionTimeMs < lastSessionTimeMs - REWIND_TOLERANCE_MS);
        if (reset) {
            generation++;
        }
        if (generation == 0L) {
            generation = 1L;
        }

        List<BeatEvent> combatLine = session.getTrackPack().timeline().combatLine();
        boolean fallbackBeatGrid = combatLine == null || combatLine.isEmpty();
        double virtualBeat = BeatGridUtil.getVirtualBeat(sessionTimeMs, combatLine);
        double beatFraction = virtualBeat - Math.floor(virtualBeat);
        cachedSnapshot = new ClockSnapshot(sessionTimeMs, virtualBeat, beatFraction,
                session.isPlaying(), session.isPaused(), generation, fallbackBeatGrid, combatLine);
        cachedFrameTimeNs = frameTimeNs;
        lastSessionTimeMs = sessionTimeMs;
        lastSession = session;
        return cachedSnapshot;
    }

    public void invalidate() {
        lastSession = null;
        lastSessionTimeMs = -1L;
        cachedFrameTimeNs = Long.MIN_VALUE;
        cachedSnapshot = null;
    }

    public record ClockSnapshot(
            long sessionTimeMs,
            double virtualBeat,
            double beatFraction,
            boolean playing,
            boolean paused,
            long timelineGeneration,
            boolean fallbackBeatGrid,
            List<BeatEvent> combatLine) {

        public ClockSnapshot {
            if (sessionTimeMs < 0 || !Double.isFinite(virtualBeat) || !Double.isFinite(beatFraction)
                    || timelineGeneration <= 0) {
                throw new IllegalArgumentException("Invalid animation clock snapshot");
            }
        }

        public long timeAtVirtualBeat(double beat) {
            return BeatGridUtil.getTimeAtVirtualBeat(beat, combatLine);
        }

        public double virtualBeatAt(long timeMs) {
            return BeatGridUtil.getVirtualBeat(timeMs, combatLine);
        }
    }
}
