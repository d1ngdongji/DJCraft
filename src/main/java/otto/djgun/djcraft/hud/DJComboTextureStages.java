package otto.djgun.djcraft.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Immutable threshold selection and per-digit inheritance for combo texture styles. */
final class DJComboTextureStages<T> {
    private static final int DIGIT_COUNT = 10;
    private final NavigableMap<Integer, List<T>> stages;

    private DJComboTextureStages(NavigableMap<Integer, List<T>> stages) {
        this.stages = stages;
    }

    static <T> DJComboTextureStages<T> create(List<T> base, List<T> builtInFifty,
            Map<Integer, Map<Integer, T>> customOverrides) {
        requireDigitSet(base, "base");
        requireDigitSet(builtInFifty, "built-in 50");

        NavigableMap<Integer, List<T>> resolved = new TreeMap<>();
        List<T> current = List.copyOf(base);
        resolved.put(1, current);

        if (customOverrides.isEmpty()) {
            resolved.put(50, List.copyOf(builtInFifty));
        } else {
            for (Map.Entry<Integer, Map<Integer, T>> stage : new TreeMap<>(customOverrides).entrySet()) {
                int threshold = stage.getKey();
                if (threshold < 1) {
                    throw new IllegalArgumentException("Combo threshold must be positive");
                }
                List<T> inherited = new ArrayList<>(current);
                for (Map.Entry<Integer, T> override : stage.getValue().entrySet()) {
                    int digit = override.getKey();
                    if (digit < 0 || digit >= DIGIT_COUNT) {
                        throw new IllegalArgumentException("Combo digit must be between 0 and 9");
                    }
                    inherited.set(digit, override.getValue());
                }
                current = List.copyOf(inherited);
                resolved.put(threshold, current);
            }
        }
        return new DJComboTextureStages<>(java.util.Collections.unmodifiableNavigableMap(resolved));
    }

    T resolve(int combo, int digit) {
        if (digit < 0 || digit >= DIGIT_COUNT) {
            throw new IllegalArgumentException("Combo digit must be between 0 and 9");
        }
        Map.Entry<Integer, List<T>> stage = stages.floorEntry(Math.max(1, combo));
        return stage.getValue().get(digit);
    }

    private static void requireDigitSet(List<?> styles, String name) {
        if (styles.size() != DIGIT_COUNT) {
            throw new IllegalArgumentException(name + " combo style set must contain 10 digits");
        }
    }
}
