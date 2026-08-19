package otto.djgun.djcraft.session;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJServerClockTest {
    @Test
    void sharedClockUsesOneMonotonicTimeline() {
        GroupPlaybackClock clock = new GroupPlaybackClock(250L);
        long first = clock.currentTimeMs();
        long second = clock.currentTimeMs();
        assertTrue(first >= 250L);
        assertTrue(second >= first);
    }

    @Test
    void standaloneClockCanAlignBackwardWithoutAffectingSharedClockContract() {
        DJServerClock clock = new StandaloneDJServerClock();
        long before = clock.currentTimeMs();
        clock.alignBackward(500L);
        assertTrue(clock.currentTimeMs() <= before);
    }

    @Test
    void standaloneClockCanStartAtConfiguredPlaybackPosition() {
        DJServerClock clock = new StandaloneDJServerClock(750L);
        assertTrue(clock.currentTimeMs() >= 750L);
    }
}
