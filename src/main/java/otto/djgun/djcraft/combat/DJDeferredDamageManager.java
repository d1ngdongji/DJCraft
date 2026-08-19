package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.network.packet.DJDeferredDamagePromptPayload;
import otto.djgun.djcraft.network.packet.DJDeferredDamageStatePayload;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

/** Delays sourced non-player damage until the end of the next judgment window. */
public final class DJDeferredDamageManager {
    private static final Map<UUID, DJDeferredDamageQueue<DeferredDamage>> PENDING = new HashMap<>();
    private static final ThreadLocal<ReplayToken> REPLAYING = new ThreadLocal<>();

    private DJDeferredDamageManager() {
    }

    public static boolean tryDefer(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getOriginalAmount() <= 0.0F
                || isCurrentReplay(player, event.getSource())) {
            return false;
        }

        Entity causingEntity = event.getSource().getEntity();
        if (causingEntity == null || causingEntity instanceof Player) {
            return false;
        }

        DJSession session = DJModeManager.getInstance().getSession(player)
                .filter(DJSession::isPlaying)
                .orElse(null);
        if (session == null) {
            return false;
        }

        OptionalLong dueTimelineMs = BeatJudgmentEvaluator.deferredDamageWindowEndMs(
                session.getCurrentTimeMs(), session.getTrackPack());
        if (dueTimelineMs.isEmpty()) {
            return false;
        }

        PENDING.computeIfAbsent(player.getUUID(), ignored -> new DJDeferredDamageQueue<>())
                .add(session.getSessionId(), dueTimelineMs.getAsLong(),
                        new DeferredDamage(event.getSource(), event.getOriginalAmount()));
        event.setCanceled(true);
        DJCraft.LOGGER.debug(
                "Queued deferred damage for {} in DJ session {} at {} ms, due at {} ms, causing entity {}",
                player.getGameProfile().getName(), session.getSessionId(), session.getCurrentTimeMs(),
                dueTimelineMs.getAsLong(), causingEntity.getType());
        PacketDistributor.sendToPlayer(player, new DJDeferredDamageStatePayload(session.getSessionId(), true));
        PacketDistributor.sendToPlayer(player, new DJDeferredDamagePromptPayload(session.getSessionId()));
        return true;
    }

    /** Replays queued damage only after the session has activated its parry state. */
    public static void triggerForParry(ServerPlayer player, DJSession session) {
        DJDeferredDamageQueue<DeferredDamage> queue = PENDING.get(player.getUUID());
        if (queue == null) {
            return;
        }
        List<DeferredDamage> ready = queue.drainSession(session.getSessionId(),
                damage -> DJCombatHandler.isParryCandidate(player, damage.source, damage.originalAmount));
        if (!queue.hasSession(session.getSessionId())) {
            PacketDistributor.sendToPlayer(player,
                    new DJDeferredDamageStatePayload(session.getSessionId(), false));
        }
        if (queue.isEmpty()) {
            PENDING.remove(player.getUUID(), queue);
        }
        for (DeferredDamage damage : ready) {
            replay(player, damage);
        }
    }

    public static void tick(MinecraftServer server) {
        for (UUID playerId : List.copyOf(PENDING.keySet())) {
            DJDeferredDamageQueue<DeferredDamage> queue = PENDING.get(playerId);
            if (queue == null) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                PENDING.remove(playerId);
                continue;
            }

            DJSession session = DJModeManager.getInstance().getSession(player)
                    .filter(DJSession::isPlaying)
                    .orElse(null);
            Long activeSessionId = session == null ? null : session.getSessionId();
            long currentTimelineMs = session == null ? 0L : session.getCurrentTimeMs();
            List<DeferredDamage> ready = queue.drainReady(activeSessionId, currentTimelineMs);
            if (queue.isEmpty()) {
                PENDING.remove(playerId, queue);
                if (activeSessionId != null) {
                    PacketDistributor.sendToPlayer(player,
                            new DJDeferredDamageStatePayload(activeSessionId, false));
                }
            }

            for (DeferredDamage damage : ready) {
                if (!player.isAlive()) {
                    cleanupPlayer(playerId);
                    break;
                }
                replay(player, damage);
            }
        }
    }

    public static void cleanupPlayer(UUID playerId) {
        DJDeferredDamageQueue<DeferredDamage> removed = PENDING.remove(playerId);
        if (removed != null) {
            removed.clear();
        }
    }

    public static void clear() {
        PENDING.values().forEach(DJDeferredDamageQueue::clear);
        PENDING.clear();
        REPLAYING.remove();
    }

    private static void replay(ServerPlayer player, DeferredDamage damage) {
        player.invulnerableTime = 0;
        ReplayToken previous = REPLAYING.get();
        REPLAYING.set(new ReplayToken(player.getUUID(), damage.source));
        try {
            player.hurt(damage.source, damage.originalAmount);
        } finally {
            if (previous == null) {
                REPLAYING.remove();
            } else {
                REPLAYING.set(previous);
            }
        }
    }

    private static boolean isCurrentReplay(ServerPlayer player, DamageSource source) {
        ReplayToken token = REPLAYING.get();
        return token != null && token.playerId.equals(player.getUUID()) && token.source == source;
    }

    private record DeferredDamage(DamageSource source, float originalAmount) {
    }

    private record ReplayToken(UUID playerId, DamageSource source) {
    }
}
