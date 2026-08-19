package otto.djgun.djcraft.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.util.BeatGridUtil;

/** Server-owned one-beat delayed firing state for automatic-charge ray weapons. */
public final class DJAutoChargeManager {
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private DJAutoChargeManager() {
    }

    public static boolean begin(ServerPlayer player, DJSession session, DJActionContext action,
            double targetVirtualBeat) {
        double currentVirtualBeat = currentVirtualBeat(session);
        if (!Double.isFinite(targetVirtualBeat) || targetVirtualBeat <= currentVirtualBeat + 1.0E-9) {
            return false;
        }
        PENDING.put(player.getUUID(), new Pending(action, targetVirtualBeat));
        return true;
    }

    public static void tick(MinecraftServer server) {
        List<Ready> ready = new ArrayList<>();
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null || !isValid(player, pending)) {
                iterator.remove();
                continue;
            }
            DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
            if (session != null && DJAutoChargeTiming.isDue(
                    currentVirtualBeat(session), pending.targetVirtualBeat())) {
                iterator.remove();
                ready.add(new Ready(entry.getKey(), pending));
            }
        }

        // Firing may synchronously kill a player, whose death handler removes another
        // pending action. Dispatch only after the map iterator has been exhausted.
        for (Ready action : ready) {
            ServerPlayer player = server.getPlayerList().getPlayer(action.playerId());
            if (player == null || !isValid(player, action.pending())) {
                continue;
            }
            DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
            if (session != null) {
                fire(player, session, action.pending());
            }
        }
    }

    private static void fire(ServerPlayer player, DJSession session, Pending pending) {
        ItemStack stack = pending.action().source().resolve(player, pending.action().hand());
        DJRayWeaponProfile profile = DJRayWeaponManager.resolve(stack).orElse(null);
        if (profile == null || profile.autoChargeBeats() <= 0) {
            return;
        }
        DJActionContext stored = pending.action();
        DJActionContext firing = new DJActionContext(stored.sessionId(), stored.sequence(), stored.hand(),
                stored.source(), stored.stackSnapshot(), stored.result(), stored.damageAuthorized(),
                stored.stopAfterAction(),
                player.level().getGameTime() + DJActionContext.DEFAULT_TTL_TICKS,
                stored.damageMultiplier(), stored.soundProfileId());
        DJRayWeaponCombatService.fire(player, firing, profile);
        if (stored.stopAfterAction()) {
            player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
            DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
            DJCraft.LOGGER.warn("Stopped DJ session for {} after delayed action and five clock anomalies",
                    player.getName().getString());
        }
    }

    private static double currentVirtualBeat(DJSession session) {
        return BeatGridUtil.getVirtualBeat(session.getCurrentTimeMs(),
                session.getTrackPack().timeline().combatLine());
    }

    public static void cleanupPlayer(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static void clear() {
        PENDING.clear();
    }

    private static boolean isValid(ServerPlayer player, Pending pending) {
        DJActionContext action = pending.action();
        DJSession session = DJModeManager.getInstance().getSession(player).orElse(null);
        if (!player.isAlive() || session == null || !session.isPlaying()
                || session.getSessionId() != action.sessionId()) {
            return false;
        }
        if (action.hand() == InteractionHand.MAIN_HAND
                && player.getInventory().selected != action.source().slot()) {
            return false;
        }
        ItemStack live = action.source().resolve(player, action.hand());
        return !live.isEmpty() && ItemStack.isSameItemSameComponents(live, action.stackSnapshot())
                && DJRayWeaponManager.resolve(live).map(profile -> profile.autoChargeBeats() > 0).orElse(false);
    }

    private record Pending(DJActionContext action, double targetVirtualBeat) {
    }

    private record Ready(UUID playerId, Pending pending) {
    }
}
