package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

class TrackPackSemanticValidationTest {
    @Test
    void acceptsCompleteFiniteTrack() {
        assertNotNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "settings":{"crosshair_mode":"time","crosshair_time_ms":0,"volume_multiplier":1},
                "definitions":{"normal":{"color":"#12abEF","scale":1}},
                "timeline":{"combat_line":[{"t":0,"type":"normal"}]}
                """));
    }

    @Test
    void rejectsMissingMetaAndInvalidCoreValues() {
        assertNull(TrackPackLoader.loadFromReader("test", new StringReader("{}")));
        assertNull(load("""
                "meta":{"bpm":0,"total_duration_ms":1000},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":0},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
        assertNotNull(load("""
                "meta":{"bpm":120.5,"total_duration_ms":1000},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"playback_start_ms":1000,"total_duration_ms":1000},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"playback_start_ms":1001,"total_duration_ms":1000},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
    }

    @Test
    void rejectsInvalidSettingsColorTimesAndReferences() {
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "settings":{"crosshair_mode":"unknown"},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "definitions":{"normal":{"color":"red"}},
                "timeline":{"combat_line":[]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "definitions":{"normal":{}},
                "timeline":{"combat_line":[{"t":-1,"type":"normal"}]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "definitions":{"normal":{}},
                "timeline":{"combat_line":[{"t":0,"type":"missing"}]}
                """));
        assertNull(load("""
                "meta":{"bpm":120,"total_duration_ms":1000},
                "settings":{"crosshair_mode":"time","volume_multiplier":1e400},
                "definitions":{},"timeline":{"combat_line":[]}
                """));
    }

    private static otto.djgun.djcraft.data.TrackPack load(String body) {
        return TrackPackLoader.loadFromReader("test", new StringReader("{" + body + "}"));
    }
}
