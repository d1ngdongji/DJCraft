package otto.djgun.djcraft.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.combat.DJMaceThrowRules;
import otto.djgun.djcraft.init.ModEntities;

public final class DJThrownMace extends ThrowableItemProjectile {
    public DJThrownMace(EntityType<? extends DJThrownMace> entityType, Level level) {
        super(entityType, level);
    }

    public DJThrownMace(LivingEntity owner, Level level) {
        super(ModEntities.THROWN_MACE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.MACE;
    }

    @Override
    protected double getDefaultGravity() {
        return DJMaceThrowRules.GRAVITY;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return target != getOwner() && target instanceof LivingEntity && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel serverLevel) || !(result.getEntity() instanceof LivingEntity target)) {
            return;
        }
        Entity owner = getOwner();
        if (target.hurt(damageSources().thrown(this, owner), DJMaceThrowRules.BASE_DAMAGE)) {
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(movement.x, movement.y + DJMaceThrowRules.VERTICAL_KNOCKBACK, movement.z);
            target.hurtMarked = true;
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount >= DJMaceThrowRules.MAX_LIFETIME_TICKS) {
            discard();
        }
    }
}
