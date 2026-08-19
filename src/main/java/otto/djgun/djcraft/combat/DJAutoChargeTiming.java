package otto.djgun.djcraft.combat;

import java.util.List;

import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.util.BeatGridUtil;

/** Continuous virtual-beat timing rules shared by delayed firing on both sides. */
public final class DJAutoChargeTiming {
    private static final double EPSILON = 1.0E-9;

    private DJAutoChargeTiming() {
    }

    public static Schedule schedule(long pressedAtMs, List<BeatEvent> beats, int chargeBeats) {
        if (pressedAtMs < 0L || chargeBeats <= 0) {
            return null;
        }
        double startVirtualBeat = BeatGridUtil.getVirtualBeat(pressedAtMs, beats);
        double targetVirtualBeat = startVirtualBeat + chargeBeats;
        long targetTimeMs = BeatGridUtil.getTimeAtVirtualBeat(targetVirtualBeat, beats);
        if (!Double.isFinite(startVirtualBeat) || !Double.isFinite(targetVirtualBeat)
                || targetTimeMs < pressedAtMs) {
            return null;
        }
        return new Schedule(startVirtualBeat, targetVirtualBeat, pressedAtMs, targetTimeMs);
    }

    public static boolean isSchedulable(Schedule schedule, double currentVirtualBeat,
            int totalDurationMs, int offsetMs) {
        if (schedule == null || !Double.isFinite(currentVirtualBeat)
                || schedule.targetVirtualBeat() <= currentVirtualBeat + EPSILON) {
            return false;
        }
        if (totalDurationMs <= 0) {
            return true;
        }
        long rawTargetTimeMs = schedule.targetTimeMs() + (long) offsetMs;
        return rawTargetTimeMs >= 0L && rawTargetTimeMs < totalDurationMs;
    }

    public static boolean isDue(double currentVirtualBeat, double targetVirtualBeat) {
        return Double.isFinite(currentVirtualBeat) && Double.isFinite(targetVirtualBeat)
                && currentVirtualBeat + EPSILON >= targetVirtualBeat;
    }

    public static float progress(double currentVirtualBeat, double startVirtualBeat,
            double targetVirtualBeat) {
        double duration = targetVirtualBeat - startVirtualBeat;
        if (!Double.isFinite(currentVirtualBeat) || !Double.isFinite(duration) || duration <= 0.0) {
            return 0.0F;
        }
        return Math.clamp((float) ((currentVirtualBeat - startVirtualBeat) / duration), 0.0F, 1.0F);
    }

    public record Schedule(double startVirtualBeat, double targetVirtualBeat,
            long startTimeMs, long targetTimeMs) {
    }
}
