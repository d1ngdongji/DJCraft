package otto.djgun.djcraft.combat;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.network.packet.DJRayEffectPayload;
import otto.djgun.djcraft.cybergrind.CyberGrindManager;
import otto.djgun.djcraft.init.ModEnchantments;
import otto.djgun.djcraft.item.AssaultCrossbowItem;

/** Authoritative block-clipped ray tracing and damage for trigger-family ray weapons. */
public final class DJRayWeaponCombatService {
    private DJRayWeaponCombatService() {
    }

    public static Result fire(ServerPlayer player, DJActionContext action, DJRayWeaponProfile profile) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 direction = resolveDirection(player, origin, look, profile);
        Trace trace = trace(player, origin, direction, profile.range());
        Vec3 end = trace.end();
        PotionContents potionArrow = action.stackSnapshot().getItem() instanceof AssaultCrossbowItem
                ? AssaultCrossbowItem.consumeTippedArrow(player)
                : PotionContents.EMPTY;

        List<Contact> contacts = collectContacts(player, origin, end, profile.explosion() != null);
        LivingEntity directTarget = null;
        if (!profile.pierceEntities() && !contacts.isEmpty()) {
            Contact firstContact = contacts.getFirst();
            directTarget = firstContact.target();
            end = firstContact.position();
            contacts = List.of(firstContact);
        }

        DJActionContext damageAction = new DJActionContext(action.sessionId(), action.sequence(), action.hand(),
                action.source(), action.stackSnapshot(), action.result(), action.damageAuthorized(), false,
                action.expiresAtTick(),
                action.damageMultiplier(), action.soundProfileId());
        double enchantmentDamage = rayEnchantmentDamage(ModEnchantments.level(
                player.level().registryAccess(), action.stackSnapshot(), ModEnchantments.RAY_OVERCHARGE));
        double directDamage = profile.baseDamage() > 0.0 ? profile.baseDamage() + enchantmentDamage : 0.0;
        if (directDamage > 0.0) {
            for (Contact contact : contacts) {
                boolean damaged = hurt(player, damageAction, contact.target(),
                        player.damageSources().playerAttack(player), directDamage);
                if (damaged && potionArrow.hasEffects()) {
                    AssaultCrossbowItem.applyPotionEffects(player, contact.target(), potionArrow);
                }
            }
        }

        double shockwaveRadius = 0.0;
        DJRayExplosionProfile explosion = profile.explosion();
        boolean hasEndpoint = directTarget != null || trace.hitBlock() || (explosion != null
                && explosion.explodeAtMaxRange());
        if (explosion != null && hasEndpoint) {
            boolean airborne = directTarget != null && !directTarget.onGround();
            shockwaveRadius = airborne ? explosion.airborneRadius() : explosion.radius();
            double damage = (airborne ? explosion.airborneDamage() : explosion.damage()) + enchantmentDamage;
            applyExplosion(player, damageAction, end, shockwaveRadius, damage);
        }

