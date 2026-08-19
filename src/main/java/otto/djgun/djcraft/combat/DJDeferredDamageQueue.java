package otto.djgun.djcraft.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Pure FIFO scheduling state for server-authoritative deferred damage. */
final class DJDeferredDamageQueue<T> {
    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();

    void add(long sessionId, long dueTimelineMs, T value) {
        entries.addLast(new Entry<>(sessionId, dueTimelineMs, value));
    }

    List<T> drainReady(Long activeSessionId, long currentTimelineMs) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<T> ready = new ArrayList<>();
        Iterator<Entry<T>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry<T> entry = iterator.next();
            if (activeSessionId == null || entry.sessionId != activeSessionId
                    || currentTimelineMs >= entry.dueTimelineMs) {
                ready.add(entry.value);
                iterator.remove();
            }
        }
        return ready;
    }

    List<T> drainSession(long sessionId, Predicate<T> predicate) {
        if (entries.isEmpty()) {
            return List.of();
        }
        List<T> ready = new ArrayList<>();
        Iterator<Entry<T>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry<T> entry = iterator.next();
            if (entry.sessionId == sessionId && predicate.test(entry.value)) {
                ready.add(entry.value);
                iterator.remove();
            }
        }
        return ready;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    boolean hasSession(long sessionId) {
        return entries.stream().anyMatch(entry -> entry.sessionId == sessionId);
    }

    int size() {
        return entries.size();
    }

    void clear() {
        entries.clear();
    }

    private record Entry<T>(long sessionId, long dueTimelineMs, T value) {
    }
}
