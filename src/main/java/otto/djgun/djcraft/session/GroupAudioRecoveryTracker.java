package otto.djgun.djcraft.session;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-playback recovery state; a new Playback owns a fresh tracker. */
public final class GroupAudioRecoveryTracker {
    public static final int MAX_RETRIES = 2;

    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> quarantined = new HashSet<>();

    public Result recordFailure(UUID playerId) {
        int attempt = attempts.merge(playerId, 1, Integer::sum);
        if (attempt <= MAX_RETRIES) {
            return new Result(Decision.RETRY, attempt);
        }
        quarantined.add(playerId);
        return new Result(Decision.QUARANTINE, attempt);
    }

    public void quarantine(UUID playerId) {
        quarantined.add(playerId);
    }

    public boolean isQuarantined(UUID playerId) {
        return quarantined.contains(playerId);
    }

    public enum Decision {
        RETRY,
        QUARANTINE
    }

    public record Result(Decision decision, int attempt) {
    }
}
