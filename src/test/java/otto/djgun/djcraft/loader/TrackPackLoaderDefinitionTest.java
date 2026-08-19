package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import otto.djgun.djcraft.data.BeatCategory;

class TrackPackLoaderDefinitionTest {

    @Test
    void parsesBeatCategoriesAndDefaultsUnknownValues() {
        String json = """
                {
                  "meta": {
                    "version": "1",
                    "author": "test",
                    "bpm": 120,
                    "difficulty": "normal",
                    "sound_file": "track.ogg",
                    "offset_ms": 0,
                    "playback_start_ms": 250,
                    "total_duration_ms": 1000,
                    "display_name": "test"
                  },
                  "definitions": {
                    "weak": { "category": "weakbeat" },
                    "down": { "category": "downbeat" },
                    "unknown": { "category": "other" }
                  },
                  "timeline": { "combat_line": [] }
                }
                """;

        var pack = TrackPackLoader.loadFromReader("test", new StringReader(json));

        assertNotNull(pack);
        assertEquals(250, pack.getPlaybackStartMs());
        assertFalse(pack.hasReachedPlaybackEnd(999));
        assertTrue(pack.hasReachedPlaybackEnd(1000));
        assertEquals(BeatCategory.WEAKBEAT, pack.getDefinition("weak").category());
        assertEquals(BeatCategory.DOWNBEAT, pack.getDefinition("down").category());
        assertEquals(BeatCategory.NORMAL, pack.getDefinition("unknown").category());
        assertEquals("djcraft:textures/gui/beats/blue_beat.png",
                pack.getDefinition("weak").texture());
    }

    @Test
    void parsesAndResolvesEventPropsOverrides() {
        String json = """
                {
                  "meta": {
                    "version": "1",
                    "author": "test",
                    "bpm": 120,
                    "difficulty": "normal",
                    "sound_file": "track.ogg",
                    "offset_ms": 0,
                    "total_duration_ms": 1000,
                    "display_name": "test"
                  },
                  "definitions": {
                    "normal": {
                      "can_attack": true,
                      "color": "#ffffff",
                      "category": "normal",
                      "tolerance": 0.1,
                      "texture": "beats/normal.gif",
                      "landing_x_percent": 25,
                      "spawn_advance_ms": 1800,
                      "hit_behavior": "bounce",
                      "matched_hit_behavior": "dissipate",
                      "miss_behavior": "dissipate",
                      "rotation_rpm": 45
                    }
                  },
                  "timeline": {
                    "combat_line": [{
                      "t": 500,
                      "type": "normal",
                      "props": {
                        "can_attack": false,
                        "color": "#ff0000",
                        "category": "downbeat",
                        "tolerance": 80,
                        "landing_x_percent": 75,
                        "spawn_advance_ms": 900,
                        "hit_behavior": "freeze_dissipate",
                        "matched_hit_behavior": "bounce",
                        "miss_behavior": "none",
                        "rotation_rpm": -15,
                        "exampleaddon:power": 2
                      }
                    }]
                  }
                }
                """;

        var pack = TrackPackLoader.loadFromReader("test", new StringReader(json));
        assertNotNull(pack);
        var beat = pack.timeline().combatLine().getFirst();
        var resolved = pack.resolveDefinition(beat);

        assertFalse(resolved.canAttack());
        assertEquals("#ff0000", resolved.color());
        assertEquals(BeatCategory.DOWNBEAT, resolved.category());
        assertEquals(80.0f, resolved.tolerance());
        assertEquals("beats/normal.gif", resolved.texture());
        assertEquals(75.0f, resolved.landingXPercent());
        assertEquals(900, resolved.spawnAdvanceMs());
        assertEquals(otto.djgun.djcraft.data.BeatPostJudgmentBehavior.FREEZE_DISSIPATE,
                resolved.hitBehavior());
        assertEquals(otto.djgun.djcraft.data.BeatPostJudgmentBehavior.NONE, resolved.missBehavior());
        assertEquals(otto.djgun.djcraft.data.BeatPostJudgmentBehavior.BOUNCE,
                resolved.matchedHitBehavior());
        assertEquals(-15.0f, resolved.rotationRpm());
        assertEquals(2.0, beat.props().get("exampleaddon:power"));
    }
}
