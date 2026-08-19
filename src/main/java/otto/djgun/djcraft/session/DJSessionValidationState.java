package otto.djgun.djcraft.session;

public final class DJSessionValidationState {
    private Long clientClockOffsetMs;
    private long lastActionSequence;
    private int consecutiveClockAnomalies;

    public void synchronizeClock(long serverTimeMs, long clientTimeMs) {
        clientClockOffsetMs = serverTimeMs - clientTimeMs;
        consecutiveClockAnomalies = 0;
    }

    public boolean acceptActionSequence(long sequence) {
        if (sequence <= lastActionSequence) {
            return false;
        }
        lastActionSequence = sequence;
        return true;
    }

    public ClockAudit auditClock(long serverTimeMs, long clientTimeMs, int pingMs) {
        if (clientClockOffsetMs == null) {
            return ClockAudit.notAligned();
        }
        long expectedClientTime = serverTimeMs - clientClockOffsetMs;
        long differenceMs = Math.abs(expectedClientTime - clientTimeMs);
        long thresholdMs = Math.max(1000L, Math.min(3000L, 1000L + 2L * Math.max(0, pingMs)));
        boolean anomalous = differenceMs > thresholdMs;
        consecutiveClockAnomalies = anomalous ? consecutiveClockAnomalies + 1 : 0;
        return new ClockAudit(true, anomalous, differenceMs, thresholdMs, consecutiveClockAnomalies,
                consecutiveClockAnomalies >= 5);
    }

    public record ClockAudit(boolean aligned, boolean anomalous, long differenceMs, long thresholdMs,
            int consecutiveAnomalies, boolean stopSession) {
        private static ClockAudit notAligned() {
            return new ClockAudit(false, false, 0L, 0L, 0, false);
        }
    }
}
