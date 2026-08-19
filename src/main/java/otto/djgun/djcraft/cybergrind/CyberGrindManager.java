package otto.djgun.djcraft.cybergrind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.data.DiscStatistics;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.init.ModBlocks;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.CyberGrindPreparePayload;
import otto.djgun.djcraft.network.packet.CyberGrindPresetListPayload;
import otto.djgun.djcraft.network.packet.CyberGrindResultPayload;
import otto.djgun.djcraft.network.packet.CyberGrindSpawnWarningPayload;
import otto.djgun.djcraft.network.packet.CyberGrindStatePayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.network.server.ClientTrackStatusService;
import otto.djgun.djcraft.playback.DJPlaybackMode;
import otto.djgun.djcraft.playback.DJPlaylistSequencer;
import otto.djgun.djcraft.session.DJModeManager;
import otto.djgun.djcraft.session.DJNetworkGroupManager;
import otto.djgun.djcraft.session.DJSession;
import otto.djgun.djcraft.session.GroupPlaybackClock;

/** Server authority for preparation, arena allocation, waves and scoring. */
public final class CyberGrindManager {
    public static final ResourceLocation DIMENSION_ID = ResourceLocation.fromNamespaceAndPath(
            DJCraft.MODID, "cyber_grind");
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, DIMENSION_ID);
    private static final CyberGrindManager INSTANCE = new CyberGrindManager();
    private static final int ARENA_Y = 80;
    private static final int ARENA_SIZE = 64;
    private static final int ARENA_HALF = ARENA_SIZE / 2;
    private static final int ARENA_SPACING = 1024;
    private static final int COUNTDOWN_TICKS = 100;
    private static final long READY_TIMEOUT_TICKS = 20L * 300L;
    private static final double MAX_TABLE_DISTANCE_SQUARED = 64.0;
    private static final double AGGRO_FOLLOW_RANGE = 96.0;
    private static final double AGGRO_FOLLOW_RANGE_SQUARED = AGGRO_FOLLOW_RANGE * AGGRO_FOLLOW_RANGE;

    private final Map<UUID, Run> runs = new LinkedHashMap<>();
    private final Map<UUID, UUID> runByPlayer = new HashMap<>();
    private final Queue<Integer> freeSlots = new ArrayDeque<>();
    private int nextSlot;

    private CyberGrindManager() {
    }

    public static CyberGrindManager getInstance() {
        return INSTANCE;
    }

    public boolean isParticipant(UUID playerId) {
        return runByPlayer.containsKey(playerId);
    }

    public Optional<UUID> runId(UUID playerId) {
        return Optional.ofNullable(runByPlayer.get(playerId));
    }

    public boolean sameActiveRun(UUID first, UUID second) {
        UUID runId = runByPlayer.get(first);
        return runId != null && runId.equals(runByPlayer.get(second))
                && runs.containsKey(runId) && runs.get(runId).phase == Phase.ACTIVE;
    }

    public void syncPresets(ServerPlayer player) {
        List<CyberGrindPresetListPayload.Preset> presets = CyberGrindProfileManager.getInstance().summaries()
                .stream().map(summary -> new CyberGrindPresetListPayload.Preset(
                        summary.id().toString(), summary.displayName(), summary.description())).toList();
        PacketDistributor.sendToPlayer(player, new CyberGrindPresetListPayload(presets));
    }

    public void prepare(ServerPlayer owner, String profileId, int jukeboxSlot, int mode,
            int startDiscSlot, BlockPos tablePos) {
        if (!validTable(owner, tablePos)) {
            fail(owner, "message.djcraft.cyber_grind.invalid_table");
            return;
        }
        ResourceLocation parsedProfile = ResourceLocation.tryParse(profileId);
        CyberGrindProfile profile = parsedProfile == null ? null
                : CyberGrindProfileManager.getInstance().get(parsedProfile).orElse(null);
        if (profile == null) {
            fail(owner, "message.djcraft.cyber_grind.invalid_profile");
            return;
        }
        if (mode < 0 || mode >= DJPlaybackMode.values().length) {
            fail(owner, "message.djcraft.cyber_grind.invalid_playlist");
            return;
        }
        List<DiscPlaybackReference> playlist = readPlaylist(owner, jukeboxSlot);
        int startIndex = CyberGrindPlaylistRules.indexForDiscSlot(playlist, startDiscSlot);
        if (playlist.isEmpty() || startIndex < 0) {
            fail(owner, "message.djcraft.cyber_grind.invalid_playlist");
            return;
        }

        DJNetworkGroupManager groups = DJNetworkGroupManager.getInstance();
        List<UUID> roster;
        UUID groupId = groups.getGroupId(owner.getUUID()).orElse(null);
        if (groupId == null) {
            roster = List.of(owner.getUUID());
        } else {
            if (!groups.isOwner(owner.getUUID())) {
                fail(owner, "message.djcraft.group.owner_only");
                return;
            }
            roster = groups.getMemberIds(owner.getUUID());
        }
        if (roster.isEmpty() || roster.stream().anyMatch(runByPlayer::containsKey)) {
            fail(owner, "message.djcraft.cyber_grind.player_busy");
            return;
        }
        List<ServerPlayer> members = new ArrayList<>();
        for (UUID memberId : roster) {
            ServerPlayer member = owner.getServer().getPlayerList().getPlayer(memberId);
            if (member == null || !member.isAlive()) {
                fail(owner, "message.djcraft.cyber_grind.member_unavailable");
                return;
            }
            members.add(member);
        }

        UUID runId = UUID.randomUUID();
        DJPlaylistSequencer sequencer = new DJPlaylistSequencer(playlist);
        sequencer.setMode(DJPlaybackMode.values()[mode]);
        sequencer.select(startIndex);
        Run run = new Run(runId, owner.getUUID(), groupId, profile, List.copyOf(roster),
                sequencer, owner.getServer().overworld().getGameTime() + READY_TIMEOUT_TICKS);
        runs.put(runId, run);
        roster.forEach(memberId -> runByPlayer.put(memberId, runId));

        List<CyberGrindPreparePayload.TrackRequirement> requirements = requirements(playlist);
        List<String> names = members.stream().map(member -> member.getName().getString()).toList();
        CyberGrindPreparePayload payload = new CyberGrindPreparePayload(runId,
                profile.displayName(), owner.getName().getString(), names, requirements);
        members.forEach(member -> PacketDistributor.sendToPlayer(member, payload));
        DJCraft.LOGGER.info("Prepared Cyber Grind run {} preset={} roster={}", runId, profile.id(), roster);
    }

    public void setReady(ServerPlayer player, UUID runId, boolean ready, String detail) {
        Run run = runs.get(runId);
        if (run == null || run.phase != Phase.PREPARING || !run.roster.contains(player.getUUID())) {
            return;
        }
        if (!ready || !tracksVerified(player, run.playlist.entries())) {
            cancel(run, player.getServer(), Component.translatable(
                    "message.djcraft.cyber_grind.prepare_failed",
                    detail == null || detail.isBlank() ? player.getName().getString() : detail));
            return;
        }
        run.ready.add(player.getUUID());
        if (run.ready.containsAll(run.roster)) {
            run.phase = Phase.COUNTDOWN;
            run.countdownTicks = COUNTDOWN_TICKS;
            sync(run, player.getServer());
        }
    }

    public boolean exit(ServerPlayer player, UUID expectedRunId) {
        Run run = runFor(player.getUUID());
        if (run == null || expectedRunId != null && !run.id.equals(expectedRunId)) {
            return false;
        }
        eliminate(run, player, true);
        return true;
    }

    public boolean handleLethalDamage(ServerPlayer player) {
        Run run = runFor(player.getUUID());
        if (run == null || run.phase != Phase.ACTIVE || !run.alive.contains(player.getUUID())) {
            return false;
        }
        eliminate(run, player, true);
        return true;
    }

    public void onLogout(ServerPlayer player) {
        Run run = runFor(player.getUUID());
        if (run == null) {
            return;
        }
        if (run.phase == Phase.ACTIVE) {
            eliminate(run, player, false);
        } else {
            cancel(run, player.getServer(), Component.translatable("message.djcraft.cyber_grind.member_left"));
        }
    }

    public void onLogin(ServerPlayer player) {
        syncPresets(player);
        CyberGrindSavedData data = data(player.getServer());
        data.returnPoint(player.getUUID()).ifPresent(point -> returnPlayer(player, point, true));
    }

    public void onAudioFailure(ServerPlayer player) {
        Run run = runFor(player.getUUID());
        if (run != null && run.phase == Phase.ACTIVE) {
            int attempt = run.audioRetries.merge(player.getUUID(), 1, Integer::sum);
            if (attempt <= 2 && run.track != null && run.clock != null) {
                DJSession session = DJModeManager.getInstance().startSession(player, run.track, null,
                        DiscStatistics.EMPTY, run.clock, true);
                run.sessionIds.put(player.getUUID(), session.getSessionId());
                long transit = Math.min(3_000L, Math.max(0L, player.connection.latency() / 2L));
                PacketDistributor.sendToPlayer(player, new PlayTrackPayload(session.getSessionId(),
                        run.track.id(), null, run.id, run.playbackId, run.clock.currentTimeMs(), transit,
                        player.getUUID().equals(run.ownerId)));
                session.sendResourceState();
                session.sendMovementState();
                player.sendSystemMessage(Component.translatable(
                        "message.djcraft.cyber_grind.audio_retry", attempt, 2));
            } else {
                eliminate(run, player, true);
            }
        }
    }

    public boolean ownsEntity(UUID entityId) {
        return runs.values().stream().anyMatch(run -> run.entityCosts.containsKey(entityId)
                || run.summonedEntities.contains(entityId));
    }

    public boolean isInsideArena(BlockPos pos) {
        return runs.values().stream().filter(run -> run.slot >= 0)
                .anyMatch(run -> Math.abs(pos.getX() - run.centerX) <= ARENA_HALF
                        && Math.abs(pos.getZ() - run.centerZ) <= ARENA_HALF
                        && pos.getY() >= ARENA_Y - 4 && pos.getY() <= ARENA_Y + 64);
    }

    public void associateSummon(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !level.dimension().equals(DIMENSION)
                || ownsEntity(entity.getUUID()) || !(entity instanceof Mob mob)) {
            return;
        }
        Run run = runAt(entity.blockPosition());
        if (run != null && run.phase == Phase.ACTIVE) {
            run.summonedEntities.add(mob.getUUID());
            mob.setPersistenceRequired();
            strengthenAggroRange(mob);
            retarget(run, mob, level);
        }
    }

    public void tick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        for (Run run : List.copyOf(runs.values())) {
            if (run.phase == Phase.PREPARING) {
                if (gameTime >= run.readyDeadline) {
                    cancel(run, server, Component.translatable("message.djcraft.cyber_grind.prepare_timeout"));
                }
                continue;
            }
            if (run.phase == Phase.COUNTDOWN) {
                run.countdownTicks--;
                if (run.countdownTicks <= 0) {
                    activate(run, server);
                } else if (run.countdownTicks % 20 == 0) {
                    sync(run, server);
                }
                continue;
            }
            if (run.phase == Phase.ACTIVE) {
                tickActive(run, server, gameTime);
            }
        }
    }

    public void clear(MinecraftServer server) {
        for (Run run : List.copyOf(runs.values())) {
            if (run.phase == Phase.ACTIVE) {
                for (UUID playerId : List.copyOf(run.alive)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        eliminate(run, player, true);
                    }
                }
            } else {
                cancel(run, server, Component.translatable("message.djcraft.cyber_grind.server_stopped"));
            }
        }
        runs.clear();
        runByPlayer.clear();
    }

    private void activate(Run run, MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) {
            cancel(run, server, Component.translatable("message.djcraft.cyber_grind.dimension_missing"));
            return;
        }
        run.slot = allocateSlot();
        run.centerX = (run.slot % 64) * ARENA_SPACING;
        run.centerZ = (run.slot / 64) * ARENA_SPACING;
        buildArena(level, run.centerX, run.centerZ);
        run.phase = Phase.ACTIVE;
        run.alive.addAll(run.roster);
        ServerPlayer groupOwner = server.getPlayerList().getPlayer(run.ownerId);
        if (run.groupId != null && groupOwner != null) {
            DJNetworkGroupManager.getInstance().stopPlaybackForCyberGrind(groupOwner);
        }
        CyberGrindSavedData saved = data(server);
        int index = 0;
        for (UUID playerId : run.roster) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            saved.putReturn(playerId, new CyberGrindSavedData.ReturnPoint(player.level().dimension().location(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
            double angle = Math.PI * 2.0 * index++ / Math.max(1, run.roster.size());
            player.teleportTo(level, run.centerX + Math.cos(angle) * 3.0, ARENA_Y + 1,
                    run.centerZ + Math.sin(angle) * 3.0, player.getYRot(), player.getXRot());
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            player.resetFallDistance();
        }
        startTrack(run, run.playlist.entries().get(run.playlist.currentIndex()), server);
        scheduleWave(run, level, server.overworld().getGameTime());
    }

    private void tickActive(Run run, MinecraftServer server, long gameTime) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null) {
            finishRun(run, server);
            return;
        }
        if (run.track != null && run.clock != null
                && run.track.hasReachedPlaybackEnd(run.clock.currentTimeMs())) {
            startTrack(run, run.playlist.next(), server);
        }
        run.entityCosts.keySet().removeIf(entityId -> {
            Mob mob = run.controlledMobs.get(entityId);
            boolean removed = mob == null || mob.isRemoved() || !mob.isAlive();
            if (removed) {
                run.controlledMobs.remove(entityId);
            }
            return removed;
        });
        run.summonedEntities.removeIf(entityId -> {
            Entity entity = level.getEntity(entityId);
            return entity == null || !entity.isAlive();
        });

        List<PendingSpawn> due = run.pending.stream().filter(spawn -> spawn.spawnAt <= gameTime).toList();
        if (!due.isEmpty()) {
            run.pending.removeAll(due);
            due.forEach(spawn -> spawn(run, level, spawn));
            sync(run, server);
        }
        if (CyberGrindRunRules.canAdvance(run.pending.isEmpty(), gameTime, run.nextAdvanceCheck,
                livingWeight(run), run.profile.advanceThreshold())) {
            run.completedWaves = run.wave;
            scheduleWave(run, level, gameTime);
        }
        if (gameTime % 10L == 0L) {
            strengthenAggro(run, level);
            sync(run, server);
        }
        for (UUID playerId : List.copyOf(run.alive)) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && CyberGrindRunRules.shouldEliminateForLocation(
                    player.level() == level, inside(run, player.position()))) {
                eliminate(run, player, true);
            }
        }
    }

    private void scheduleWave(Run run, ServerLevel level, long gameTime) {
        run.wave++;
        CyberGrindWavePlanner.Plan plan = CyberGrindWavePlanner.plan(
                run.profile, run.wave, Math.max(1, run.alive.size()), ThreadLocalRandom.current());
        List<Vec3> reserved = new ArrayList<>();
        for (CyberGrindWavePlanner.Spawn planned : plan.spawns()) {
            Vec3 position = findSpawn(run, level, reserved);
            if (position == null) {
                DJCraft.LOGGER.warn("Could not find Cyber Grind spawn position run={} wave={} entity={}",
                        run.id, run.wave, planned.entry().entityId());
                continue;
            }
            reserved.add(position);
            UUID warningId = UUID.randomUUID();
            run.pending.add(new PendingSpawn(warningId, planned.entry(), position,
                    gameTime + run.profile.warningTicks()));
            CyberGrindSpawnWarningPayload warning = new CyberGrindSpawnWarningPayload(
                    warningId, position.x, position.y + 0.02, position.z,
                    Math.max(1.0F, planned.entry().cost() >= 30 ? 2.5F : 1.25F),
                    run.profile.warningTicks());
            send(run, level.getServer(), warning);
        }
        run.nextAdvanceCheck = gameTime + run.profile.warningTicks() + 20L;
        sync(run, level.getServer());
    }

    private void spawn(Run run, ServerLevel level, PendingSpawn pending) {
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(pending.entry.entityId()).orElse(null);
        if (type == null) {
            return;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            DJCraft.LOGGER.error("Cyber Grind entry {} did not create a Mob", pending.entry.entityId());
            return;
        }
        mob.moveTo(pending.position.x, pending.position.y, pending.position.z,
                ThreadLocalRandom.current().nextFloat(360.0F), 0.0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null);
        if (!pending.entry.nbt().isEmpty()) {
            CompoundTag merged = new CompoundTag();
            mob.saveWithoutId(merged);
            merged.merge(pending.entry.nbt().copy());
            mob.load(merged);
            mob.moveTo(pending.position.x, pending.position.y, pending.position.z,
                    mob.getYRot(), mob.getXRot());
        }
        mob.setPersistenceRequired();
        strengthenAggroRange(mob);
        if (!level.noCollision(mob, mob.getBoundingBox())) {
            DJCraft.LOGGER.warn("Cyber Grind spawn collision run={} wave={} entity={} position={}",
                    run.id, run.wave, pending.entry.entityId(), pending.position);
            return;
        }
        if (!level.addFreshEntity(mob)) {
            DJCraft.LOGGER.warn("Cyber Grind rejected entity spawn run={} wave={} entity={} position={}",
                    run.id, run.wave, pending.entry.entityId(), pending.position);
            return;
        }
        run.entityCosts.put(mob.getUUID(), pending.entry.cost());
        run.controlledMobs.put(mob.getUUID(), mob);
        retarget(run, mob, level);
    }

    private void strengthenAggro(Run run, ServerLevel level) {
        Set<UUID> mobs = new HashSet<>(run.entityCosts.keySet());
        mobs.addAll(run.summonedEntities);
        for (UUID entityId : mobs) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof Mob mob && mob.isAlive()) {
                strengthenAggroRange(mob);
                retarget(run, mob, level);
            }
        }
    }

    private static void strengthenAggroRange(Mob mob) {
        var followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null && followRange.getBaseValue() < AGGRO_FOLLOW_RANGE) {
            followRange.setBaseValue(AGGRO_FOLLOW_RANGE);
        }
    }

    private static void retarget(Run run, Mob mob, ServerLevel level) {
        ServerPlayer nearest = null;
        double nearestDistance = AGGRO_FOLLOW_RANGE_SQUARED;
        for (UUID playerId : run.alive) {
            ServerPlayer candidate = level.getServer().getPlayerList().getPlayer(playerId);
            if (candidate == null || candidate.level() != level || !candidate.isAlive()
                    || candidate.isCreative() || candidate.isSpectator() || !mob.canAttack(candidate)) {
                continue;
            }
            double distance = mob.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
            mob.setAggressive(true);
        }
    }

    private void startTrack(Run run, DiscPlaybackReference selection, MinecraftServer server) {
        TrackPack track = TrackPackManager.getInstance().getTrackPack(selection.trackId()).orElse(null);
        if (track == null) {
            finishRun(run, server);
            return;
        }
        for (UUID playerId : run.alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                DJModeManager.getInstance().stopSession(player, StopReason.REQUESTED);
            }
        }
        run.track = track;
        run.clock = new GroupPlaybackClock(track.getPlaybackStartMs());
        run.playbackId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        run.audioRetries.clear();
        for (UUID playerId : run.alive) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            DJSession session = DJModeManager.getInstance().startSession(player, track, null,
                    DiscStatistics.EMPTY, run.clock, run.clock.currentTimeMs() > 0L);
            run.sessionIds.put(playerId, session.getSessionId());
            long transit = Math.min(3_000L, Math.max(0L, player.connection.latency() / 2L));
            PacketDistributor.sendToPlayer(player, new PlayTrackPayload(session.getSessionId(),
                    track.id(), null, run.id, run.playbackId, run.clock.currentTimeMs(), transit,
                    playerId.equals(run.ownerId)));
            session.sendResourceState();
            session.sendMovementState();
        }
    }

    private void eliminate(Run run, ServerPlayer player, boolean teleport) {
        if (!run.alive.remove(player.getUUID()) && run.phase == Phase.ACTIVE) {
            return;
        }
        runByPlayer.remove(player.getUUID());
        DJModeManager.getInstance().stopSession(player, StopReason.REQUESTED);
        CyberGrindSavedData saved = data(player.getServer());
        int personalBest = saved.updatePersonal(run.profile.id().toString(), player.getUUID(), run.completedWaves);
        int groupBest = run.roster.size() > 1
                ? saved.updateGroup(run.profile.id().toString(), run.roster, run.completedWaves) : 0;
        PacketDistributor.sendToPlayer(player, CyberGrindStatePayload.empty());
        PacketDistributor.sendToPlayer(player, new CyberGrindResultPayload(
                run.profile.displayName(), run.completedWaves, personalBest, groupBest));
        if (teleport) {
            saved.returnPoint(player.getUUID()).ifPresent(point -> returnPlayer(player, point, true));
        }
        if (run.alive.isEmpty()) {
            finishRun(run, player.getServer());
        } else {
            sync(run, player.getServer());
        }
    }

    private void returnPlayer(ServerPlayer player, CyberGrindSavedData.ReturnPoint point, boolean clear) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, point.dimension());
        ServerLevel target = player.getServer().getLevel(key);
        if (target == null || key.equals(DIMENSION)) {
            target = player.getServer().overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(target, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        }
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.resetFallDistance();
        if (clear) {
            data(player.getServer()).removeReturn(player.getUUID());
        }
    }

    private void finishRun(Run run, MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level != null) {
            for (Mob mob : run.controlledMobs.values()) {
                if (!mob.isRemoved()) {
                    mob.discard();
                }
            }
            for (UUID entityId : run.summonedEntities) {
                Entity entity = level.getEntity(entityId);
                if (entity != null) {
                    entity.discard();
                }
            }
            if (run.slot >= 0) {
                clearArena(level, run.centerX, run.centerZ);
            }
        }
        run.roster.forEach(playerId -> runByPlayer.remove(playerId, run.id));
        runs.remove(run.id);
        if (run.slot >= 0) {
            freeSlots.add(run.slot);
        }
    }

    private void cancel(Run run, MinecraftServer server, Component reason) {
        for (UUID playerId : run.roster) {
            runByPlayer.remove(playerId, run.id);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(reason);
                PacketDistributor.sendToPlayer(player, CyberGrindStatePayload.empty());
            }
        }
        runs.remove(run.id);
    }

    private void sync(Run run, MinecraftServer server) {
        CyberGrindStatePayload payload = new CyberGrindStatePayload(run.phase != Phase.PREPARING,
                run.id, run.profile.displayName(), run.wave, run.completedWaves,
                livingWeight(run), run.profile.advanceThreshold(), run.countdownTicks);
        send(run, server, payload);
    }

    private static void send(Run run, MinecraftServer server, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        for (UUID playerId : run.roster) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && runByPlayerStatic(run, playerId)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static boolean runByPlayerStatic(Run run, UUID playerId) {
        return run.roster.contains(playerId) && (run.phase != Phase.ACTIVE || run.alive.contains(playerId));
    }

    private static int livingWeight(Run run) {
        return run.entityCosts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private Run runFor(UUID playerId) {
        UUID runId = runByPlayer.get(playerId);
        return runId == null ? null : runs.get(runId);
    }

    private Run runAt(BlockPos pos) {
        return runs.values().stream().filter(run -> run.slot >= 0 && inside(run, Vec3.atCenterOf(pos)))
                .findFirst().orElse(null);
    }

    private static boolean inside(Run run, Vec3 position) {
        return Math.abs(position.x - run.centerX) <= ARENA_HALF + 8
                && Math.abs(position.z - run.centerZ) <= ARENA_HALF + 8
                && position.y >= ARENA_Y - 64 && position.y <= ARENA_Y + 96;
    }

    private static boolean validTable(ServerPlayer player, BlockPos tablePos) {
        return player.level().getBlockState(tablePos).is(ModBlocks.DJ_CRAFTING_TABLE.get())
                && player.position().distanceToSqr(Vec3.atCenterOf(tablePos)) <= MAX_TABLE_DISTANCE_SQUARED;
    }

    private static List<DiscPlaybackReference> readPlaylist(ServerPlayer player, int jukeboxSlot) {
        if (jukeboxSlot < 0 || jukeboxSlot >= player.getInventory().getContainerSize()) {
            return List.of();
        }
        ItemStack jukebox = player.getInventory().getItem(jukeboxSlot);
        if (!jukebox.is(ModItems.PORTABLE_JUKEBOX.get())) {
            return List.of();
        }
        NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);
        jukebox.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        List<DiscPlaybackReference> playlist = new ArrayList<>();
        for (int discSlot = 0; discSlot < items.size(); discSlot++) {
            ItemStack disc = items.get(discSlot);
            String trackId = disc.get(ModDataComponents.TRACK_PACK_ID.get());
            if (disc.is(ModItems.EMPTY_DISC.get()) && trackId != null
                    && TrackPackManager.getInstance().isPackLoaded(trackId)) {
                playlist.add(new DiscPlaybackReference(trackId,
                        disc.get(ModDataComponents.DISC_ID.get()), jukeboxSlot, discSlot));
            }
        }
        return List.copyOf(playlist);
    }

    private static List<CyberGrindPreparePayload.TrackRequirement> requirements(
            List<DiscPlaybackReference> playlist) {
        Set<String> unique = new LinkedHashSet<>();
        playlist.forEach(entry -> unique.add(entry.trackId()));
        TrackPackManager manager = TrackPackManager.getInstance();
        return unique.stream().map(trackId -> {
            String hash = manager.getContentHash(trackId).orElse("");
            var descriptor = manager.getArchiveDescriptor(trackId).orElse(null);
            boolean downloadable = descriptor != null && descriptor.size() > 0
                    && descriptor.size() <= Config.maxTrackPackBytes();
            return new CyberGrindPreparePayload.TrackRequirement(trackId, hash, downloadable);
        }).toList();
    }

    private static boolean tracksVerified(ServerPlayer player, List<DiscPlaybackReference> playlist) {
        TrackPackManager manager = TrackPackManager.getInstance();
        return playlist.stream().map(DiscPlaybackReference::trackId).distinct().allMatch(trackId ->
                ClientTrackStatusService.isVerified(player, trackId,
                        manager.getContentHash(trackId).orElse(null)));
    }

    private Vec3 findSpawn(Run run, ServerLevel level, List<Vec3> reserved) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double x = run.centerX + ThreadLocalRandom.current().nextDouble(-27.0, 27.0);
            double z = run.centerZ + ThreadLocalRandom.current().nextDouble(-27.0, 27.0);
            Vec3 candidate = new Vec3(x, ARENA_Y + 1.0, z);
            boolean nearPlayer = run.alive.stream().map(level.getServer().getPlayerList()::getPlayer)
                    .filter(java.util.Objects::nonNull).anyMatch(player -> player.position().distanceToSqr(candidate) < 36.0);
            boolean nearWarning = reserved.stream().anyMatch(other -> other.distanceToSqr(candidate) < 16.0);
            AABB clearance = new AABB(x - 0.75, ARENA_Y + 1, z - 0.75,
                    x + 0.75, ARENA_Y + 4, z + 0.75);
            if (!nearPlayer && !nearWarning && level.getBlockState(BlockPos.containing(x, ARENA_Y, z)).isSolid()
                    && level.noCollision(clearance)) {
                return candidate;
            }
        }
        return null;
    }

    private int allocateSlot() {
        Integer reused = freeSlots.poll();
        return reused == null ? nextSlot++ : reused;
    }

    private static void buildArena(ServerLevel level, int centerX, int centerZ) {
        for (int x = -ARENA_HALF; x < ARENA_HALF; x++) {
            for (int z = -ARENA_HALF; z < ARENA_HALF; z++) {
                boolean edge = x == -ARENA_HALF || x == ARENA_HALF - 1
                        || z == -ARENA_HALF || z == ARENA_HALF - 1;
                boolean grid = Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0;
                var block = edge || grid ? Blocks.SEA_LANTERN.defaultBlockState()
                        : Blocks.BLACK_CONCRETE.defaultBlockState();
                level.setBlockAndUpdate(new BlockPos(centerX + x, ARENA_Y, centerZ + z), block);
            }
        }
    }

    private static void clearArena(ServerLevel level, int centerX, int centerZ) {
        for (int x = -ARENA_HALF; x < ARENA_HALF; x++) {
            for (int z = -ARENA_HALF; z < ARENA_HALF; z++) {
                level.setBlockAndUpdate(new BlockPos(centerX + x, ARENA_Y, centerZ + z),
                        Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    private static CyberGrindSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CyberGrindSavedData.FACTORY, CyberGrindSavedData.NAME);
    }

    private enum Phase {
        PREPARING,
        COUNTDOWN,
        ACTIVE
    }

    private static final class Run {
        private final UUID id;
        private final UUID ownerId;
        @SuppressWarnings("unused")
        private final UUID groupId;
        private final CyberGrindProfile profile;
        private final List<UUID> roster;
        private final DJPlaylistSequencer playlist;
        private final long readyDeadline;
        private final Set<UUID> ready = new HashSet<>();
        private final Set<UUID> alive = new LinkedHashSet<>();
        private final Map<UUID, Integer> entityCosts = new HashMap<>();
        private final Map<UUID, Mob> controlledMobs = new HashMap<>();
        private final Set<UUID> summonedEntities = new HashSet<>();
        private final List<PendingSpawn> pending = new ArrayList<>();
        private final Map<UUID, Long> sessionIds = new HashMap<>();
        private final Map<UUID, Integer> audioRetries = new HashMap<>();
        private Phase phase = Phase.PREPARING;
        private int countdownTicks;
        private int slot = -1;
        private int centerX;
        private int centerZ;
        private int wave;
        private int completedWaves;
        private long nextAdvanceCheck;
        private TrackPack track;
        private GroupPlaybackClock clock;
        private long playbackId;

        private Run(UUID id, UUID ownerId, UUID groupId, CyberGrindProfile profile,
                List<UUID> roster, DJPlaylistSequencer playlist, long readyDeadline) {
            this.id = id;
            this.ownerId = ownerId;
            this.groupId = groupId;
            this.profile = profile;
            this.roster = roster;
            this.playlist = playlist;
            this.readyDeadline = readyDeadline;
        }
    }

    private record PendingSpawn(UUID warningId, CyberGrindProfile.EnemyEntry entry,
            Vec3 position, long spawnAt) {
    }
}
