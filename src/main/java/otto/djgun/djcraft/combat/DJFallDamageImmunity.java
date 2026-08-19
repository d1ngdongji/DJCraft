package otto.djgun.djcraft.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One-shot fall-damage immunity armed by DJ air movement abilities. */
public final class DJFallDamageImmunity {
    private static final Set<UUID> ARMED_PLAYERS = new HashSet<>();

    private DJFallDamageImmunity() {
    }

    public static void arm(UUID playerId) {
        ARMED_PLAYERS.add(playerId);
    }

    public static boolean consume(UUID playerId) {
        return ARMED_PLAYERS.remove(playerId);
    }

    public static void clear(UUID playerId) {
        ARMED_PLAYERS.remove(playerId);
    }

    public static void clear() {
        ARMED_PLAYERS.clear();
    }
}
