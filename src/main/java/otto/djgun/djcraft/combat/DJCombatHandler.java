package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.access.LivingEntityEquipmentAccess;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJNetworkGroupManager;
import otto.djgun.djcraft.session.DJShieldState;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.network.packet.DJWeaponSoundBroadcastPayload;
import otto.djgun.djcraft.network.packet.DJParrySuccessPayload;
import otto.djgun.djcraft.network.packet.DJShieldParryWindowPayload;
import otto.djgun.djcraft.network.server.DJGameplaySoundBroadcaster;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;
import otto.djgun.djcraft.init.ModEffects;
import otto.djgun.djcraft.init.ModEnchantments;

public final class DJCombatHandler {
    private static final long TTL_TICKS = 4L;
    private static final Map<UUID, DJActionContext> PENDING_JUDGMENTS = new HashMap<>();
    private static final Map<DamageKey, PendingDamage> PENDING_DAMAGE = new HashMap<>();
    private static final Map<UUID, ConfirmedDamage> DJ_HIT_TARGETS = new HashMap<>();
    private static final Map<UUID, FiringJudgment> FIRING_PROJECTILES = new HashMap<>();
    private static final Map<UUID, ProjectileJudgment> PROJECTILE_JUDGMENTS = new HashMap<>();
    private static final ThreadLocal<Boolean> REFLECTING_DAMAGE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> APPLYING_MOVEMENT_ABILITY_DAMAGE =
            ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> AUTHORIZED_MELEE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<AttackSlotOverride> ATTACK_SLOT_OVERRIDE = new ThreadLocal<>();

    private DJCombatHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()
                && DJNetworkGroupManager.getInstance().isCombatSuppressed(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (!player.level().isClientSide() && DJModeManager.getInstance().isInDJMode(player)
                && !AUTHORIZED_MELEE.get()) {
            event.setCanceled(true);
            return;
        }
        if (!player.level().isClientSide() && player.getMainHandItem().isEmpty()
                && DJModeManager.getInstance().isInDJMode(player)) {
            event.setCanceled(true);
        }
    }

    public static void receivePendingJudgment(Player player, DJActionContext action) {
        PENDING_JUDGMENTS.put(player.getUUID(), action);
    }

    public static void discardPendingJudgment(UUID playerId) {
        PENDING_JUDGMENTS.remove(playerId);
    }

    /** Executes the only server-authorized Player#attack path during an active DJ session. */
    public static void attackAuthorized(ServerPlayer player, Entity target) {
        AUTHORIZED_MELEE.set(true);
        try {
            player.attack(target);
        } finally {
            AUTHORIZED_MELEE.set(false);
        }
    }

    /** Binds vanilla Player#attack to the server-validated action source until that invocation returns. */
    public static void beginVanillaAttack(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || ATTACK_SLOT_OVERRIDE.get() != null) {
            return;
        }
        DJActionContext action = PENDING_JUDGMENTS.get(player.getUUID());
        if (action == null || action.hand() != InteractionHand.MAIN_HAND
                || !action.isCurrent(serverPlayer,
                        DJModeManager.getInstance().getSession(player).map(value -> value.getSessionId()).orElse(-1L))) {
            return;
        }
        int actionSlot = action.source().slot();
        int selectedSlot = player.getInventory().selected;
        ItemStack actionStack = action.resolveLiveStack(serverPlayer);
        if (actionSlot < 0 || actionSlot >= 9 || actionStack.isEmpty()) {
            return;
        }
        boolean changedSlot = actionSlot != selectedSlot;
        if (changedSlot) {
            player.getInventory().selected = actionSlot;
        }

