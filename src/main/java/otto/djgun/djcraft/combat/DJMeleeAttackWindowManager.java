package otto.djgun.djcraft.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJSoftTargetMeleeBehavior;
import otto.djgun.djcraft.combat.access.PlayerAttackStrengthAccess;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.init.ModEnchantments;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

/** Server-authoritative movement sweep for every authorized DJ melee attack. */
public final class DJMeleeAttackWindowManager {
    public static final long WINDOW_TICKS = 2L;
    public static final long MACE_WINDOW_TICKS = WINDOW_TICKS * 2L;
    public static final long MACE_WINDOW_TICKS_PER_ENCHANTMENT_LEVEL = 3L;
    private static final double MAX_CONTINUOUS_MOVEMENT_SQR = 16.0 * 16.0;
    private static final Map<UUID, List<ActiveWindow>> ACTIVE = new HashMap<>();

    private DJMeleeAttackWindowManager() {
    }

    public static void activate(ServerPlayer player, DJSession session, DJActionContext action,
            DJMeleeBehavior behavior) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        int attackStrengthTicks = ((PlayerAttackStrengthAccess) player).djcraft$getAttackStrengthTicker();
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, true);

        DJItemBehavior family = DJItemBehaviorManager.resolve(action.stackSnapshot());
        int lingeringSweepLevel = family == DJItemBehavior.MACE
                ? ModEnchantments.level(player.level().registryAccess(), action.stackSnapshot(),
                        ModEnchantments.LINGERING_SWEEP)
                : 0;
        ActiveWindow window = new ActiveWindow(player.level().dimension(),
                player.level().getGameTime() + windowTicks(family, lingeringSweepLevel), eye, look, action,
                behavior, attackStrengthTicks,
                behavior instanceof DJAreaMeleeBehavior area
                        && area.resetFallDistanceAfterContact() && MaceItem.canSmashAttack(player));
        boolean finished = scan(player, window, eye);
        if (finished) {
            finish(player, window);
        } else {
            ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>()).add(window);
        }
    }

    static long windowTicks(DJItemBehavior family, int lingeringSweepLevel) {
        if (family != DJItemBehavior.MACE) {
            return WINDOW_TICKS;
        }
        int level = Math.max(0, lingeringSweepLevel);
        return MACE_WINDOW_TICKS + level * MACE_WINDOW_TICKS_PER_ENCHANTMENT_LEVEL;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, List<ActiveWindow>>> players = ACTIVE.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, List<ActiveWindow>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            Iterator<ActiveWindow> windows = entry.getValue().iterator();
            while (windows.hasNext()) {
                ActiveWindow window = windows.next();
                if (!isCurrent(player, window)) {
                    stopForClockDesyncIfStillActive(player, window);
                    windows.remove();
                    continue;
                }
                Vec3 eye = player.getEyePosition();
                if (eye.distanceToSqr(window.lastEye) > MAX_CONTINUOUS_MOVEMENT_SQR) {
                    stopForClockDesyncIfStillActive(player, window);
                    windows.remove();
                    continue;
                }
                boolean finished = scan(player, window, eye);
                window.lastEye = eye;
                if (finished || window.state.shouldFinish(player.level().getGameTime())) {
                    finish(player, window);
                    windows.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                players.remove();
            }
        }
    }

    private static boolean scan(ServerPlayer player, ActiveWindow window, Vec3 currentEye) {
        DJSweptMeleeVolume volume = DJSweptMeleeVolume.create(
                window.lastEye, currentEye, window.look, window.behavior);
        // Level#getEntities evaluates its predicate while iterating the live entity
        // section. Keep that predicate side-effect free because compatibility hooks in
        // target checks may remove or replace an entity.
        List<Entity> nearby = player.serverLevel().getEntities(player, volume.bounds(), target -> true);
        List<TargetCandidate> candidates = nearby.stream()
                .filter(target -> isLegalTarget(player, target)
                        && volume.intersects(target.getBoundingBox()))
                .map(target -> candidate(window, currentEye, target))
                .toList();
        if (window.behavior instanceof DJAreaMeleeBehavior area) {
            candidates.stream()
                    .sorted(TargetCandidate.AREA_ORDER)
                    .filter(candidate -> window.state.acceptContact(candidate.entity.getId()))
                    .forEach(candidate -> {
                        boolean primaryEffect = !area.primaryItemEffectsOnly() || !window.primaryUsed;
                        DJAreaMeleeCombatService.attackTarget(player, window.action,
                                candidate.entity, window.attackStrengthTicks, primaryEffect);
                        window.primaryUsed = true;
                        window.contacted = true;
                    });
            return false;
        }

        DJSoftTargetMeleeBehavior soft = (DJSoftTargetMeleeBehavior) window.behavior;
        DJSweptMeleeVolume direct = DJSweptMeleeVolume.directRay(
                window.lastEye, currentEye, window.look, soft.reach());
        TargetCandidate selected = candidates.stream()
                .filter(candidate -> direct.intersects(candidate.entity.getBoundingBox()))
                .min(TargetCandidate.DIRECT_ORDER)
                .orElseGet(() -> candidates.stream().min(TargetCandidate.SOFT_ORDER).orElse(null));
        if (selected == null) {
            return false;
        }
        window.state.acceptContact(selected.entity.getId());
        attackOrdinaryTarget(player, window, selected.entity);
        window.contacted = true;
        return true;
    }

    private static void attackOrdinaryTarget(ServerPlayer player, ActiveWindow window, Entity target) {
        PlayerAttackStrengthAccess accessor = (PlayerAttackStrengthAccess) player;
        int liveAttackStrengthTicks = accessor.djcraft$getAttackStrengthTicker();
        try {
            accessor.djcraft$setAttackStrengthTicker(window.attackStrengthTicks);
            DJCombatHandler.receivePendingJudgment(player, window.action);
            DJCombatHandler.attackAuthorized(player, target);
        } finally {
            DJCombatHandler.endVanillaAttack(player);
            accessor.djcraft$setAttackStrengthTicker(liveAttackStrengthTicks);
            DJCombatHandler.discardPendingJudgment(player.getUUID());
        }
    }

    private static TargetCandidate candidate(ActiveWindow window, Vec3 currentEye, Entity entity) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 movement = currentEye.subtract(window.lastEye);
        double fraction = closestFraction(window.lastEye, movement, center);
        Vec3 origin = window.lastEye.add(movement.scale(fraction));
        Vec3 toTarget = center.subtract(origin);
        double angle = angularDistance(window.look, toTarget);
        double contactFraction = contactFraction(window, box, currentEye);
        return new TargetCandidate(entity, contactFraction, angle, origin.distanceToSqr(center));
    }

    private static double contactFraction(ActiveWindow window, AABB box, Vec3 currentEye) {
        if (window.lastEye.distanceToSqr(currentEye) < 1.0E-10) {
            return 0.0;
        }
        DJSweptMeleeVolume initial = DJSweptMeleeVolume.create(
                window.lastEye, window.lastEye, window.look, window.behavior);
        if (initial.intersects(box)) {
            return 0.0;
        }
        double low = 0.0;
        double high = 1.0;
        for (int iteration = 0; iteration < 12; iteration++) {
            double middle = (low + high) * 0.5;
            Vec3 end = window.lastEye.lerp(currentEye, middle);
            if (DJSweptMeleeVolume.create(window.lastEye, end, window.look, window.behavior).intersects(box)) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private static double closestFraction(Vec3 start, Vec3 movement, Vec3 point) {
        double lengthSquared = movement.lengthSqr();
        if (lengthSquared < 1.0E-10) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, point.subtract(start).dot(movement) / lengthSquared));
    }

    private static double angularDistance(Vec3 look, Vec3 targetDirection) {
        Vec3 normalized = targetDirection.normalize();
        return Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(normalized))));
    }

    private static boolean isCurrent(ServerPlayer player, ActiveWindow window) {
        if (!player.isAlive() || !player.level().dimension().equals(window.dimension)
                || window.action.resolveLiveStack(player).isEmpty()) {
            return false;
        }
        return DJModeManager.getInstance().getSession(player)
                .filter(DJSession::isPlaying)
                .filter(session -> session.getSessionId() == window.action.sessionId())
                .isPresent();
    }

    private static boolean isLegalTarget(ServerPlayer player, Entity target) {
        boolean redirectableTrident = target instanceof net.minecraft.world.entity.projectile.ThrownTrident trident
                && trident instanceof DJThrownTridentExtension extension
                && extension.djcraft$canBeRedirected();
        if (target == player || !target.isAlive() || !target.isAttackable()
                || (!redirectableTrident && target.skipAttackInteraction(player)) || player.isAlliedTo(target)
                || target instanceof ItemEntity || target instanceof ExperienceOrb
                || target instanceof AbstractArrow arrow && !arrow.isAttackable()
                || !player.serverLevel().getWorldBorder().isWithinBounds(target.blockPosition())
                || !player.hasLineOfSight(target)) {
            return false;
        }
        if (target instanceof Player other) {
            return !other.isSpectator() && !other.isCreative() && player.canHarmPlayer(other);
        }
        return true;
    }

    private static void finish(ServerPlayer player, ActiveWindow window) {
        if (window.behavior instanceof DJAreaMeleeBehavior area) {
            damageAreaWeapon(player, window.action);
            if (window.resetFallDistance && window.contacted) {
                player.resetFallDistance();
            }
        }
        stopForClockDesyncIfStillActive(player, window);
    }

    private static void stopForClockDesyncIfStillActive(ServerPlayer player, ActiveWindow window) {
        boolean sameSession = DJModeManager.getInstance().getSession(player)
                .filter(DJSession::isPlaying)
                .filter(session -> session.getSessionId() == window.action.sessionId())
                .isPresent();
        if (window.action.stopAfterAction() && sameSession) {
            player.sendSystemMessage(Component.translatable("message.djcraft.clock_desync"));
            DJModeManager.getInstance().stopSession(player, StopReason.CLOCK_DESYNC);
            DJCraft.LOGGER.warn("Stopped DJ session for {} after five clock anomalies",
                    player.getName().getString());
        }
    }

    private static void damageAreaWeapon(ServerPlayer player, DJActionContext action) {
        ItemStack stack = action.resolveLiveStack(player);
        if (stack.isEmpty() || player.hasInfiniteMaterials()) {
            return;
        }
        int selected = player.getInventory().selected;
        boolean switchSlot = action.hand() == InteractionHand.MAIN_HAND
                && action.source().slot() >= 0 && action.source().slot() < 9
                && selected != action.source().slot();
        try {
            if (switchSlot) {
                player.getInventory().selected = action.source().slot();
            }
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        } finally {
            if (switchSlot) {
                player.getInventory().selected = selected;
            }
        }
    }

    public static void cleanupPlayer(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static final class ActiveWindow {
        private final ResourceKey<Level> dimension;
        private Vec3 lastEye;
        private final Vec3 look;
        private final DJActionContext action;
        private final DJMeleeBehavior behavior;
        private final int attackStrengthTicks;
        private final boolean resetFallDistance;
        private final DJMeleeWindowState state;
        private boolean primaryUsed;
        private boolean contacted;

        private ActiveWindow(ResourceKey<Level> dimension, long expiresAtTick, Vec3 lastEye,
                Vec3 look, DJActionContext action, DJMeleeBehavior behavior, int attackStrengthTicks,
                boolean resetFallDistance) {
            this.dimension = dimension;
            this.lastEye = lastEye;
            this.look = look;
            this.action = action;
            this.behavior = behavior;
            this.attackStrengthTicks = attackStrengthTicks;
            this.resetFallDistance = resetFallDistance;
            this.state = new DJMeleeWindowState(behavior.area(), expiresAtTick);
        }
    }

    private record TargetCandidate(Entity entity, double contactFraction, double angle, double distanceSquared) {
        private static final Comparator<TargetCandidate> AREA_ORDER = Comparator
                .comparingDouble(TargetCandidate::contactFraction)
                .thenComparingDouble(TargetCandidate::distanceSquared)
                .thenComparingInt(candidate -> candidate.entity.getId());
        private static final Comparator<TargetCandidate> DIRECT_ORDER = Comparator
                .comparingDouble(TargetCandidate::contactFraction)
                .thenComparingDouble(TargetCandidate::distanceSquared)
                .thenComparingInt(candidate -> candidate.entity.getId());
        private static final Comparator<TargetCandidate> SOFT_ORDER = Comparator
                .comparingDouble(TargetCandidate::angle)
                .thenComparingDouble(TargetCandidate::distanceSquared)
                .thenComparingInt(candidate -> candidate.entity.getId());
    }
}
