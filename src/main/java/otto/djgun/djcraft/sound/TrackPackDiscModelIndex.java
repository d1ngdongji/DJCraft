package otto.djgun.djcraft.sound;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Resolves the item-model predicate value used by written discs. */
public final class TrackPackDiscModelIndex {
    private TrackPackDiscModelIndex() {
    }

    public static float resolve(String packId, Collection<String> loadedPackIds,
            Predicate<String> hasCustomDiscTexture) {
        if (packId == null || !hasCustomDiscTexture.test(packId)) {
            return 0.0f;
        }

        List<String> sortedPackIds = loadedPackIds.stream().sorted().toList();
        int index = sortedPackIds.indexOf(packId);
        return index >= 0 ? index + 1.0f : 0.0f;
    }
}
