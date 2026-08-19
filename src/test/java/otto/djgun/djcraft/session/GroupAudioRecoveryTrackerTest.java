package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class GroupAudioRecoveryTrackerTest {
    @Test
    void retriesTwiceThenQuarantinesCurrentPlayback() {
        GroupAudioRecoveryTracker tracker = new GroupAudioRecoveryTracker();
        UUID playerId = UUID.randomUUID();

        assertEquals(GroupAudioRecoveryTracker.Decision.RETRY,
                tracker.recordFailure(playerId).decision());
        assertEquals(GroupAudioRecoveryTracker.Decision.RETRY,
                tracker.recordFailure(playerId).decision());
        assertFalse(tracker.isQuarantined(playerId));
        assertEquals(GroupAudioRecoveryTracker.Decision.QUARANTINE,
                tracker.recordFailure(playerId).decision());
        assertTrue(tracker.isQuarantined(playerId));
    }

    @Test
    void aNewPlaybackGetsIndependentRecoveryState() {
        UUID playerId = UUID.randomUUID();
        GroupAudioRecoveryTracker oldPlayback = new GroupAudioRecoveryTracker();
        oldPlayback.quarantine(playerId);

        assertFalse(new GroupAudioRecoveryTracker().isQuarantined(playerId));
    }
}
