package otto.djgun.djcraft.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.network.packet.DJDashMomentumPayload;
import otto.djgun.djcraft.session.DJModeManager;

/** Server-authoritative contact window for Flowery-enhanced dashes. */
public final class FloweryDashController {
    public static final double SPEED_MULTIPLIER = 1.5;
    public static final double REBOUND_MOMENTUM_MULTIPLIER = 0.5;
    public static final float CONTACT_DAMAGE = 9.0F;
    public static final int EFFECT_DURATION_TICKS = 20;
    private static final double CONTACT_INFLATION = 0.35;
    private static final Map<UUID, ActiveDash> ACTIVE_DASHES = new HashMap<>();

    private FloweryDashController() {
    }

    public static boolean isEquipped(Player player) {
        return ModItems.FLOWERY.get().isActiveFor(player);
    }

    public static void activate(ServerPlayer player, long sessionId, long actionSequence) {
        long expiresAtTick = player.level().getGameTime() + EFFECT_DURATION_TICKS;
        ACTIVE_DASHES.put(player.getUUID(),
                new ActiveDash(expiresAtTick, player.position(), sessionId, actionSequence));
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, ActiveDash>> iterator = ACTIVE_DASHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveDash> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ActiveDash dash = entry.getValue();
            if (player == null || !player.isAlive() || player.level().getGameTime() >= dash.expiresAtTick()) {
                iterator.remove();
                continue;
            }

            Vec3 currentPosition = player.position();
            Vec3 traveled = currentPosition.subtract(dash.lastPosition());
            AABB contactBox = player.getBoundingBox().move(traveled.scale(-1.0))
                    .expandTowards(traveled).inflate(CONTACT_INFLATION);
            LivingEntity target = player.level().getEntitiesOfClass(LivingEntity.class, contactBox,
                            entity -> entity != player && entity.isAlive() && !player.isAlliedTo(entity))
                    .stream()
                    .min(java.util.Comparator.comparingDouble(player::distanceToSqr))
                    .orElse(null);

            if (target != null && DJCombatHandler.hurtFromMovementAbility(player, target, CONTACT_DAMAGE)) {
                DJModeManager.getInstance().getSession(player)
                        .filter(session -> session.isPlaying() && session.getSessionId() == dash.sessionId())
                        .ifPresent(session -> session.confirmComboHit(dash.actionSequence()));
                Vec3 remainingMomentum = player.getDeltaMovement();
                Vec3 reboundMomentum = remainingMomentum.scale(-REBOUND_MOMENTUM_MULTIPLIER);
                DJDashMomentumController.cancel(player.getUUID());
                player.setDeltaMovement(reboundMomentum);
                player.hasImpulse = true;
                player.hurtMarked = true;
                PacketDistributor.sendToPlayer(player,
                        new DJDashMomentumPayload(reboundMomentum, 0));
                iterator.remove();
            } else {
                entry.setValue(new ActiveDash(dash.expiresAtTick(), currentPosition,
                        dash.sessionId(), dash.actionSequence()));
            }
        }
    }

    public static void cleanupPlayer(UUID playerId) {
        ACTIVE_DASHES.remove(playerId);
    }

    public static void clear() {
        ACTIVE_DASHES.clear();
    }

    private record ActiveDash(long expiresAtTick, Vec3 lastPosition, long sessionId, long actionSequence) {
    }
}
