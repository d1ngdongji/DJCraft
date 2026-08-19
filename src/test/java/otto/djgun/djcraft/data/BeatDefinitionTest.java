package otto.djgun.djcraft.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;

import org.junit.jupiter.api.Test;

class BeatDefinitionTest {

    @Test
    void appliesEverySupportedEventOverride() {
        BeatDefinition base = BeatDefinition.createDefault();

        BeatDefinition resolved = base.withOverrides(Map.ofEntries(
                Map.entry("can_attack", false),
                Map.entry("color", "#123456"),
                Map.entry("scale", 1.5),
                Map.entry("category", "weakbeat"),
                Map.entry("haptic_intensity", 0.25),
                Map.entry("tolerance", 75.0),
                Map.entry("particle", "djcraft:spark"),
                Map.entry("trigger", "accent"),
                Map.entry("texture", "beats/accent.gif"),
                Map.entry("landing_x_percent", 72.5),
                Map.entry("spawn_advance_ms", 2250.0),
                Map.entry("hit_behavior", "bounce"),
                Map.entry("miss_behavior", "dissipate"),
                Map.entry("rotation_rpm", -30.0),
                Map.entry("matched_hit_behavior", "none")));

        assertFalse(resolved.canAttack());
        assertEquals("#123456", resolved.color());
        assertEquals(1.5f, resolved.scale());
        assertEquals(BeatCategory.WEAKBEAT, resolved.category());
        assertEquals(0.25f, resolved.hapticIntensity());
        assertEquals(75.0f, resolved.tolerance());
        assertEquals("djcraft:spark", resolved.particle());
        assertEquals("accent", resolved.trigger());
        assertEquals("beats/accent.gif", resolved.texture());
        assertEquals(72.5f, resolved.landingXPercent());
        assertEquals(2250, resolved.spawnAdvanceMs());
        assertEquals(BeatPostJudgmentBehavior.BOUNCE, resolved.hitBehavior());
        assertEquals(BeatPostJudgmentBehavior.DISSIPATE, resolved.missBehavior());
        assertEquals(-30.0f, resolved.rotationRpm());
        assertEquals(BeatPostJudgmentBehavior.NONE, resolved.matchedHitBehavior());
    }

    @Test
    void emptyPropsReuseTheBaseDefinition() {
        BeatDefinition base = BeatDefinition.createDefault();

        assertSame(base, base.withOverrides(Map.of()));
    }

    @Test
    void invalidVisualOverridesFallBackWithoutChangingCombatValues() {
        BeatDefinition base = BeatDefinition.createDefault();
        BeatDefinition resolved = base.withOverrides(Map.of(
                "landing_x_percent", 101.0,
                "spawn_advance_ms", 0.0,
                "hit_behavior", "unknown",
                "rotation_rpm", 10_001.0));

        assertEquals(BeatDefinition.DEFAULT_LANDING_X_PERCENT, resolved.landingXPercent());
        assertEquals(BeatDefinition.DEFAULT_SPAWN_ADVANCE_MS, resolved.spawnAdvanceMs());
        assertEquals(BeatDefinition.DEFAULT_HIT_BEHAVIOR, resolved.hitBehavior());
        assertEquals(0.0f, resolved.rotationRpm());
    }

    @Test
    void legacyConstructorReceivesFallingDefaults() {
        BeatDefinition definition = new BeatDefinition(
                true, "#ffffff", 1.0f, BeatCategory.NORMAL, 1.0f, 0.1f, null, null);

        assertEquals(BeatDefinition.DEFAULT_TEXTURE, definition.texture());
        assertEquals(BeatDefinition.DEFAULT_LANDING_X_PERCENT, definition.landingXPercent());
        assertEquals(BeatDefinition.DEFAULT_SPAWN_ADVANCE_MS, definition.spawnAdvanceMs());
        assertEquals(BeatDefinition.DEFAULT_HIT_BEHAVIOR, definition.hitBehavior());
        assertEquals(BeatDefinition.DEFAULT_MISS_BEHAVIOR, definition.missBehavior());
        assertEquals(BeatDefinition.DEFAULT_HIT_BEHAVIOR, definition.matchedHitBehavior());
    }

    @Test
    void missingTextureUsesTheBuiltInBlueBeat() {
        BeatDefinition definition = new BeatDefinition(
                true, "#ffffff", 1.0f, BeatCategory.NORMAL, 1.0f, 0.1f, null, null,
                null, 50.0f, 1400, BeatPostJudgmentBehavior.FREEZE_DISSIPATE,
                BeatPostJudgmentBehavior.NONE, 0.0f);

        assertEquals("djcraft:textures/gui/beats/blue_beat.png", definition.texture());
    }

    @Test
    void matchedHitBehaviorDefaultsToTheOrdinaryHitBehavior() {
        BeatDefinition definition = new BeatDefinition(
                true, "#ffffff", 1.0f, BeatCategory.WEAKBEAT, 1.0f, 0.1f, null, null,
                BeatDefinition.DEFAULT_TEXTURE, 50.0f, 1400,
                BeatPostJudgmentBehavior.BOUNCE, BeatPostJudgmentBehavior.NONE, 0.0f);

        assertEquals(BeatPostJudgmentBehavior.BOUNCE, definition.matchedHitBehavior());
    }

    @Test
    void selectsMatchedBehaviorOnlyForSuccessfulCategoryMatches() {
        BeatDefinition definition = new BeatDefinition(
                true, "#ffffff", 1.0f, BeatCategory.DOWNBEAT, 1.0f, 0.1f, null, null,
                BeatDefinition.DEFAULT_TEXTURE, 50.0f, 1400,
                BeatPostJudgmentBehavior.DISSIPATE, BeatPostJudgmentBehavior.NONE, 0.0f,
                BeatPostJudgmentBehavior.BOUNCE);

        assertEquals(BeatPostJudgmentBehavior.DISSIPATE,
                definition.behaviorAfterJudgment(true, false));
        assertEquals(BeatPostJudgmentBehavior.BOUNCE,
                definition.behaviorAfterJudgment(true, true));
        assertEquals(BeatPostJudgmentBehavior.NONE,
                definition.behaviorAfterJudgment(false, true));
    }
}
