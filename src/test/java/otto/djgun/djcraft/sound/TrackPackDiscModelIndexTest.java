package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TrackPackDiscModelIndexTest {
    private static final List<String> PACK_IDS = List.of("plain_after", "custom", "plain_before");
    private static final Set<String> CUSTOM_TEXTURES = Set.of("custom");

    @Test
    void packWithoutDiscTextureUsesDefaultModel() {
        assertEquals(0.0f,
                TrackPackDiscModelIndex.resolve("plain_after", PACK_IDS, CUSTOM_TEXTURES::contains));
    }

    @Test
    void packWithDiscTextureUsesItsSortedPackIndex() {
        assertEquals(1.0f,
                TrackPackDiscModelIndex.resolve("custom", PACK_IDS, CUSTOM_TEXTURES::contains));
    }

    @Test
    void blankOrUnknownDiscUsesDefaultModel() {
        assertEquals(0.0f, TrackPackDiscModelIndex.resolve(null, PACK_IDS, CUSTOM_TEXTURES::contains));
        assertEquals(0.0f,
                TrackPackDiscModelIndex.resolve("missing", PACK_IDS, CUSTOM_TEXTURES::contains));
    }
}
