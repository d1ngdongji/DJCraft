package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative fixed-momentum window for DJ dashes. */
public final class DJDashMomentumController {
    private static final Map<UUID, LockedMomentum> LOCKED_MOMENTA = new HashMap<>();

    private DJDashMomentumController() {
    }

    public static void activate(ServerPlayer player, Vec3 momentum, int durationTicks) {
        if (durationTicks <= 0) {
            cancel(player.getUUID());
            apply(player, momentum);
            return;
        }
        LOCKED_MOMENTA.put(player.getUUID(),
                new LockedMomentum(momentum, durationTicks));
        apply(player, momentum);
    }

    public static Vec3 consumeForTravel(ServerPlayer player) {
        LockedMomentum lock = LOCKED_MOMENTA.get(player.getUUID());
        if (lock == null) {
            return null;
        }
        if (lock.remainingTravelTicks() <= 1) {
            LOCKED_MOMENTA.remove(player.getUUID());
        } else {
            LOCKED_MOMENTA.put(player.getUUID(),
                    new LockedMomentum(lock.momentum(), lock.remainingTravelTicks() - 1));
        }
        return lock.momentum();
    }

    public static AirImpulseResult addAirImpulse(ServerPlayer player, Vec3 airImpulse) {
        LockedMomentum lock = LOCKED_MOMENTA.get(player.getUUID());
        if (lock == null) {
            apply(player, airImpulse);
            return new AirImpulseResult(airImpulse, 0);
        }
        Vec3 combined = DJDashMomentum.addAirImpulse(lock.momentum(), airImpulse);
        LOCKED_MOMENTA.put(player.getUUID(),
                new LockedMomentum(combined, lock.remainingTravelTicks()));
        apply(player, combined);
        return new AirImpulseResult(combined, lock.remainingTravelTicks());
    }

    public static void cancel(UUID playerId) {
        LOCKED_MOMENTA.remove(playerId);
    }

    public static void clear() {
        LOCKED_MOMENTA.clear();
    }

    private static void apply(ServerPlayer player, Vec3 momentum) {
        player.setDeltaMovement(momentum);
        player.resetFallDistance();
        player.hasImpulse = true;
        player.hurtMarked = true;
    }

    private record LockedMomentum(Vec3 momentum, int remainingTravelTicks) {
    }

    public record AirImpulseResult(Vec3 velocity, int remainingTravelTicks) {
    }
}
