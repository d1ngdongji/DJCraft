package otto.djgun.djcraft.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiscStatisticsTest {
    @Test
    void clampsMalformedValuesAndMergesAbsoluteProgress() {
        assertEquals(DiscStatistics.EMPTY, new DiscStatistics(-2, -10L));
        assertEquals(new DiscStatistics(12, 8_000L),
                new DiscStatistics(12, 5_000L).merge(new DiscStatistics(9, 8_000L)));
    }

    @Test
    void gildingUsesCeilingOfEightyPercent() {
        assertEquals(9, DiscStatistics.gildedThreshold(11));
        assertFalse(new DiscStatistics(8, 0L).isGilded(11));
        assertTrue(new DiscStatistics(9, 0L).isGilded(11));
        assertFalse(new DiscStatistics(Integer.MAX_VALUE, 0L).isGilded(0));
    }
}
