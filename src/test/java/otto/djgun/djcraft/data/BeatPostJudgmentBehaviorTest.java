package otto.djgun.djcraft.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeatPostJudgmentBehaviorTest {
    @Test
    void parsesSupportedIdsAndFallsBackForUnknownIds() {
        assertEquals(BeatPostJudgmentBehavior.NONE,
                BeatPostJudgmentBehavior.fromId("none", BeatPostJudgmentBehavior.BOUNCE));
        assertEquals(BeatPostJudgmentBehavior.FREEZE_DISSIPATE,
                BeatPostJudgmentBehavior.fromId("FREEZE_DISSIPATE", BeatPostJudgmentBehavior.NONE));
        assertEquals(BeatPostJudgmentBehavior.DISSIPATE,
                BeatPostJudgmentBehavior.fromId("dissipate", BeatPostJudgmentBehavior.NONE));
        assertEquals(BeatPostJudgmentBehavior.BOUNCE,
                BeatPostJudgmentBehavior.fromId("bounce", BeatPostJudgmentBehavior.NONE));
        assertEquals(BeatPostJudgmentBehavior.NONE,
                BeatPostJudgmentBehavior.fromId("other", BeatPostJudgmentBehavior.NONE));
    }
}
