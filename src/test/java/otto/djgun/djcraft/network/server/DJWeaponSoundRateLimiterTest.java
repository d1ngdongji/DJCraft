package otto.djgun.djcraft.network.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DJWeaponSoundRateLimiterTest {
    @Test
    void rejectsDuplicatesAndLimitsBurstWhileRefillingAtFortyPerSecond() {
        var limiter = new DJWeaponSoundRateLimiter<String>(12.0, 2.0);
        for (long sequence = 1; sequence <= 12; sequence++) {
            assertTrue(limiter.accept("player", 100, sequence));
        }
        assertFalse(limiter.accept("player", 100, 13));
        assertFalse(limiter.accept("player", 101, 13));
        assertTrue(limiter.accept("player", 101, 14));
        assertFalse(limiter.accept("player", 101, 14));
    }

    @Test
    void keysHaveIndependentBuckets() {
        var limiter = new DJWeaponSoundRateLimiter<String>(1.0, 1.0);
        assertTrue(limiter.accept("a", 0, 1));
        assertTrue(limiter.accept("b", 0, 1));
        assertFalse(limiter.accept("a", 0, 2));
    }
}
