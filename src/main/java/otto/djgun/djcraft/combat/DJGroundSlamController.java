package otto.djgun.djcraft.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import otto.djgun.djcraft.init.ModSounds;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJSession;

/** Server authority for a ground slam from activation through landing. */
public final class DJGroundSlamController {
    private static final Map<UUID, ActiveSlam> ACTIVE = new HashMap<>();

    private DJGroundSlamController() {
    }

    public static boolean activate(ServerPlayer player, DJSession session, long actionSequence,
            boolean beatHit) {
        if (ACTIVE.containsKey(player.getUUID())) {
            return false;
        }
        ACTIVE.put(player.getUUID(), new ActiveSlam(session.getSessionId(), actionSequence,
                beatHit, player.getY(), player.getY()));
        player.playNotifySound(ModSounds.GROUND_SLAM_WHOOSH.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static void tick(MinecraftServer server) {
        List<ReadySlam> ready = null;
        Iterator<Map.Entry<UUID, ActiveSlam>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveSlam> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ActiveSlam slam = entry.getValue();
            DJSession session = player == null ? null
                    : DJModeManager.getInstance().getSession(player).orElse(null);
            if (player == null || !player.isAlive() || session == null || !session.isPlaying()
                    || session.getSessionId() != slam.sessionId()) {
                if (player != null) {
                    stopWhoosh(player);
                }
                iterator.remove();
                continue;
            }

            slam = slam.withLowestY(Math.min(slam.lowestY(), player.getY()));
            entry.setValue(slam);
            Optional<BlockPos> supportingBlock = player.mainSupportingBlockPos;
            if (!player.onGround() || supportingBlock.isEmpty()) {
                continue;
            }

            iterator.remove();
            if (ready == null) {
                ready = new ArrayList<>();
            }
            ready.add(new ReadySlam(player, session, slam, supportingBlock.get()));
        }

        if (ready == null) {
            return;
        }
        for (ReadySlam landing : ready) {
            ServerPlayer player = landing.player();
            DJSession currentSession = DJModeManager.getInstance().getSession(player).orElse(null);
            if (!player.isAlive() || currentSession != landing.session() || !currentSession.isPlaying()) {
                stopWhoosh(player);
                continue;
            }
            finish(player, currentSession, landing.slam(), landing.supportingBlock());
        }
    }

    private static void finish(ServerPlayer player, DJSession session, ActiveSlam slam, BlockPos supportingBlock) {
        stopWhoosh(player);
        player.serverLevel().playSound(null, player, ModSounds.GROUND_SLAM_LAND.value(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        double fallHeight = Math.max(0.0, slam.startY() - slam.lowestY());
        if (!slam.beatHit() || fallHeight < DJMovementAbilityRules.GROUND_SLAM_MIN_EFFECT_FALL_DISTANCE) {
            return;
        }

        player.serverLevel().playSound(null, player, ModSounds.GROUND_SLAM_IMPACT.value(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        Vec3 center = new Vec3(player.getX(), player.getBoundingBox().minY, player.getZ());
        spawnImpactParticles(player, supportingBlock, center);
        float damage = DJMovementAbilityRules.groundSlamDamage(fallHeight);
        double radius = DJMovementAbilityRules.GROUND_SLAM_RADIUS;
        AABB searchBounds = new AABB(center, center).inflate(
                radius, DJMovementAbilityRules.GROUND_SLAM_VERTICAL_RANGE, radius);
        boolean damagedAny = false;
        for (LivingEntity target : player.serverLevel().getEntitiesOfClass(LivingEntity.class, searchBounds,
                target -> target != player && target.isAlive()
                        && DJMovementAbilityRules.isWithinGroundSlamRange(
                                center.x, center.y, center.z,
                                target.getX(), target.getY(), target.getZ()))) {
            if (!DJCombatHandler.hurtFromMovementAbility(player, target, damage)) {
                continue;
            }
            Vec3 movement = target.getDeltaMovement();
            target.setDeltaMovement(movement.x, Math.max(movement.y, DJMovementAbilityRules.GROUND_SLAM_LAUNCH_SPEED),
                    movement.z);
            target.hasImpulse = true;
            target.hurtMarked = true;
            damagedAny = true;
        }
        if (damagedAny) {
            session.confirmComboHit(slam.actionSequence());
        }
    }

    private static void spawnImpactParticles(ServerPlayer player, BlockPos supportingBlock, Vec3 impactCenter) {
        var level = player.serverLevel();
        BlockState groundState = level.getBlockState(supportingBlock);
        double spread = DJMovementAbilityRules.GROUND_SLAM_RADIUS * 0.55;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                impactCenter.x, impactCenter.y + 0.1, impactCenter.z,
                64, spread, 0.2, spread, 0.25);
    }

    public static void cleanupPlayer(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) {
            stopWhoosh(player);
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private static void stopWhoosh(ServerPlayer player) {
        player.connection.send(new ClientboundStopSoundPacket(
                ModSounds.GROUND_SLAM_WHOOSH.unwrapKey().orElseThrow().location(), SoundSource.PLAYERS));
    }

    private record ActiveSlam(long sessionId, long actionSequence, boolean beatHit,
            double startY, double lowestY) {
        ActiveSlam withLowestY(double value) {
            return value == lowestY ? this : new ActiveSlam(sessionId, actionSequence, beatHit, startY, value);
        }
    }

    private record ReadySlam(ServerPlayer player, DJSession session, ActiveSlam slam, BlockPos supportingBlock) {
    }
}
