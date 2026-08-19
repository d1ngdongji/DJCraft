package otto.djgun.djcraft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.sound.DJWeaponSoundIdentityRegistry;

/** Immutable action-time state shared by melee, release, trigger, and projectile stages. */
public record DJActionContext(
        long sessionId,
        long sequence,
        InteractionHand hand,
        DJActionSource source,
        ItemStack stackSnapshot,
        HitResult result,
        boolean damageAuthorized,
        boolean stopAfterAction,
        long expiresAtTick,
        float damageMultiplier,
        ResourceLocation soundProfileId) {
    public static final long DEFAULT_TTL_TICKS = 4L;

    public DJActionContext {
        stackSnapshot = stackSnapshot.copy();
    }

    public static DJActionContext create(ServerPlayer player, long sessionId, long sequence,
            InteractionHand hand, DJActionSource source, ItemStack sourceStack, HitResult result,
            boolean stopAfterAction, boolean damageAuthorized, int offBeatDamagePercent) {
        ItemStack snapshot = sourceStack.copy();
        return new DJActionContext(sessionId, sequence, hand, source, snapshot, result, damageAuthorized,
                stopAfterAction,
                player.level().getGameTime() + DEFAULT_TTL_TICKS,
                damageAuthorized
                        ? DJAttackExecutionRules.damageMultiplier(result, snapshot, offBeatDamagePercent)
                        : 0.0F,
                DJWeaponSoundIdentityRegistry.resolve(snapshot));
    }

    public boolean isCurrent(ServerPlayer player, long expectedSessionId) {
        return sessionId == expectedSessionId && player.level().getGameTime() <= expiresAtTick;
    }

    public ItemStack resolveLiveStack(ServerPlayer player) {
        return source.resolve(player, hand);
    }
}