        ItemStack appliedMainHand = ((LivingEntityEquipmentAccess) player)
                .djcraft$getLastHandItem(EquipmentSlot.MAINHAND).copy();
        ItemStack actionAttributes = actionStack.copy();
        boolean changedAttributes = !ItemStack.matches(appliedMainHand, actionAttributes);
        if (changedAttributes) {
            replaceMainHandAttributes(serverPlayer, appliedMainHand, actionAttributes);
        }
        ATTACK_SLOT_OVERRIDE.set(new AttackSlotOverride(player.getUUID(), selectedSlot, changedSlot,
                appliedMainHand, actionAttributes, changedAttributes));
    }

    public static void endVanillaAttack(Player player) {
        if (player instanceof ServerPlayer) {
            PENDING_JUDGMENTS.remove(player.getUUID());
        }
        AttackSlotOverride override = ATTACK_SLOT_OVERRIDE.get();
        if (override == null || !override.playerId.equals(player.getUUID())) {
            return;
        }
        try {
            if (override.changedAttributes) {
                replaceMainHandAttributes((ServerPlayer) player, override.actionAttributes,
                        override.appliedMainHand);
            }
        } finally {
            if (override.changedSlot) {
                player.getInventory().selected = override.selectedSlot;
            }
            ATTACK_SLOT_OVERRIDE.remove();
        }
    }

    private static void replaceMainHandAttributes(ServerPlayer player, ItemStack removeStack, ItemStack addStack) {
        removeStack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = player.getAttributes().getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier);
            }
        });
        addStack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = player.getAttributes().getInstance(attribute);
            if (instance != null) {
                instance.removeModifier(modifier.id());
                instance.addTransientModifier(modifier);
            }
        });
    }

    static boolean hurtFromMovementAbility(ServerPlayer attacker, LivingEntity target, float damage) {
        APPLYING_MOVEMENT_ABILITY_DAMAGE.set(true);
        try {
            return target.hurt(attacker.damageSources().playerAttack(attacker), damage);
        } finally {
            APPLYING_MOVEMENT_ABILITY_DAMAGE.set(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (REFLECTING_DAMAGE.get()) {
            return;
        }
        if (tryParry(event)) {
            return;
        }
        if (APPLYING_MOVEMENT_ABILITY_DAMAGE.get()) {
            return;
        }
        if (DJDeferredDamageManager.tryDefer(event)) {
            return;
        }

        var directEntity = event.getSource().getDirectEntity();
        if (directEntity != null) {
            ProjectileJudgment projectile = PROJECTILE_JUDGMENTS.get(directEntity.getUUID());
            if (projectile != null && event.getEntity().level().getGameTime() <= projectile.expiresAtTick) {
                LivingEntity target = event.getEntity();
                target.invulnerableTime = 0;
                DamageKey key = new DamageKey(projectile.attackerId, target.getUUID(), projectile.sessionId);
                PENDING_DAMAGE.put(key,
                        new PendingDamage(projectile.sequence, projectile.damageMultiplier, false,
                                projectile.beatHit, target.level().getGameTime() + TTL_TICKS,
                                projectile.hand, projectile.profileId));
                return;
            }
        }

        Player attacker = getPlayerAttacker(event);
        if (attacker == null) {
            return;
        }
        if (DJNetworkGroupManager.getInstance().isCombatSuppressed(attacker.getUUID())) {
            event.setCanceled(true);
            return;
        }

        var session = DJModeManager.getInstance().getSession(attacker).orElse(null);
        if (session == null || !session.isPlaying()) {
            return;
        }

        DJActionContext pending = PENDING_JUDGMENTS.remove(attacker.getUUID());
        long gameTime = attacker.level().getGameTime();
        if (pending == null || !(attacker instanceof ServerPlayer serverAttacker)
                || !pending.isCurrent(serverAttacker, session.getSessionId())) {
            event.setCanceled(true);
            DJCraft.LOGGER.debug("DJ attack had no current matching judgment for {}, treating as MISS",
                    attacker.getName().getString());
            return;
        }

        if (!pending.damageAuthorized()) {
            event.setCanceled(true);
            stopForClockDesync(attacker, pending.stopAfterAction());
            return;
        }

        LivingEntity target = event.getEntity();
        target.invulnerableTime = 0;
        DamageKey key = new DamageKey(attacker.getUUID(), target.getUUID(), pending.sessionId());
        PENDING_DAMAGE.put(key,
                new PendingDamage(pending.sequence(), pending.damageMultiplier(), pending.stopAfterAction(),
                        pending.result().isHit(), gameTime + TTL_TICKS,
                        pending.hand(), pending.soundProfileId()));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShieldUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        if (DJNetworkGroupManager.getInstance().isCombatSuppressed(player.getUUID())
                && DJItemBehaviorManager.resolve(event.getItem()) != DJItemBehavior.NONE) {
            event.setCanceled(true);
            return;
        }
        if (!DJItemBehaviorManager.is(event.getItem(), DJItemBehavior.SHIELD)) {
            return;
        }
        var session = DJModeManager.getInstance().getSession(player).filter(value -> value.isPlaying()).orElse(null);
        if (session == null) {
            return;
        }
        if (!session.beginShieldUse(event.getHand(), event.getItem().getItem(), player.level().getGameTime())) {
            event.setCanceled(true);
            return;
        }
        long parryExpiresAtMs = session.getShieldParryExpiresAtMs();
        if (parryExpiresAtMs > session.getCurrentTimeMs() && player instanceof ServerPlayer serverPlayer) {
            DJDeferredDamageManager.triggerForParry(serverPlayer, session);
            PacketDistributor.sendToPlayer(serverPlayer,
                    new DJShieldParryWindowPayload(session.getSessionId(), event.getHand(), parryExpiresAtMs));
        }
    }

    @SubscribeEvent
    public static void onShieldUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !DJItemBehaviorManager.is(event.getItem(), DJItemBehavior.SHIELD)) {
            return;
        }
        DJModeManager.getInstance().getSession(player).ifPresent(session -> session.stopShieldUse());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBowUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !DJItemBehaviorManager.resolve(event.getItem()).isCharge()) {
            return;
        }
        if (DJNetworkGroupManager.getInstance().isCombatSuppressed(player.getUUID())) {
            event.setCanceled(true);
            return;
        }
        var session = DJModeManager.getInstance().getSession(player).filter(value -> value.isPlaying()).orElse(null);
        if (session == null) {
            return;
        }
        DJActionContext decision = DJChargeJudgmentCache.consume(player.getUUID(),
                session.getSessionId(), event.getHand(), event.getItem(),
                player.level().getGameTime());
        if (decision == null) {
            DJCraft.LOGGER.warn("Missing/expired DJ bow release judgment for {}; allowing vanilla release",
                    player.getName().getString());
            return;
        }
        if (!decision.damageAuthorized()) {
            event.setCanceled(true);
        } else {
            beginProjectileFire((ServerPlayer) player, decision);
        }
        stopForClockDesync(player, decision.stopAfterAction());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        DamageKey key = resolveDamageKey(event);
        if (key == null) {
            return;
        }
        PendingDamage pending = PENDING_DAMAGE.remove(key);
        if (pending == null || event.getEntity().level().getGameTime() > pending.expiresAtTick) {
            return;
        }
        event.setNewDamage(event.getNewDamage() * pending.damageMultiplier);
        DJ_HIT_TARGETS.put(event.getEntity().getUUID(), new ConfirmedDamage(key.attackerId, key.sessionId,
                pending.sequence, pending.beatHit, pending.hand, pending.profileId));
        if (pending.stopAfterAction && event.getSource().getEntity() instanceof Player attacker) {
            stopForClockDesync(attacker, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        DJAreaMeleeCombatService.confirmDamage(event.getEntity());
        if (!event.getEntity().level().isClientSide() && event.getNewDamage() > 0.0F
                && event.getSource().getDirectEntity() instanceof ThrownTrident trident
                && trident instanceof DJThrownTridentExtension extension
                && extension.djcraft$isDJTrident()) {
            event.getEntity().addEffect(new MobEffectInstance(
                    ModEffects.REND, DJTridentRules.REND_DURATION_TICKS,
                    DJTridentRules.rendAmplifier(ModEnchantments.level(
                            trident.level().registryAccess(), trident.getWeaponItem(),
                            ModEnchantments.RENDING))));
        }
        ConfirmedDamage confirmed = DJ_HIT_TARGETS.remove(event.getEntity().getUUID());
        if (confirmed != null) {
            event.getEntity().invulnerableTime = 0;
            boolean djTridentDamage = event.getSource().getDirectEntity() instanceof ThrownTrident trident
                    && trident instanceof DJThrownTridentExtension extension
                    && extension.djcraft$isDJTrident();
            if (confirmed.beatHit
                    && event.getEntity().level().getPlayerByUUID(confirmed.attackerId) instanceof ServerPlayer attacker) {
                DJModeManager.getInstance().getSession(attacker)
                        .filter(session -> session.getSessionId() == confirmed.sessionId)
                        .ifPresent(session -> {
                            if (djTridentDamage) {
                                session.confirmProjectileDamage(confirmed.sequence);
                            } else {
                                session.confirmComboHit(confirmed.sequence);
                            }
                        });
            }
            if (confirmed.beatHit
                    && event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level) {
                long seed = confirmed.sequence * 0x9E3779B97F4A7C15L ^ event.getEntity().getUUID().hashCode();
                PacketDistributor.sendToPlayersNear(level, null, event.getEntity().getX(), event.getEntity().getY(),
                        event.getEntity().getZ(), 64.0,
                        new DJWeaponSoundBroadcastPayload(Math.max(1L, confirmed.sequence), confirmed.attackerId,
                                confirmed.sequence, confirmed.hand,
                                DJWeaponSoundSemantic.TARGET_HIT, confirmed.profileId, BeatOutcome.HIT,
                                TargetOutcome.HIT, event.getEntity().getX(), event.getEntity().getY(),
                                event.getEntity().getZ(), seed));
            }
        }
    }

    public static void cleanupPlayer(UUID playerId) {
        PENDING_JUDGMENTS.remove(playerId);
        PENDING_DAMAGE.keySet().removeIf(key -> key.attackerId.equals(playerId));
        DJChargeJudgmentCache.cleanup(playerId);
        DJActionSourceHistory.cleanupPlayer(playerId);
        FIRING_PROJECTILES.remove(playerId);
        PROJECTILE_JUDGMENTS.values().removeIf(value -> value.attackerId.equals(playerId));
    }

    public static void cleanupExpired(long gameTime) {
        PENDING_JUDGMENTS.values().removeIf(value -> gameTime > value.expiresAtTick());
        PENDING_DAMAGE.values().removeIf(value -> gameTime > value.expiresAtTick);
        DJChargeJudgmentCache.cleanupExpired(gameTime);
        DJActionSourceHistory.cleanupExpired(gameTime);
        FIRING_PROJECTILES.values().removeIf(value -> gameTime > value.expiresAtTick);
        PROJECTILE_JUDGMENTS.values().removeIf(value -> gameTime > value.expiresAtTick);
    }

    public static void clear() {
        PENDING_JUDGMENTS.clear();
        PENDING_DAMAGE.clear();
        DJ_HIT_TARGETS.clear();
        DJChargeJudgmentCache.clear();
        DJActionSourceHistory.clear();
        FIRING_PROJECTILES.clear();
        PROJECTILE_JUDGMENTS.clear();
    }

    public static void beginProjectileFire(ServerPlayer player, DJActionContext action) {
        FIRING_PROJECTILES.put(player.getUUID(), new FiringJudgment(action.sessionId(), action.sequence(),
                action.damageMultiplier(), action.result().isHit(), action.hand(), action.soundProfileId(),
                action.expiresAtTick()));
    }

    public static void endProjectileFire(UUID playerId) {
        FIRING_PROJECTILES.remove(playerId);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)
                || !(projectile.getOwner() instanceof Player owner)) {
            return;
        }
        FiringJudgment firing = FIRING_PROJECTILES.get(owner.getUUID());
        if (firing != null && owner.level().getGameTime() <= firing.expiresAtTick) {
            PROJECTILE_JUDGMENTS.put(projectile.getUUID(),
                    new ProjectileJudgment(owner.getUUID(), firing.sessionId, firing.sequence,
                            firing.damageMultiplier, firing.beatHit, owner.level().getGameTime() + 1200L, firing.hand,
                            firing.profileId));
        }
    }

    private static DamageKey resolveDamageKey(LivingDamageEvent.Pre event) {
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            ProjectileJudgment judgment = PROJECTILE_JUDGMENTS.get(projectile.getUUID());
            if (judgment != null) {
                return new DamageKey(judgment.attackerId, event.getEntity().getUUID(), judgment.sessionId);
            }
        }
        if (event.getSource().getDirectEntity() instanceof Player attacker) {
            var session = DJModeManager.getInstance().getSession(attacker).orElse(null);
            if (session != null) {
                return new DamageKey(attacker.getUUID(), event.getEntity().getUUID(), session.getSessionId());
            }
        }
        return null;
    }

    private static Player getPlayerAttacker(LivingIncomingDamageEvent event) {
        return event.getSource().getDirectEntity() instanceof Player player ? player : null;
    }

    private static boolean tryParry(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender) || defender.level().isClientSide()
                || !isParryCandidate(defender, event.getSource(), event.getOriginalAmount())) {
            return false;
        }
        Entity attacker = event.getSource().getEntity();
        var session = DJModeManager.getInstance().getSession(defender).filter(value -> value.isPlaying()).orElse(null);
        var parry = session == null ? java.util.Optional.<DJShieldState.ParryResult>empty()
                : session.tryShieldParry();
        if (parry.isEmpty()) {
            return false;
        }

        event.setCanceled(true);
        if (event.getSource().getDirectEntity() instanceof Player directAttacker) {
            PENDING_JUDGMENTS.remove(directAttacker.getUUID());
        }
        session.confirmParry(parry.get().rewardEnergy());
        REFLECTING_DAMAGE.set(true);
        try {
            attacker.hurt(defender.damageSources().thorns(defender),
                    event.getOriginalAmount() * DJShieldRules.PARRY_DAMAGE_MULTIPLIER);
        } finally {
            REFLECTING_DAMAGE.set(false);
        }
        applyParryImpulse(event, defender, attacker);
        if (defender instanceof ServerPlayer serverDefender) {
            InteractionHand shieldHand = defender.getUsedItemHand();
            long soundSequence = Math.max(1L, defender.level().getGameTime());
            DJGameplaySoundBroadcaster.broadcast(serverDefender, soundSequence, shieldHand,
                    DJWeaponSoundSemantic.PARRY, BeatOutcome.HIT, TargetOutcome.HIT);
            PacketDistributor.sendToPlayer(serverDefender,
                    new DJParrySuccessPayload(session.getSessionId(), shieldHand));
        }
        return true;
    }

    static boolean isParryCandidate(Player defender, DamageSource source, float originalAmount) {
        Entity attacker = source.getEntity();
        return originalAmount > 0.0F && attacker != null && attacker != defender
                && DJShieldFacingRules.isFacing(defender.getViewVector(1.0F), defender.position(),
                        attacker.position());
    }

    private static void applyParryImpulse(LivingIncomingDamageEvent event, Player defender, Entity attacker) {
        Vec3 attackDirection;
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            attackDirection = projectile.getDeltaMovement();
        } else {
            attackDirection = defender.position().subtract(attacker.position());
        }

        Vec3 horizontal = new Vec3(attackDirection.x, 0.0, attackDirection.z);
        if (horizontal.lengthSqr() < 1.0E-8) {
            horizontal = defender.getLookAngle();
        }
        Vec3 reverse = new Vec3(horizontal.x, 0.0, horizontal.z).normalize()
                .scale(-DJShieldRules.PARRY_HORIZONTAL_VELOCITY);
        attacker.setDeltaMovement(reverse.x, DJShieldRules.PARRY_VERTICAL_VELOCITY, reverse.z);
        attacker.hasImpulse = true;
        attacker.hurtMarked = true;
    }

    private static void stopForClockDesync(Player player, boolean stopAfterAction) {
        if (stopAfterAction) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.djcraft.clock_desync"));
            DJModeManager.getInstance().stopSession(player,
                    otto.djgun.djcraft.network.packet.StopReason.CLOCK_DESYNC);
        }
    }

    private record DamageKey(UUID attackerId, UUID targetId, long sessionId) {
    }

    private record PendingDamage(long sequence, float damageMultiplier, boolean stopAfterAction,
            boolean beatHit, long expiresAtTick, InteractionHand hand, ResourceLocation profileId) {
    }

    private record FiringJudgment(long sessionId, long sequence, float damageMultiplier, boolean beatHit,
            InteractionHand hand, ResourceLocation profileId, long expiresAtTick) {
    }

    private record ProjectileJudgment(UUID attackerId, long sessionId, long sequence,
            float damageMultiplier, boolean beatHit, long expiresAtTick, InteractionHand hand,
            ResourceLocation profileId) {
    }

    private record ConfirmedDamage(UUID attackerId, long sessionId, long sequence, boolean beatHit,
            InteractionHand hand,
            ResourceLocation profileId) {
    }

    private record AttackSlotOverride(UUID playerId, int selectedSlot, boolean changedSlot,
            ItemStack appliedMainHand, ItemStack actionAttributes, boolean changedAttributes) {
    }
}