        damageWeapon(player, action);
        List<Vec3> hitPositions = contacts.stream().map(Contact::position).toList();
        List<Vec3> visualContacts = hitPositions.stream().limit(DJRayEffectPayload.MAX_CONTACTS).toList();
        PacketDistributor.sendToPlayersNear(player.serverLevel(), null,
                player.getX(), player.getY(), player.getZ(), profile.range() + 32.0,
                new DJRayEffectPayload(action.sequence(), player.getUUID(), action.hand(),
                        profile.effect(), origin, end, visualContacts, shockwaveRadius));
        return new Result(origin, end, hitPositions, shockwaveRadius);
    }

    private static Vec3 resolveDirection(ServerPlayer player, Vec3 origin, Vec3 look,
            DJRayWeaponProfile profile) {
        boolean ignoreVanillaPvp = profile.explosion() != null;
        Trace directTrace = trace(player, origin, look, profile.range());
        if (!collectContacts(player, origin, directTrace.end(), ignoreVanillaPvp).isEmpty()) {
            return look;
        }
        double maximumPercent = Math.max(profile.horizontalAimAssistPercent(),
                profile.verticalAimAssistPercent());
        if (maximumPercent <= 0.0) {
            return look;
        }
        double inflation = profile.range() * maximumPercent / 100.0 + 1.0;
        AABB searchBounds = new AABB(origin, origin.add(look.scale(profile.range()))).inflate(inflation);
        return player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, searchBounds,
                        target -> isLegalTarget(player, target, ignoreVanillaPvp)
                        && DJRaycastMath.isWithinAimAssist(origin, look, target.getBoundingBox(), profile.range(),
                                profile.horizontalAimAssistPercent(), profile.verticalAimAssistPercent())
                        && isVisibleAlongAssistedRay(player, origin, target, profile.range()))
                .stream()
                .min(Comparator.comparingDouble((LivingEntity target) -> angularDistance(
                        look, target.getBoundingBox().getCenter().subtract(origin)))
                        .thenComparingDouble(target -> origin.distanceToSqr(target.getBoundingBox().getCenter()))
                        .thenComparingInt(LivingEntity::getId))
                .map(target -> target.getBoundingBox().getCenter().subtract(origin).normalize())
                .orElse(look);
    }

    private static boolean isVisibleAlongAssistedRay(ServerPlayer player, Vec3 origin,
            LivingEntity target, double range) {
        Vec3 direction = target.getBoundingBox().getCenter().subtract(origin).normalize();
        Vec3 end = trace(player, origin, direction, range).end();
        return DJRaycastMath.intersectionFraction(origin, end, target.getBoundingBox()).isPresent();
    }

    private static double angularDistance(Vec3 look, Vec3 direction) {
        Vec3 normalized = direction.normalize();
        return Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(normalized))));
    }

    private static Trace trace(ServerPlayer player, Vec3 origin, Vec3 direction, double range) {
        Vec3 maximumEnd = origin.add(direction.scale(range));
        var blockHit = player.serverLevel().clip(new ClipContext(origin, maximumEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                ? maximumEnd : blockHit.getLocation();
        return new Trace(end, blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS);
    }

    private static List<Contact> collectContacts(ServerPlayer player, Vec3 origin, Vec3 end,
            boolean ignoreVanillaPvp) {
        AABB searchBounds = new AABB(origin, end).inflate(1.0);
        return player.serverLevel()
                .getEntitiesOfClass(LivingEntity.class, searchBounds,
                        target -> isLegalTarget(player, target, ignoreVanillaPvp))
                .stream()
                .map(target -> contact(origin, end, target))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(Contact::fraction)
                        .thenComparingInt(contact -> contact.target().getId()))
                .toList();
    }

    private static Contact contact(Vec3 origin, Vec3 end, LivingEntity target) {
        OptionalDouble fraction = DJRaycastMath.intersectionFraction(origin, end, target.getBoundingBox());
        return fraction.isEmpty() ? null
                : new Contact(target, fraction.getAsDouble(), origin.lerp(end, fraction.getAsDouble()));
    }

    private static boolean isLegalTarget(ServerPlayer player, LivingEntity target,
            boolean ignoreVanillaPvp) {
        if (target == player || !target.isAlive() || !target.isAttackable()
                || target.skipAttackInteraction(player)
                || !player.serverLevel().getWorldBorder().isWithinBounds(target.blockPosition())) {
            return false;
        }
        if (target instanceof Player other) {
            return isEligiblePlayerTarget(other.isSpectator(), other.isCreative(),
                    player.isAlliedTo(other), CyberGrindManager.getInstance().sameActiveRun(
                            player.getUUID(), other.getUUID()), ignoreVanillaPvp,
                    player.canHarmPlayer(other));
        }
        return !player.isAlliedTo(target);
    }

    private static void applyExplosion(ServerPlayer player, DJActionContext action, Vec3 center,
            double radius, double damage) {
        AABB bounds = new AABB(center, center).inflate(radius);
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, bounds,
                candidate -> isLegalTarget(player, candidate, true))) {
            Vec3 nearest = closestPoint(target.getBoundingBox(), center);
            double distance = nearest.distanceTo(center);
            if (distance > radius) {
                continue;
            }
            double exposure = Explosion.getSeenPercent(center, target);
            double strength = explosionStrength(distance, radius, exposure);
            if (strength <= 0.0) {
                continue;
            }
            if (damage > 0.0) {
                hurt(player, action, target, explosionDamageSource(player, target),
                        damage * strength);
            }
            Vec3 push = target.getBoundingBox().getCenter().subtract(center);
            if (push.lengthSqr() < 1.0E-8) {
                push = new Vec3(0.0, 1.0, 0.0);
            }
            push = push.normalize().scale(Math.min(1.0, strength));
            target.push(push.x, push.y, push.z);
        }
        player.serverLevel().playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0F,
                0.9F + player.getRandom().nextFloat() * 0.2F);
    }

    private static boolean hurt(ServerPlayer player, DJActionContext action, LivingEntity target,
            DamageSource source, double damage) {
        DJCombatHandler.receivePendingJudgment(player, action);
        try {
            return target.hurt(source, (float) damage);
        } finally {
            DJCombatHandler.discardPendingJudgment(player.getUUID());
        }
    }

    private static DamageSource explosionDamageSource(ServerPlayer player, LivingEntity target) {
        if (shouldBypassVanillaPvp(target instanceof Player, player.server.isPvpAllowed())) {
            // ServerPlayer#hurt rejects any source whose causing entity is a player when pvp=false.
            // Keeping the shooter as the direct entity preserves DJ judgment attribution while the
            // explicit target filter above remains authoritative for allies and protected players.
            return player.damageSources().explosion(player, null);
        }
        return player.damageSources().explosion(player, player);
    }

    static boolean isEligiblePlayerTarget(boolean spectator, boolean creative,
            boolean allied, boolean sameCyberGrindRun, boolean ignoreVanillaPvp,
            boolean canHarmPlayer) {
        return !spectator && !creative && !allied && !sameCyberGrindRun
                && (ignoreVanillaPvp || canHarmPlayer);
    }

    static boolean shouldBypassVanillaPvp(boolean playerTarget, boolean serverPvpAllowed) {
        return playerTarget && !serverPvpAllowed;
    }

    static double explosionStrength(double distance, double radius, double exposure) {
        if (!Double.isFinite(distance) || !Double.isFinite(radius) || !Double.isFinite(exposure)
                || radius <= 0.0 || distance >= radius || exposure <= 0.0) {
            return 0.0;
        }
        return Math.clamp((1.0 - Math.max(0.0, distance) / radius) * exposure, 0.0, 1.0);
    }

    static double rayEnchantmentDamage(int level) {
        return Math.max(0, level) * 2.0;
    }

    static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Math.clamp(point.x, box.minX, box.maxX),
                Math.clamp(point.y, box.minY, box.maxY),
                Math.clamp(point.z, box.minZ, box.maxZ));
    }

    private static void damageWeapon(ServerPlayer player, DJActionContext action) {
        if (player.hasInfiniteMaterials()) {
            return;
        }
        var stack = action.resolveLiveStack(player);
        if (stack.isEmpty()) {
            return;
        }
        stack.hurtAndBreak(1, player,
                action.hand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    private record Contact(LivingEntity target, double fraction, Vec3 position) {
    }

    private record Trace(Vec3 end, boolean hitBlock) {
    }

    public record Result(Vec3 origin, Vec3 end, List<Vec3> contacts, double shockwaveRadius) {
        public Result {
            contacts = List.copyOf(contacts);
        }
    }
}
