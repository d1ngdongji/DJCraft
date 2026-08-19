package otto.djgun.djcraft.combat;

import java.util.UUID;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

public final class DJChargeJudgmentCache {
    private static final Map<UUID, DJActionContext> PENDING = new HashMap<>();

    private DJChargeJudgmentCache() {
    }

    public static void store(UUID playerId, DJActionContext action) {
        PENDING.put(playerId, action);
    }

    public static DJActionContext consume(UUID playerId, long sessionId, InteractionHand hand, ItemStack releaseStack,
            long gameTime) {
        DJActionContext action = PENDING.remove(playerId);
        if (action == null || action.sessionId() != sessionId || action.hand() != hand
                || gameTime > action.expiresAtTick()
                || action.stackSnapshot().getItem() != releaseStack.getItem()) {
            return null;
        }
        return action;
    }

    public static void cleanup(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static void cleanupExpired(long gameTime) {
        PENDING.values().removeIf(action -> gameTime > action.expiresAtTick());
    }

    public static void clear() {
        PENDING.clear();
    }
}
