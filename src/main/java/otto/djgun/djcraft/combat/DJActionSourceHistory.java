package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/** Short server-side history used to validate an action racing a hotbar selection packet. */
public final class DJActionSourceHistory {
    private static final long SOURCE_GRACE_TICKS = DJActionContext.DEFAULT_TTL_TICKS;
    private static final Map<UUID, long[]> RECENT_SELECTIONS = new HashMap<>();

    private DJActionSourceHistory() {
    }

    public static void recordSelectionChange(ServerPlayer player, int nextSlot) {
        int previousSlot = player.getInventory().selected;
        if (previousSlot == nextSlot || previousSlot < 0 || previousSlot >= 9
                || nextSlot < 0 || nextSlot >= 9) {
            return;
        }
        long[] expiries = RECENT_SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new long[9]);
        recordSelection(expiries, previousSlot, nextSlot, player.level().getGameTime());
    }

    public static boolean wasRecentlySelected(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= 9) {
            return false;
        }
        long[] expiries = RECENT_SELECTIONS.get(player.getUUID());
        return expiries != null && isRecent(expiries, slot, player.level().getGameTime());
    }

    public static void cleanupPlayer(UUID playerId) {
        RECENT_SELECTIONS.remove(playerId);
    }

    public static void cleanupExpired(long gameTime) {
        RECENT_SELECTIONS.values().removeIf(expiries -> allExpired(expiries, gameTime));
    }

    public static void clear() {
        RECENT_SELECTIONS.clear();
    }

    static void recordSelection(long[] expiries, int previousSlot, int nextSlot, long gameTime) {
        if (previousSlot != nextSlot && previousSlot >= 0 && previousSlot < expiries.length
                && nextSlot >= 0 && nextSlot < expiries.length) {
            expiries[previousSlot] = gameTime + SOURCE_GRACE_TICKS;
        }
    }

    static boolean isRecent(long[] expiries, int slot, long gameTime) {
        long expiry = slot >= 0 && slot < expiries.length ? expiries[slot] : 0L;
        return expiry != 0L && gameTime <= expiry;
    }

    private static boolean allExpired(long[] expiries, long gameTime) {
        for (long expiry : expiries) {
            if (expiry != 0L && gameTime <= expiry) {
                return false;
            }
        }
        return true;
    }
}
