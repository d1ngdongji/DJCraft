package otto.djgun.djcraft.combat;

import java.util.Map;

public record DJItemTimingSnapshot<I, K>(Map<I, DJItemTimingProfile> byId,
        Map<K, DJItemTimingProfile> byKey) {
    public DJItemTimingSnapshot {
        byId = Map.copyOf(byId);
        byKey = Map.copyOf(byKey);
    }

    public static <I, K> DJItemTimingSnapshot<I, K> empty() {
        return new DJItemTimingSnapshot<>(Map.of(), Map.of());
    }
}
