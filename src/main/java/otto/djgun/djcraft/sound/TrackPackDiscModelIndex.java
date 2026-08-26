package otto.djgun.djcraft.sound;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Resolves the item-model predicate value used by written discs. */
public final class TrackPackDiscModelIndex {
    private TrackPackDiscModelIndex() {
    }

    public static Map<String, Float> build(Collection<String> loadedPackIds,
            Predicate<String> hasCustomDiscTexture) {
        List<String> sortedPackIds = loadedPackIds.stream().sorted().toList();
        Map<String, Float> indexes = new HashMap<>();
        for (int index = 0; index < sortedPackIds.size(); index++) {
            String packId = sortedPackIds.get(index);
            if (hasCustomDiscTexture.test(packId)) {
                indexes.put(packId, index + 1.0f);
            }
        }
        return Map.copyOf(indexes);
    }

    public static float resolve(String packId, Map<String, Float> modelIndexes) {
        return packId == null ? 0.0f : modelIndexes.getOrDefault(packId, 0.0f);
    }

}
