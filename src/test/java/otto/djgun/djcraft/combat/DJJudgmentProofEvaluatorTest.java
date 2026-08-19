package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatCategory;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.Timeline;
import otto.djgun.djcraft.data.TrackMeta;
import otto.djgun.djcraft.data.TrackPack;

class DJJudgmentProofEvaluatorTest {

    @Test
    void acceptsSelfConsistentHitAndUsesServerDefinitionCategory() {
        HitResult result = DJJudgmentProofEvaluator.evaluate(true, 1000L, 0, pack(BeatCategory.DOWNBEAT));

        assertTrue(result.isHit());
        assertEquals(BeatCategory.DOWNBEAT, result.beatData().category());
        assertEquals(0, result.beatIndex());
    }

    @Test
    void downgradesWrongBeatAndClientMissToMiss() {
        TrackPack serverPack = pack(BeatCategory.WEAKBEAT);

        HitResult wrongBeat = DJJudgmentProofEvaluator.evaluate(true, 1000L, 1, serverPack);
        HitResult clientMiss = DJJudgmentProofEvaluator.evaluate(false, 1000L, 0, serverPack);

        assertFalse(wrongBeat.isHit());
        assertFalse(clientMiss.isHit());
    }

    private static TrackPack pack(BeatCategory category) {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, category, 1, 100, null, null);
        TrackMeta meta = new TrackMeta("1", "test", 120, "test", "track.ogg", 0, 0, 2000, "test");
        Timeline timeline = new Timeline(List.of(new BeatEvent(1000, "normal")), Map.of());
        return new TrackPack("test", meta, null, Map.of("normal", definition), timeline);
    }
}
