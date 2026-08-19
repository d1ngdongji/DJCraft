package otto.djgun.djcraft.combat;

import java.util.List;
import java.util.OptionalLong;

import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;

/** Pure timeline judgment shared by the OpenAL client and server proof checks. */
public final class BeatJudgmentEvaluator {

    private BeatJudgmentEvaluator() {
    }

    public static HitResult evaluate(long currentTimeMs, TrackPack trackPack) {
        return evaluate(currentTimeMs, trackPack, true);
    }

    public static HitResult evaluate(long currentTimeMs, TrackPack trackPack, boolean enforceCanAttack) {
        if (trackPack == null || trackPack.timeline() == null) {
            return HitResult.miss(currentTimeMs);
        }

        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty()) {
            return HitResult.miss(currentTimeMs);
        }

        int floorIndex = floorIndex(combatLine, currentTimeMs);
        int previousIndex = floorIndex;
        int nextIndex = floorIndex + 1;
        BeatEvent previous = previousIndex >= 0 ? combatLine.get(previousIndex) : null;
        BeatEvent next = nextIndex < combatLine.size() ? combatLine.get(nextIndex) : null;

        long previousDistance = previous == null ? Long.MAX_VALUE : Math.abs(currentTimeMs - previous.t());
        long nextDistance = next == null ? Long.MAX_VALUE : Math.abs(currentTimeMs - next.t());
        int closestIndex = previousDistance <= nextDistance ? previousIndex : nextIndex;
        if (closestIndex < 0 || closestIndex >= combatLine.size()) {
            return HitResult.miss(currentTimeMs);
        }

        BeatEvent closestBeat = combatLine.get(closestIndex);
        BeatDefinition definition = trackPack.resolveDefinition(closestBeat);

        if (enforceCanAttack && !definition.canAttack()) {
            return HitResult.miss(definition, closestBeat, closestIndex, currentTimeMs);
        }

        long toleranceMs = toleranceMs(combatLine, closestIndex, currentTimeMs, definition.tolerance());
        long distance = Math.abs(currentTimeMs - closestBeat.t());
        if (distance <= toleranceMs) {
            return new HitResult(true, definition, closestBeat, closestIndex, currentTimeMs);
        }
        return HitResult.miss(definition, closestBeat, closestIndex, currentTimeMs);
    }

    /** Returns the right edge of the first beat strictly after {@code currentTimeMs}. */
    static OptionalLong nextWindowEndMs(long currentTimeMs, TrackPack trackPack) {
        return futureWindowEndMs(currentTimeMs, trackPack, 1);
    }

    /**
     * Schedules sourced damage at least one beat ahead: from a judgment window to the next beat,
     * otherwise to the beat after next.
     */
    static OptionalLong deferredDamageWindowEndMs(long currentTimeMs, TrackPack trackPack) {
        if (trackPack == null || trackPack.timeline() == null) {
            return OptionalLong.empty();
        }
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty()) {
            return OptionalLong.empty();
        }

        HitResult window = evaluate(currentTimeMs, trackPack, false);
        int candidateIndex = window.isHit()
                ? window.beatIndex() + 1
                : floorIndex(combatLine, currentTimeMs) + 2;
        int targetIndex = nextAttackableIndex(trackPack, combatLine, candidateIndex);
        return windowEndMs(trackPack, combatLine, targetIndex);
    }

    private static int nextAttackableIndex(TrackPack trackPack, List<BeatEvent> combatLine, int startIndex) {
        for (int index = Math.max(0, startIndex); index < combatLine.size(); index++) {
            if (trackPack.resolveDefinition(combatLine.get(index)).canAttack()) {
                return index;
            }
        }
        return -1;
    }

    private static OptionalLong futureWindowEndMs(long currentTimeMs, TrackPack trackPack, int beatsAhead) {
        if (trackPack == null || trackPack.timeline() == null) {
            return OptionalLong.empty();
        }

        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty()) {
            return OptionalLong.empty();
        }

        int nextIndex = floorIndex(combatLine, currentTimeMs) + beatsAhead;
        if (nextIndex < 0 || nextIndex >= combatLine.size()) {
            return OptionalLong.empty();
        }

        return windowEndMs(trackPack, combatLine, nextIndex);
    }

    private static OptionalLong windowEndMs(TrackPack trackPack, List<BeatEvent> combatLine, int beatIndex) {
        if (beatIndex < 0 || beatIndex >= combatLine.size()) {
            return OptionalLong.empty();
        }
        BeatEvent beat = combatLine.get(beatIndex);
        BeatDefinition definition = trackPack.resolveDefinition(beat);
        long toleranceMs = rightToleranceMs(combatLine, beatIndex, definition.tolerance());
        return OptionalLong.of(saturatingAdd(beat.t(), toleranceMs));
    }

    private static int floorIndex(List<BeatEvent> combatLine, long currentTimeMs) {
        int low = 0;
        int high = combatLine.size() - 1;
        int result = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (combatLine.get(middle).t() <= currentTimeMs) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return result;
    }

    private static long toleranceMs(List<BeatEvent> combatLine, int beatIndex, long currentTimeMs,
            float tolerance) {
        if (tolerance > 1.0f) {
            return Math.max(0L, (long) tolerance);
        }

        BeatEvent beat = combatLine.get(beatIndex);
        long interval;
        if (currentTimeMs < beat.t()) {
            interval = beatIndex > 0
                    ? beat.t() - combatLine.get(beatIndex - 1).t()
                    : beatIndex + 1 < combatLine.size() ? combatLine.get(beatIndex + 1).t() - beat.t() : 500L;
        } else {
            interval = beatIndex + 1 < combatLine.size()
                    ? combatLine.get(beatIndex + 1).t() - beat.t()
                    : beatIndex > 0 ? beat.t() - combatLine.get(beatIndex - 1).t() : 500L;
        }
        return Math.max(0L, (long) (interval * tolerance));
    }

    private static long rightToleranceMs(List<BeatEvent> combatLine, int beatIndex, float tolerance) {
        if (tolerance > 1.0f) {
            return Math.max(0L, (long) tolerance);
        }

        BeatEvent beat = combatLine.get(beatIndex);
        long interval = beatIndex + 1 < combatLine.size()
                ? combatLine.get(beatIndex + 1).t() - beat.t()
                : beatIndex > 0 ? beat.t() - combatLine.get(beatIndex - 1).t() : 500L;
        return Math.max(0L, (long) (interval * tolerance));
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
