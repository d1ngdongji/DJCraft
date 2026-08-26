package otto.djgun.djcraft.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TrackPackDiscModelIndexTest {
    private static final List<String> PACK_IDS = List.of("plain_after", "custom", "plain_before");
    private static final Set<String> CUSTOM_TEXTURES = Set.of("custom");
    private static final Map<String, Float> MODEL_INDEXES =
            TrackPackDiscModelIndex.build(PACK_IDS, CUSTOM_TEXTURES::contains);

    @Test
    void packWithoutDiscTextureUsesDefaultModel() {
        assertEquals(0.0f,
                TrackPackDiscModelIndex.resolve("plain_after", MODEL_INDEXES));
    }

    @Test
    void packWithDiscTextureUsesItsSortedPackIndex() {
        assertEquals(1.0f,
                TrackPackDiscModelIndex.resolve("custom", MODEL_INDEXES));
    }

    @Test
    void blankOrUnknownDiscUsesDefaultModel() {
        assertEquals(0.0f, TrackPackDiscModelIndex.resolve(null, MODEL_INDEXES));
        assertEquals(0.0f,
                TrackPackDiscModelIndex.resolve("missing", MODEL_INDEXES));
    }

    @Test
    void filesystemPredicateIsOnlyUsedWhileBuildingTheSnapshot() {
        AtomicInteger checks = new AtomicInteger();
        Map<String, Float> indexes = TrackPackDiscModelIndex.build(List.of("a_plain", "z_custom"), id -> {
            checks.incrementAndGet();
            return id.equals("z_custom");
        });

        assertEquals(2, checks.get());
        assertEquals(2.0f, TrackPackDiscModelIndex.resolve("z_custom", indexes));
        assertEquals(2.0f, TrackPackDiscModelIndex.resolve("z_custom", indexes));
        assertEquals(2, checks.get());
    }
}
