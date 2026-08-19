package otto.djgun.djcraft.combat.client;

import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Local prediction and authoritative reconciliation for the short dash lock. */
@OnlyIn(Dist.CLIENT)
public final class DJClientDashMomentumState {
    private static UUID playerId;
    private static Vec3 momentum;
    private static int remainingTravelTicks;
    private static int predictedTravelTicksConsumed;
    private static boolean awaitingReconciliation;

    private DJClientDashMomentumState() {
    }

    public static void predict(LocalPlayer player, Vec3 lockedMomentum, int durationTicks) {
        playerId = player.getUUID();
        momentum = lockedMomentum;
        remainingTravelTicks = Math.max(0, durationTicks);
        predictedTravelTicksConsumed = 0;
        awaitingReconciliation = true;
        apply(player, lockedMomentum);
    }

    public static void reconcile(LocalPlayer player, Vec3 lockedMomentum, int durationTicks) {
        if (durationTicks <= 0) {
            reset();
            apply(player, lockedMomentum);
            return;
        }
        boolean matchesPrediction = player.getUUID().equals(playerId) && awaitingReconciliation;
        playerId = player.getUUID();
        momentum = lockedMomentum;
        remainingTravelTicks = matchesPrediction
                ? Math.max(0, durationTicks - predictedTravelTicksConsumed)
                : durationTicks;
        predictedTravelTicksConsumed = 0;
        awaitingReconciliation = false;
        if (remainingTravelTicks > 0) {
            apply(player, lockedMomentum);
        } else {
            playerId = null;
            momentum = null;
        }
    }

    public static Vec3 consumeForTravel(Player player) {
        if (!player.getUUID().equals(playerId) || remainingTravelTicks <= 0) {
            return null;
        }
        Vec3 lockedMomentum = momentum;
        remainingTravelTicks--;
        if (awaitingReconciliation) {
            predictedTravelTicksConsumed++;
        }
        if (remainingTravelTicks == 0 && !awaitingReconciliation) {
            playerId = null;
            momentum = null;
        }
        return lockedMomentum;
    }

    public static Vec3 addAirImpulse(Player player, Vec3 airImpulse) {
        if (!player.getUUID().equals(playerId) || remainingTravelTicks <= 0 || momentum == null) {
            apply(player, airImpulse);
            return airImpulse;
        }
        momentum = otto.djgun.djcraft.combat.DJDashMomentum.addAirImpulse(momentum, airImpulse);
        apply(player, momentum);
        return momentum;
    }

    public static void reconcileAirMovement(Player player, Vec3 authoritativeVelocity,
            int serverRemainingTravelTicks) {
        if (serverRemainingTravelTicks > 0) {
            boolean hasLocalLock = player.getUUID().equals(playerId)
                    && remainingTravelTicks > 0 && momentum != null;
            playerId = player.getUUID();
            momentum = authoritativeVelocity;
            remainingTravelTicks = hasLocalLock
                    ? Math.min(remainingTravelTicks, serverRemainingTravelTicks)
                    : serverRemainingTravelTicks;
            predictedTravelTicksConsumed = 0;
            awaitingReconciliation = false;
            apply(player, authoritativeVelocity);
            return;
        }
        reset();
        apply(player, authoritativeVelocity);
    }

    public static void reset() {
        playerId = null;
        momentum = null;
        remainingTravelTicks = 0;
        predictedTravelTicksConsumed = 0;
        awaitingReconciliation = false;
    }

    private static void apply(Player player, Vec3 lockedMomentum) {
        player.setDeltaMovement(lockedMomentum);
        player.resetFallDistance();
        player.hasImpulse = true;
    }
}
