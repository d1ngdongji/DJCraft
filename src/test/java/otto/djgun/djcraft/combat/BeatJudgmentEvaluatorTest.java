package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatCategory;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.Timeline;
import otto.djgun.djcraft.data.TrackMeta;
import otto.djgun.djcraft.data.TrackPack;

class BeatJudgmentEvaluatorTest {

    @Test
    void usesProportionalToleranceAndReturnsBeatMetadata() {
        TrackPack pack = pack(new BeatDefinition(true, "#fff", 1, BeatCategory.DOWNBEAT, 1, 0.1f, null, null));

        HitResult hit = BeatJudgmentEvaluator.evaluate(1049, pack);
        HitResult miss = BeatJudgmentEvaluator.evaluate(1051, pack);

        assertTrue(hit.isHit());
        assertEquals(0, hit.beatIndex());
        assertEquals(1049, hit.judgedAtMs());
        assertEquals(BeatCategory.DOWNBEAT, hit.beatData().category());
        assertFalse(miss.isHit());
    }

    @Test
    void supportsAbsoluteToleranceAndFirstLastBeats() {
        TrackPack pack = pack(new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 120, null, null));

        assertTrue(BeatJudgmentEvaluator.evaluate(880, pack).isHit());
        assertTrue(BeatJudgmentEvaluator.evaluate(2120, pack).isHit());
        assertFalse(BeatJudgmentEvaluator.evaluate(2121, pack).isHit());
    }

    @Test
    void behaviorsApplyTheirOwnCanAttackPolicies() {
        TrackPack pack = pack(new BeatDefinition(false, "#fff", 1, BeatCategory.WEAKBEAT, 1, 500, null, null));

        HitResult meleeResult = BeatJudgmentEvaluator.evaluate(
                1000, pack, DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
        HitResult shieldResult = BeatJudgmentEvaluator.evaluate(
                1000, pack, DJItemBehaviorRegistry.SHIELD.disabledByCanAttack());
        HitResult miningResult = BeatJudgmentEvaluator.evaluate(
                1000, pack, DJItemBehaviorRegistry.MINING.disabledByCanAttack());
        HitResult eatingResult = BeatJudgmentEvaluator.evaluate(
                1000, pack, DJItemBehaviorRegistry.EATING.disabledByCanAttack());

        assertTrue(DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.SHIELD.disabledByCanAttack());
        assertFalse(meleeResult.isHit());
        assertTrue(shieldResult.isHit());
        assertTrue(miningResult.isHit());
        assertTrue(eatingResult.isHit());
        assertEquals(0, meleeResult.beatIndex());
        assertEquals(BeatCategory.WEAKBEAT, meleeResult.beatData().category());
    }

    @Test
    void eventPropsOverrideDefinitionForJudgmentAndMetadata() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal", Map.of(
                        "can_attack", true,
                        "tolerance", 120.0,
                        "category", "downbeat")),
                new BeatEvent(1500, "normal", Map.of("can_attack", false)),
                new BeatEvent(2000, "normal")), Map.of());
        TrackPack pack = pack(definition, timeline);

        HitResult overriddenHit = BeatJudgmentEvaluator.evaluate(1120, pack);
        HitResult disabled = BeatJudgmentEvaluator.evaluate(1500, pack);

        assertTrue(overriddenHit.isHit());
        assertEquals(BeatCategory.DOWNBEAT, overriddenHit.beatData().category());
        assertFalse(disabled.isHit());
        assertFalse(disabled.beatData().canAttack());
    }

    @Test
    void invalidPropTypesAndUnknownCategoryKeepDefinitionValues() {
        BeatDefinition definition = new BeatDefinition(true, "#abc", 2, BeatCategory.WEAKBEAT, 0.5f, 80, "spark", "go");
        BeatEvent event = new BeatEvent(1000, "normal", Map.of(
                "can_attack", "false",
                "tolerance", "200",
                "category", "unknown",
                "color", 123.0));
        TrackPack pack = pack(definition, new Timeline(List.of(event), Map.of()));

        BeatDefinition resolved = pack.resolveDefinition(event);

        assertTrue(resolved.canAttack());
        assertEquals(80, resolved.tolerance());
        assertEquals(BeatCategory.WEAKBEAT, resolved.category());
        assertEquals("#abc", resolved.color());
    }

    @Test
    void nextWindowUsesTheBeatStrictlyAfterTheCurrentTime() {
        TrackPack pack = pack(new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null));

        assertEquals(1050L, BeatJudgmentEvaluator.nextWindowEndMs(999L, pack).orElseThrow());
        assertEquals(1550L, BeatJudgmentEvaluator.nextWindowEndMs(1000L, pack).orElseThrow());
        assertEquals(2050L, BeatJudgmentEvaluator.nextWindowEndMs(1500L, pack).orElseThrow());
        assertTrue(BeatJudgmentEvaluator.nextWindowEndMs(2000L, pack).isEmpty());
    }

    @Test
    void nextWindowUsesAbsoluteAndEventOverriddenToleranceRegardlessOfCanAttack() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal", Map.of("tolerance", 120.0, "can_attack", false)),
                new BeatEvent(1500, "normal")), Map.of());
        TrackPack pack = pack(definition, timeline);

        assertEquals(1120L, BeatJudgmentEvaluator.nextWindowEndMs(999L, pack).orElseThrow());
    }

    @Test
    void nextWindowUsesSingleBeatFallbackAndOffsetCorrectedTimelineTime() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        TrackMeta meta = new TrackMeta("1", "test", 120, "test", "track.ogg", 250, 0, 3000, "test");
        TrackPack pack = new TrackPack("test", meta, null, Map.of("normal", definition),
                new Timeline(List.of(new BeatEvent(1000, "normal")), Map.of()));
        long rawPlaybackTimeMs = 1249L;

        assertEquals(1050L, BeatJudgmentEvaluator.nextWindowEndMs(
                rawPlaybackTimeMs - pack.getOffsetMs(), pack).orElseThrow());
    }

    @Test
    void deferredDamageUsesNextBeatInsideAWindowAndBeatAfterNextOutside() {
        TrackPack pack = pack(new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null));

        assertEquals(1550L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(951L, pack).orElseThrow());
        assertEquals(1550L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(999L, pack).orElseThrow());
        assertEquals(1550L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(1000L, pack).orElseThrow());
        assertEquals(2050L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(1200L, pack).orElseThrow());
        assertTrue(BeatJudgmentEvaluator.deferredDamageWindowEndMs(1600L, pack).isEmpty());
    }

    @Test
    void deferredDamageWindowDetectionUsesResolvedToleranceAndIgnoresCanAttack() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal", Map.of("tolerance", 120.0, "can_attack", false)),
                new BeatEvent(1500, "normal"),
                new BeatEvent(2000, "normal")), Map.of());
        TrackPack pack = pack(definition, timeline);

        assertEquals(1550L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(880L, pack).orElseThrow());
        assertEquals(1550L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(879L, pack).orElseThrow());
    }

    @Test
    void deferredDamageSkipsConsecutiveResolvedCanAttackFalseTargets() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal"),
                new BeatEvent(1500, "normal", Map.of("can_attack", false)),
                new BeatEvent(2000, "normal", Map.of("can_attack", false)),
                new BeatEvent(2500, "normal", Map.of("tolerance", 125.0))), Map.of());
        TrackPack pack = pack(definition, timeline);

        assertEquals(2625L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(1000L, pack).orElseThrow());
        assertEquals(2625L, BeatJudgmentEvaluator.deferredDamageWindowEndMs(1200L, pack).orElseThrow());
    }

    @Test
    void deferredDamageDoesNotDeferWhenNoAttackableTargetRemains() {
        BeatDefinition definition = new BeatDefinition(true, "#fff", 1, BeatCategory.NORMAL, 1, 0.1f, null, null);
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal"),
                new BeatEvent(1500, "normal", Map.of("can_attack", false)),
                new BeatEvent(2000, "normal", Map.of("can_attack", false))), Map.of());
        TrackPack pack = pack(definition, timeline);

        assertTrue(BeatJudgmentEvaluator.deferredDamageWindowEndMs(1000L, pack).isEmpty());
    }

    private static TrackPack pack(BeatDefinition definition) {
        Timeline timeline = new Timeline(List.of(
                new BeatEvent(1000, "normal"),
                new BeatEvent(1500, "normal"),
                new BeatEvent(2000, "normal")), Map.of());
        return pack(definition, timeline);
    }

    private static TrackPack pack(BeatDefinition definition, Timeline timeline) {
        TrackMeta meta = new TrackMeta("1", "test", 120, "test", "track.ogg", 0, 0, 3000, "test");
        return new TrackPack("test", meta, null, Map.of("normal", definition), timeline);
    }
}
