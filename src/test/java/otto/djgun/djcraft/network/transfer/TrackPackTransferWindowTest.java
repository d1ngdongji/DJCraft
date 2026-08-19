package otto.djgun.djcraft.network.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackPackTransferWindowTest {
    @Test
    void limitsAWindowToEightChunks() {
        int chunk = 256 * 1024;
        var segments = TrackPackTransferWindow.plan(0, 20L * chunk, chunk, 8);

        assertEquals(8, segments.size());
        assertEquals(0, segments.getFirst().offset());
        assertEquals(7L * chunk, segments.getLast().offset());
        assertTrue(segments.getLast().windowEnd());
        assertFalse(segments.get(6).windowEnd());
    }

    @Test
    void marksFinalPartialChunkAsWindowEnd() {
        int chunk = 256 * 1024;
        var segments = TrackPackTransferWindow.plan(2L * chunk, 3L * chunk + 17, chunk, 8);

        assertEquals(2, segments.size());
        assertEquals(17, segments.getLast().length());
        assertTrue(segments.getLast().windowEnd());
    }
}
