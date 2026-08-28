package otto.djgun.djcraft.mixin;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.combat.DJRelativeProjectileCollision;
import otto.djgun.djcraft.combat.DJTridentRules;
import otto.djgun.djcraft.combat.access.AbstractArrowAccess;
import otto.djgun.djcraft.combat.access.DJThrownTridentExtension;
import otto.djgun.djcraft.init.ModSounds;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin implements DJThrownTridentExtension {
    @Shadow
    @Final
    private static EntityDataAccessor<Byte> ID_LOYALTY;

    @Shadow
    private boolean dealtDamage;

    @Shadow
    protected abstract void onHitEntity(EntityHitResult result);

    @Unique
    private static final EntityDataAccessor<Boolean> DJ_FLIGHT = SynchedEntityData.defineId(
            ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> DJ_REDIRECT_ENABLED = SynchedEntityData.defineId(
            ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> DJ_RETURNING = SynchedEntityData.defineId(
            ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final EntityDataAccessor<Boolean> DJ_FINISHED = SynchedEntityData.defineId(
            ThrownTrident.class, EntityDataSerializers.BOOLEAN);
    @Unique
    private static final String DJ_FLIGHT_TAG = "DJCraftFlight";
    @Unique
    private static final String DJ_REDIRECT_ENABLED_TAG = "DJCraftRedirectEnabled";
    @Unique
    private static final String DJ_RETURN_GATE_TAG = "DJCraftReturnGate";
    @Unique
    private static final String DJ_RETURNING_TAG = "DJCraftReturning";
    @Unique
    private static final String DJ_RETURN_AT_TAG = "DJCraftReturnAtTimelineMs";
    @Unique
    private static final String DJ_SESSION_ID_TAG = "DJCraftSessionId";
    @Unique
    private static final String DJ_LOYALTY_TAG = "DJCraftStoredLoyalty";
    @Unique
    private static final String DJ_RETURN_DAMAGE_TAG = "DJCraftReturnDamageAvailable";
    @Unique
    private static final String DJ_RETURN_DAMAGE_AT_TAG = "DJCraftReturnDamageAtGameTime";

    @Unique
    private boolean djcraft$returnGate;
    @Unique
    private long djcraft$returnAtTimelineMs;
    @Unique
    private long djcraft$sessionId = -1L;
    @Unique
    private byte djcraft$storedLoyalty;
    @Unique
    private Vec3 djcraft$preHitVelocity;
    @Unique
    private boolean djcraft$expandedCollisionBox;
    @Unique
    private Vec3 djcraft$preTickPosition;
    @Unique
    private boolean djcraft$returnDamageAvailable;
    @Unique
    private long djcraft$returnDamageAtGameTime;
    @Unique
    private boolean djcraft$returnHitInProgress;

    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions base = EntityType.TRIDENT.getDimensions();
        if (!djcraft$expandedCollisionBox) {
            return base;
        }
        return EntityDimensions.fixed(
                base.width() * DJTridentRules.COLLISION_BOX_SCALE,
                base.height() * DJTridentRules.COLLISION_BOX_SCALE);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void djcraft$defineFlightData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(DJ_FLIGHT, false);
        builder.define(DJ_REDIRECT_ENABLED, false);
        builder.define(DJ_RETURNING, false);
        builder.define(DJ_FINISHED, false);
    }

    @Override
    public void djcraft$configureFlight(long sessionId, long returnAtTimelineMs) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        ((AbstractArrowAccess) this).djcraft$setPierceLevel((byte) 0);
        self.getEntityData().set(DJ_FLIGHT, true);
        self.getEntityData().set(DJ_REDIRECT_ENABLED, true);
        self.getEntityData().set(DJ_RETURNING, false);
        self.getEntityData().set(DJ_FINISHED, false);
        djcraft$storedLoyalty = self.getEntityData().get(ID_LOYALTY);
        self.setNoGravity(DJTridentRules.shouldUseNoGravity(djcraft$storedLoyalty));
        self.setGlowingTag(true);
        djcraft$expandCollisionBox();
        djcraft$sessionId = sessionId;
        djcraft$returnAtTimelineMs = returnAtTimelineMs;
        djcraft$armReturnGate();
    }

    @Override
    public boolean djcraft$isDJTrident() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        return self.getEntityData().get(DJ_FLIGHT)
                && !self.getEntityData().get(DJ_FINISHED);
    }

    @Override
    public boolean djcraft$canBeRedirected() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        return djcraft$isDJTrident()
                && self.getEntityData().get(DJ_REDIRECT_ENABLED)
                && self.getEntityData().get(DJ_RETURNING)
                && self.getEntityData().get(ID_LOYALTY) > 0
                && (self.level().isClientSide() || self.isNoPhysics());
    }

    @Override
    public boolean djcraft$tryRedirect(Entity attacker) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!(attacker instanceof Player) || !djcraft$canBeRedirected()) {
            return false;
        }
        Vec3 velocity = DJTridentRules.redirectVelocity(attacker.getLookAngle());
        if (self.level().isClientSide()) {
            djcraft$applyRedirectKinematics(velocity, true);
            return true;
        }

        DJSession session = djcraft$currentOwnerSession();
        if (session == null) {
            self.getEntityData().set(DJ_REDIRECT_ENABLED, false);
            return false;
        }

        djcraft$applyRedirectKinematics(velocity, false);
        djcraft$returnAtTimelineMs = session.getCompletionTimeMs(DJTridentRules.RETURN_DELAY_BEATS);
        djcraft$armReturnGate();

        if (self.level() instanceof ServerLevel level) {
            level.playSound(null, self.getX(), self.getY(), self.getZ(),
                    ModSounds.TRIDENT_REDIRECT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return true;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void djcraft$tickFlightState(CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!djcraft$isDJFlightEntity()) {
            return;
        }
        if (self.getEntityData().get(DJ_FINISHED)) {
            djcraft$applyFinishedFlightState();
            return;
        }
        if (!self.level().isClientSide() && djcraft$storedLoyalty <= 0 && dealtDamage) {
            djcraft$finishNonReturningFlight();
            return;
        }
        djcraft$preTickPosition = self.position();
        if (!djcraft$expandedCollisionBox) {
            djcraft$expandCollisionBox();
        }
        if (!self.level().isClientSide()) {
            self.setNoGravity(DJTridentRules.shouldUseNoGravity(djcraft$storedLoyalty));
        }
        if (!self.level().isClientSide()) {
            self.setGlowingTag(true);
        }
        if (!self.isNoPhysics()) {
            self.clientSideReturnTridentTickCount = 0;
        }
        if (self.level().isClientSide()) {
            return;
        }

        DJSession session = djcraft$currentOwnerSession();
        if (session == null) {
            self.getEntityData().set(DJ_REDIRECT_ENABLED, false);
            if (djcraft$returnGate) {
                djcraft$releaseReturnGate();
            } else if (djcraft$storedLoyalty <= 0) {
                djcraft$finishNonReturningFlight();
            }
            return;
        }
        if (djcraft$returnGate && session.getCurrentTimeMs() >= djcraft$returnAtTimelineMs) {
            djcraft$releaseReturnGate();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void djcraft$attackDuringReturnAndLimitVelocity(CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!djcraft$isDJTrident() || !self.getEntityData().get(DJ_RETURNING)) {
            return;
        }

        self.setDeltaMovement(DJTridentRules.limitReturnVelocity(self.getDeltaMovement()));
        if (!(self.level() instanceof ServerLevel level) || self.isRemoved()
                || !DJTridentRules.canApplyReturnDamage(djcraft$returnDamageAvailable,
                        level.getGameTime(), djcraft$returnDamageAtGameTime)
                || djcraft$preTickPosition == null || djcraft$currentOwnerSession() == null) {
            return;
        }

        Vec3 end = self.position();
        if (!DJTridentRules.isSafeCollisionTrace(djcraft$preTickPosition, end)) {
            return;
        }
        EntityHitResult hit = DJRelativeProjectileCollision.findFirstEntity(
                self.level(), self, djcraft$preTickPosition, end, this::djcraft$canHitReturningTarget);
        if (hit != null) {
            djcraft$returnDamageAvailable = false;
            onHitEntity(hit);
        }
    }

    @Inject(method = "isFoil", at = @At("HEAD"), cancellable = true)
    private void djcraft$skipRedundantFoilPass(CallbackInfoReturnable<Boolean> cir) {
        if (djcraft$isDJTrident()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void djcraft$requireCloseLoyaltyPickup(Player player, CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (djcraft$isDJTrident()
                && self.getEntityData().get(DJ_RETURNING)
                && self.getOwner() == player
                && !DJTridentRules.canCompleteLoyaltyReturn(self.position(), player.getEyePosition())) {
            ci.cancel();
        }
    }

    @Inject(method = "tickDespawn", at = @At("HEAD"), cancellable = true)
    private void djcraft$preserveWaitingLoyaltyTrident(CallbackInfo ci) {
        if (djcraft$isDJTrident() && djcraft$returnGate && djcraft$storedLoyalty > 0) {
            ci.cancel();
        }
    }

    @Inject(method = "findHitEntity", at = @At("HEAD"), cancellable = true)
    private void djcraft$findDJEntity(Vec3 start, Vec3 end,
            CallbackInfoReturnable<EntityHitResult> cir) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!djcraft$isDJTrident()) {
            return;
        }
        if (DJTridentRules.shouldSkipEntityCollision(
                self.isNoPhysics(), self.getEntityData().get(DJ_RETURNING), dealtDamage)) {
            cir.setReturnValue(null);
            return;
        }
        if (!DJTridentRules.isSafeCollisionTrace(start, end)) {
            self.setDeltaMovement(Vec3.ZERO);
            cir.setReturnValue(null);
            return;
        }
        Entity owner = self.getOwner();
        cir.setReturnValue(DJRelativeProjectileCollision.findFirstEntity(self.level(), self, start, end,
                entity -> entity != owner && ((AbstractArrowAccess) self).djcraft$canHitEntity(entity)));
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void djcraft$rememberVelocity(EntityHitResult hit, CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        djcraft$returnHitInProgress = djcraft$isDJTrident()
                && self.getEntityData().get(DJ_RETURNING);
        if (djcraft$returnHitInProgress) {
            djcraft$preHitVelocity = self.getDeltaMovement();
        }
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void djcraft$continueAfterHit(EntityHitResult hit, CallbackInfo ci) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (djcraft$returnHitInProgress && djcraft$preHitVelocity != null) {
            self.setDeltaMovement(djcraft$preHitVelocity);
            dealtDamage = true;
            djcraft$preHitVelocity = null;
        } else if (djcraft$isDJTrident()) {
            djcraft$beginReturnAfterCollision();
        }
        djcraft$returnHitInProgress = false;
    }

    @Inject(method = "hitBlockEnchantmentEffects", at = @At("TAIL"))
    private void djcraft$returnAfterBlockHit(ServerLevel level, BlockHitResult hitResult,
            ItemStack stack, CallbackInfo ci) {
        djcraft$beginReturnAfterCollision();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void djcraft$saveFlightState(CompoundTag tag, CallbackInfo ci) {
        if (!djcraft$isDJTrident()) {
            return;
        }
        ThrownTrident self = (ThrownTrident) (Object) this;
        tag.putBoolean(DJ_FLIGHT_TAG, true);
        tag.putBoolean(DJ_REDIRECT_ENABLED_TAG, self.getEntityData().get(DJ_REDIRECT_ENABLED));
        tag.putBoolean(DJ_RETURN_GATE_TAG, djcraft$returnGate);
        tag.putBoolean(DJ_RETURNING_TAG, self.getEntityData().get(DJ_RETURNING));
        tag.putLong(DJ_RETURN_AT_TAG, djcraft$returnAtTimelineMs);
        tag.putLong(DJ_SESSION_ID_TAG, djcraft$sessionId);
        tag.putByte(DJ_LOYALTY_TAG, djcraft$storedLoyalty);
        tag.putBoolean(DJ_RETURN_DAMAGE_TAG, djcraft$returnDamageAvailable);
        tag.putLong(DJ_RETURN_DAMAGE_AT_TAG, djcraft$returnDamageAtGameTime);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void djcraft$loadFlightState(CompoundTag tag, CallbackInfo ci) {
        if (!tag.getBoolean(DJ_FLIGHT_TAG)) {
            return;
        }
        ThrownTrident self = (ThrownTrident) (Object) this;
        self.getEntityData().set(DJ_FLIGHT, true);
        self.getEntityData().set(DJ_REDIRECT_ENABLED, tag.getBoolean(DJ_REDIRECT_ENABLED_TAG));
        djcraft$returnGate = tag.getBoolean(DJ_RETURN_GATE_TAG);
        self.getEntityData().set(DJ_RETURNING, tag.getBoolean(DJ_RETURNING_TAG));
        djcraft$returnAtTimelineMs = tag.getLong(DJ_RETURN_AT_TAG);
        djcraft$sessionId = tag.getLong(DJ_SESSION_ID_TAG);
        djcraft$storedLoyalty = tag.getByte(DJ_LOYALTY_TAG);
        djcraft$returnDamageAvailable = tag.getBoolean(DJ_RETURN_DAMAGE_TAG);
        djcraft$returnDamageAtGameTime = tag.getLong(DJ_RETURN_DAMAGE_AT_TAG);
        self.setNoGravity(DJTridentRules.shouldUseNoGravity(djcraft$storedLoyalty));
        self.setGlowingTag(true);
        djcraft$expandCollisionBox();
        if (djcraft$returnGate && djcraft$storedLoyalty > 0) {
            self.getEntityData().set(ID_LOYALTY, (byte) 0);
        }
    }

    @Unique
    private void djcraft$armReturnGate() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        djcraft$returnGate = djcraft$storedLoyalty > 0;
        if (djcraft$returnGate) {
            self.getEntityData().set(ID_LOYALTY, (byte) 0);
        }
    }

    @Unique
    private void djcraft$expandCollisionBox() {
        if (djcraft$expandedCollisionBox) {
            return;
        }
        djcraft$expandedCollisionBox = true;
        ((ThrownTrident) (Object) this).refreshDimensions();
    }

    @Unique
    private boolean djcraft$canHitReturningTarget(Entity target) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        Entity owner = self.getOwner();
        if (!(target instanceof LivingEntity) || target == owner
                || !((AbstractArrowAccess) self).djcraft$canHitEntity(target)) {
            return false;
        }
        if (owner != null && owner.isAlliedTo(target)) {
            return false;
        }
        return !(target instanceof Player playerTarget)
                || !(owner instanceof Player playerOwner)
                || playerOwner.canHarmPlayer(playerTarget);
    }

    @Unique
    private void djcraft$releaseReturnGate() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        djcraft$returnGate = false;
        self.getEntityData().set(DJ_RETURNING, true);
        djcraft$returnDamageAvailable = true;
        djcraft$returnDamageAtGameTime = self.level().getGameTime() + 1L;
        self.getEntityData().set(ID_LOYALTY, djcraft$storedLoyalty);
        if (djcraft$storedLoyalty > 0) {
            dealtDamage = true;
        }
    }

    @Unique
    private void djcraft$beginReturnAfterCollision() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!djcraft$isDJTrident() || self.getEntityData().get(DJ_RETURNING)) {
            return;
        }
        if (djcraft$storedLoyalty <= 0) {
            if (!self.level().isClientSide()) {
                djcraft$finishNonReturningFlight();
            }
            return;
        }
        if (djcraft$currentOwnerSession() == null) {
            return;
        }
        djcraft$releaseReturnGate();
    }

    @Unique
    private void djcraft$finishNonReturningFlight() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        self.getEntityData().set(DJ_FINISHED, true);
        self.getEntityData().set(DJ_REDIRECT_ENABLED, false);
        self.getEntityData().set(DJ_RETURNING, false);
        djcraft$applyFinishedFlightState();
    }

    @Unique
    private void djcraft$applyFinishedFlightState() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        self.setNoPhysics(false);
        self.setNoGravity(false);
        self.setGlowingTag(false);
        djcraft$returnGate = false;
        djcraft$returnDamageAvailable = false;
        djcraft$preTickPosition = null;
        if (djcraft$expandedCollisionBox) {
            djcraft$expandedCollisionBox = false;
            self.refreshDimensions();
        }
    }

    @Unique
    private boolean djcraft$isDJFlightEntity() {
        return ((ThrownTrident) (Object) this).getEntityData().get(DJ_FLIGHT);
    }

    @Unique
    private void djcraft$applyRedirectKinematics(Vec3 velocity, boolean clientPrediction) {
        ThrownTrident self = (ThrownTrident) (Object) this;
        dealtDamage = false;
        self.getEntityData().set(DJ_RETURNING, false);
        self.setNoPhysics(false);
        self.setNoGravity(true);
        self.clientSideReturnTridentTickCount = 0;
        ((AbstractArrowAccess) this).djcraft$resetFlightState();
        ((AbstractArrowAccess) this).djcraft$setPierceLevel((byte) 0);
        djcraft$preHitVelocity = null;
        djcraft$returnDamageAvailable = false;
        djcraft$returnDamageAtGameTime = 0L;
        self.setDeltaMovement(Vec3.ZERO);
        self.setDeltaMovement(velocity);
        self.hasImpulse = true;
        self.hurtMarked = true;

        if (clientPrediction) {
            self.getEntityData().set(ID_LOYALTY, (byte) 0);
            double horizontal = velocity.horizontalDistance();
            self.setYRot((float) (Mth.atan2(velocity.x, velocity.z) * 180.0F / Math.PI));
            self.setXRot((float) (Mth.atan2(velocity.y, horizontal) * 180.0F / Math.PI));
            self.setOldPosAndRot();
        }
    }

    @Nullable
    @Unique
    private DJSession djcraft$currentOwnerSession() {
        ThrownTrident self = (ThrownTrident) (Object) this;
        if (!(self.getOwner() instanceof Player owner) || !owner.isAlive() || owner.isSpectator()) {
            return null;
        }
        return DJModeManager.getInstance().getSession(owner)
                .filter(DJSession::isPlaying)
                .filter(session -> session.getSessionId() == djcraft$sessionId)
                .orElse(null);
    }
}
