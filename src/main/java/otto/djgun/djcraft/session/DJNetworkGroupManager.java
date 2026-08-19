package otto.djgun.djcraft.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.data.DiscPlaybackReference;
import otto.djgun.djcraft.data.DiscStatistics;
import otto.djgun.djcraft.init.ModDataComponents;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.loader.TrackPackManager;
import otto.djgun.djcraft.network.packet.DJGroupInvitationPayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload;
import otto.djgun.djcraft.network.packet.DJGroupPreparePayload.TrackRequirement;
import otto.djgun.djcraft.network.packet.DJGroupStatePayload;
import otto.djgun.djcraft.network.packet.DJGroupAudioRecoveryPayload;
import otto.djgun.djcraft.network.packet.PlayTrackPayload;
import otto.djgun.djcraft.network.packet.StopReason;
import otto.djgun.djcraft.playback.DJPlaylistSequencer;
import otto.djgun.djcraft.playback.DJPlaybackMode;

/**
 * Runtime-only, server-authoritative DJ listening groups.
 */
public final class DJNetworkGroupManager {
    private static final DJNetworkGroupManager INSTANCE = new DJNetworkGroupManager();

    private final Map<UUID, Group> groups = new HashMap<>();
    private final Map<UUID, UUID> groupByParticipant = new HashMap<>();

    private DJNetworkGroupManager() {
    }

    public static DJNetworkGroupManager getInstance() {
        return INSTANCE;
    }

    public boolean isParticipant(UUID playerId) {
        return groupByParticipant.containsKey(playerId);
    }

    public boolean isCombatSuppressed(UUID playerId) {
        Group group = groupFor(playerId);
        return group != null && group.playback != null
                && group.playback.recovery.isQuarantined(playerId);
    }

    public Optional<GroupView> getView(UUID playerId) {
        Group group = groupFor(playerId);
        return group == null ? Optional.empty() : Optional.of(group.view());
    }

    /** Stable server-side roster snapshot for group-scoped activities. */
    public List<UUID> getMemberIds(UUID playerId) {
        Group group = groupFor(playerId);
        return group == null ? List.of() : List.copyOf(group.members.keySet());
    }

    public boolean isOwner(UUID playerId) {
        Group group = groupFor(playerId);
        return group != null && group.ownerId.equals(playerId) && group.members.containsKey(playerId);
    }

    public Optional<UUID> getGroupId(UUID playerId) {
        Group group = groupFor(playerId);
        return group == null ? Optional.empty() : Optional.of(group.id);
    }

    public boolean create(ServerPlayer creator, int requestedJukeboxSlot) {
        if (isParticipant(creator.getUUID())) {
            fail(creator, "message.djcraft.group.already_member");
            return false;
        }
        int slot = requestedJukeboxSlot >= 0 ? requestedJukeboxSlot : findFirstJukebox(creator);
        List<DiscPlaybackReference> playlist = readPlaylist(creator, slot);
        if (playlist.isEmpty()) {
            fail(creator, "message.djcraft.group.empty_jukebox");
            return false;
        }

        UUID id = UUID.randomUUID();
        Group group = new Group(id, creator.getUUID(), creator.getUUID(), creator.getName().getString(),
                slot, playlist);
        groups.put(id, group);
        groupByParticipant.put(creator.getUUID(), id);
        group.pending.put(creator.getUUID(), "");
        sendPreparation(creator, group);
        sync(group, creator.getServer());
        return true;
    }

    public boolean invite(ServerPlayer owner, ServerPlayer target) {
        if (otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(owner.getUUID())) {
            fail(owner, "message.djcraft.cyber_grind.player_busy");
            return false;
        }
        Group group = groupFor(owner.getUUID());
        if (group == null || !group.ownerId.equals(owner.getUUID()) || !group.members.containsKey(owner.getUUID())) {
            fail(owner, "message.djcraft.group.owner_only");
            return false;
        }
        if (isParticipant(target.getUUID())) {
            fail(owner, "message.djcraft.group.target_busy");
            return false;
        }
        group.invites.add(target.getUUID());
        PacketDistributor.sendToPlayer(target,
                new DJGroupInvitationPayload(group.id, owner.getUUID(), owner.getName().getString()));
        target.sendSystemMessage(Component.translatable("message.djcraft.group.invited",
                owner.getName().getString()));
        owner.sendSystemMessage(Component.translatable("message.djcraft.group.invite_sent",
                target.getName().getString()));
        return true;
    }

    public boolean respond(ServerPlayer player, UUID groupId, boolean accepted) {
        Group group = groups.get(groupId);
        if (group == null || !group.invites.remove(player.getUUID())) {
            fail(player, "message.djcraft.group.invite_missing");
            return false;
        }
        if (!accepted) {
            return true;
        }
        if (otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(group.ownerId)) {
            fail(player, "message.djcraft.cyber_grind.player_busy");
            return false;
        }
        if (isParticipant(player.getUUID())) {
            fail(player, "message.djcraft.group.already_member");
            return false;
        }
        groupByParticipant.put(player.getUUID(), group.id);
        group.pending.put(player.getUUID(), "");
        sendPreparation(player, group);
        sync(group, player.getServer());
        return true;
    }

    public void setReady(ServerPlayer player, UUID groupId, boolean ready, String detail) {
        Group group = groups.get(groupId);
        if (group == null || !groupId.equals(groupByParticipant.get(player.getUUID()))
                || !group.pending.containsKey(player.getUUID())) {
            return;
        }
        if (!ready) {
            group.pending.put(player.getUUID(), detail == null ? "" : detail);
            sync(group, player.getServer());
            return;
        }
        group.pending.remove(player.getUUID());
        group.members.put(player.getUUID(), player.getName().getString());
        DJModeManager.getInstance().stopSession(player, StopReason.REQUESTED);
        if (group.playback != null) {
            attachMember(group, player);
        }
        sync(group, player.getServer());
    }

    public void retry(ServerPlayer player) {
        Group group = groupFor(player.getUUID());
        if (group != null && group.pending.containsKey(player.getUUID())) {
            sendPreparation(player, group);
        }
    }

    public boolean setMode(ServerPlayer player, DJPlaybackMode mode) {
        if (cyberLocked(player)) {
            return false;
        }
        Group group = ownerGroup(player);
        if (group == null) {
            return false;
        }
        group.playlist.setMode(mode);
        sync(group, player.getServer());
        return true;
    }

    public boolean play(ServerPlayer player, int index) {
        if (cyberLocked(player)) {
            return false;
        }
        Group group = ownerGroup(player);
        if (group == null) {
            return false;
        }
        try {
            startTrack(group, group.playlist.select(index), player.getServer());
            return true;
        } catch (IllegalArgumentException exception) {
            fail(player, "message.djcraft.group.invalid_track");
            return false;
        }
    }

    public boolean playDisc(ServerPlayer player, DiscPlaybackReference disc) {
        if (cyberLocked(player)) {
            return false;
        }
        Group group = ownerGroup(player);
        if (group == null) {
            return false;
        }
        List<DiscPlaybackReference> entries = group.playlist.entries();
        for (int index = 0; index < entries.size(); index++) {
            DiscPlaybackReference entry = entries.get(index);
            if (sameDisc(entry, disc)) {
                startTrack(group, group.playlist.select(index), player.getServer());
                return true;
            }
        }
        fail(player, "message.djcraft.group.invalid_track");
        return false;
    }

    public void stopPlayback(ServerPlayer player) {
        if (cyberLocked(player)) {
            return;
        }
        Group group = ownerGroup(player);
        if (group != null) {
            stopPlayback(group, StopReason.REQUESTED);
            sync(group, player.getServer());
        }
    }

    /** Internal transition used after Cyber Grind has locked normal group controls. */
    public void stopPlaybackForCyberGrind(ServerPlayer owner) {
        Group group = ownerGroup(owner);
        if (group != null) {
            stopPlayback(group, StopReason.REQUESTED);
            sync(group, owner.getServer());
        }
    }

    public void leave(ServerPlayer player, boolean disband) {
        if (cyberLocked(player)) {
            return;
        }
        Group group = groupFor(player.getUUID());
        if (group == null) {
            return;
        }
        if (disband && !group.ownerId.equals(player.getUUID())) {
            fail(player, "message.djcraft.group.owner_only");
            return;
        }
        if (disband) {
            disband(group, player.getServer());
        } else {
            removeParticipant(group, player.getUUID(), player.getServer());
        }
    }

    public void removePlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        for (Group group : List.copyOf(groups.values())) {
            group.invites.remove(playerId);
        }
        Group group = groupFor(playerId);
        if (group != null) {
            removeParticipant(group, playerId, player.getServer());
        }
    }

    private boolean cyberLocked(ServerPlayer player) {
        if (!otto.djgun.djcraft.cybergrind.CyberGrindManager.getInstance()
                .isParticipant(player.getUUID())) {
            return false;
        }
        fail(player, "message.djcraft.cyber_grind.player_busy");
        return true;
    }

    public void onRespawn(ServerPlayer player) {
        Group group = groupFor(player.getUUID());
        if (group != null && group.members.containsKey(player.getUUID()) && group.playback != null
                && !group.playback.recovery.isQuarantined(player.getUUID())) {
            attachMember(group, player);
        }
    }

    public boolean recoverMemberAudio(ServerPlayer player, long failedSessionId, long playbackId) {
        Group group = groupFor(player.getUUID());
        Playback playback = group == null ? null : group.playback;
        if (playback == null || playback.id != playbackId
                || playback.recovery.isQuarantined(player.getUUID())
                || !Long.valueOf(failedSessionId).equals(playback.sessionIds.get(player.getUUID()))) {
            DJCraft.LOGGER.warn("Ignoring stale group audio recovery from {} session={} playback={}",
                    player.getName().getString(), failedSessionId, playbackId);
            return false;
        }

        GroupAudioRecoveryTracker.Result result = playback.recovery.recordFailure(player.getUUID());
        DJModeManager.getInstance().stopSession(player, StopReason.AUDIO_UNAVAILABLE);
        playback.sessionIds.remove(player.getUUID());
        if (result.decision() == GroupAudioRecoveryTracker.Decision.RETRY) {
            PacketDistributor.sendToPlayer(player, new DJGroupAudioRecoveryPayload(group.id, playback.id,
                    DJGroupAudioRecoveryPayload.Status.RETRYING, result.attempt(),
                    GroupAudioRecoveryTracker.MAX_RETRIES));
            DJCraft.LOGGER.info("Retrying group audio for {} playback={} attempt={}/{}",
                    player.getName().getString(), playback.id, result.attempt(),
                    GroupAudioRecoveryTracker.MAX_RETRIES);
            attachMember(group, player);
        } else {
            PacketDistributor.sendToPlayer(player, new DJGroupAudioRecoveryPayload(group.id, playback.id,
                    DJGroupAudioRecoveryPayload.Status.QUARANTINED, result.attempt(),
                    GroupAudioRecoveryTracker.MAX_RETRIES));
            DJCraft.LOGGER.warn("Quarantined group audio for {} playback={} after {} failed retries",
                    player.getName().getString(), playback.id, GroupAudioRecoveryTracker.MAX_RETRIES);
        }
        return true;
    }

    public void onMemberClockDesync(ServerPlayer player, long stoppedSessionId) {
        Group group = groupFor(player.getUUID());
        Playback playback = group == null ? null : group.playback;
        if (playback == null || !Long.valueOf(stoppedSessionId).equals(
                playback.sessionIds.get(player.getUUID()))) {
            return;
        }
        playback.sessionIds.remove(player.getUUID());
        playback.recovery.quarantine(player.getUUID());
        PacketDistributor.sendToPlayer(player, new DJGroupAudioRecoveryPayload(group.id, playback.id,
                DJGroupAudioRecoveryPayload.Status.QUARANTINED,
                GroupAudioRecoveryTracker.MAX_RETRIES + 1, GroupAudioRecoveryTracker.MAX_RETRIES));
        DJCraft.LOGGER.warn("Quarantined clock-desynchronized group member {} for playback={}",
                player.getName().getString(), playback.id);
    }

    public void tick(MinecraftServer server) {
        for (Group group : List.copyOf(groups.values())) {
            Playback playback = group.playback;
            if (playback == null) {
                continue;
            }
            int duration = playback.track.getTotalDurationMs();
            if (duration > 0 && playback.clock.currentTimeMs() >= duration) {
                ServerPlayer owner = server.getPlayerList().getPlayer(group.ownerId);
                if (owner == null) {
                    stopPlayback(group, StopReason.AUDIO_ENDED);
                    sync(group, server);
                } else {
                    startTrack(group, group.playlist.next(), server);
                }
            }
        }
    }

    public void clear(MinecraftServer server) {
        for (Group group : List.copyOf(groups.values())) {
            disband(group, server);
        }
        groups.clear();
        groupByParticipant.clear();
    }

    private Group ownerGroup(ServerPlayer player) {
        Group group = groupFor(player.getUUID());
        if (group == null || !group.ownerId.equals(player.getUUID()) || !group.members.containsKey(player.getUUID())) {
            fail(player, "message.djcraft.group.owner_only");
            return null;
        }
        return group;
    }

    private Group groupFor(UUID playerId) {
        UUID groupId = groupByParticipant.get(playerId);
        return groupId == null ? null : groups.get(groupId);
    }

    private void startTrack(Group group, DiscPlaybackReference selection, MinecraftServer server) {
        var track = TrackPackManager.getInstance().getTrackPack(selection.trackId()).orElse(null);
        if (track == null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(group.ownerId);
            if (owner != null) {
                fail(owner, "message.djcraft.group.invalid_track");
            }
            return;
        }
        stopPlayback(group, StopReason.REQUESTED);
        long playbackId = ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
        Playback playback = new Playback(playbackId, track,
                new GroupPlaybackClock(track.getPlaybackStartMs()), new HashMap<>(),
                new GroupAudioRecoveryTracker());
        group.playback = playback;
        for (UUID memberId : group.members.keySet()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                attachMember(group, member);
            }
        }
        sync(group, server);
    }

    private void attachMember(Group group, ServerPlayer player) {
        Playback playback = group.playback;
        if (playback == null) {
            return;
        }
        if (playback.recovery.isQuarantined(player.getUUID())) {
            return;
        }
        DiscStatisticsService.ResolvedDisc resolved = null;
        DiscPlaybackReference source = group.playlist.entries().get(group.playlist.currentIndex());
        if (player.getUUID().equals(group.creatorId)) {
            resolved = DiscStatisticsService.resolveForPlayback(player, source);
        }
        UUID discId = resolved == null ? null : resolved.reference().discId();
        DiscStatistics statistics = resolved == null ? DiscStatistics.EMPTY : resolved.statistics();
        DJSession session = DJModeManager.getInstance().startSession(player, playback.track,
                discId, statistics, playback.clock, playback.clock.currentTimeMs() > 0L);
        playback.sessionIds.put(player.getUUID(), session.getSessionId());
        long estimatedTransitMs = Math.min(3_000L, Math.max(0L, player.connection.latency() / 2L));
        PacketDistributor.sendToPlayer(player, new PlayTrackPayload(session.getSessionId(),
                playback.track.id(), discId, group.id, playback.id, playback.clock.currentTimeMs(),
                estimatedTransitMs, player.getUUID().equals(group.ownerId)));
        session.sendResourceState();
        session.sendMovementState();
    }

    private void stopPlayback(Group group, StopReason reason) {
        if (group.playback == null) {
            return;
        }
        for (UUID memberId : group.members.keySet()) {
            ServerPlayer member = group.server == null ? null : group.server.getPlayerList().getPlayer(memberId);
            if (member != null) {
                DJModeManager.getInstance().stopSession(member, reason);
            }
        }
        group.playback = null;
    }

    private void removeParticipant(Group group, UUID playerId, MinecraftServer server) {
        group.pending.remove(playerId);
        group.members.remove(playerId);
        groupByParticipant.remove(playerId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            DJModeManager.getInstance().stopSession(player, StopReason.REQUESTED);
            PacketDistributor.sendToPlayer(player, DJGroupStatePayload.empty());
        }
        if (group.ownerId.equals(playerId)) {
            UUID successor = group.members.keySet().stream().findFirst().orElse(null);
            if (successor == null) {
                disband(group, server);
                return;
            }
            group.ownerId = successor;
            ServerPlayer successorPlayer = server.getPlayerList().getPlayer(successor);
            if (successorPlayer != null) {
                successorPlayer.sendSystemMessage(Component.translatable("message.djcraft.group.owner_transferred"));
            }
        }
        sync(group, server);
    }

    private void disband(Group group, MinecraftServer server) {
        stopPlayback(group, StopReason.REQUESTED);
        Set<UUID> affected = new LinkedHashSet<>();
        affected.addAll(group.members.keySet());
        affected.addAll(group.pending.keySet());
        affected.forEach(groupByParticipant::remove);
        groups.remove(group.id);
        for (UUID playerId : affected) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, DJGroupStatePayload.empty());
            }
        }
    }

    private void sendPreparation(ServerPlayer player, Group group) {
        List<TrackRequirement> requirements = group.distinctTrackIds.stream().map(trackId -> {
            TrackPackManager manager = TrackPackManager.getInstance();
            String contentHash = manager.getContentHash(trackId).orElse("");
            var descriptor = manager.getArchiveDescriptor(trackId).orElse(null);
            boolean downloadable = descriptor != null && descriptor.size() > 0L
                    && descriptor.size() <= Config.maxTrackPackBytes();
            return new TrackRequirement(trackId, contentHash, downloadable);
        }).toList();
        PacketDistributor.sendToPlayer(player, new DJGroupPreparePayload(group.id, requirements));
    }

    private void sync(Group group, MinecraftServer server) {
        group.server = server;
        ServerPlayer owner = server.getPlayerList().getPlayer(group.ownerId);
        String ownerName = owner == null ? group.ownerName : owner.getName().getString();
        if (owner != null) {
            group.ownerName = ownerName;
        }
        DJGroupStatePayload payload = new DJGroupStatePayload(true, group.id, group.ownerId, ownerName,
                List.copyOf(group.members.values()), pendingLabels(group, server), group.playlist.mode().ordinal(),
                group.playlist.currentIndex(), group.playback == null ? 0L : group.playback.id,
                group.playback == null ? "" : group.playback.track.id());
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.addAll(group.members.keySet());
        recipients.addAll(group.pending.keySet());
        for (UUID playerId : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static List<String> pendingLabels(Group group, MinecraftServer server) {
        List<String> labels = new ArrayList<>();
        group.pending.forEach((id, detail) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            String name = player == null ? id.toString() : player.getName().getString();
            labels.add(detail == null || detail.isBlank() ? name : name + " (" + detail + ")");
        });
        return List.copyOf(labels);
    }

    private static int findFirstJukebox(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (!readPlaylist(player, slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
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
                playlist.add(new DiscPlaybackReference(trackId.toLowerCase(Locale.ROOT),
                        disc.get(ModDataComponents.DISC_ID.get()), jukeboxSlot, discSlot));
            }
        }
        return List.copyOf(playlist);
    }

    private static boolean sameDisc(DiscPlaybackReference left, DiscPlaybackReference right) {
        if (left.discId() != null && right.discId() != null) {
            return left.discId().equals(right.discId());
        }
        return left.trackId().equals(right.trackId())
                && left.jukeboxInventorySlot() == right.jukeboxInventorySlot()
                && left.discSlot() == right.discSlot();
    }

    private static void fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key));
    }

    public record GroupView(UUID id, UUID ownerId, String ownerName, List<String> members,
            List<String> pending, List<DiscPlaybackReference> playlist, DJPlaybackMode mode,
            int currentIndex, long playbackId, String currentTrack) {
    }

    private static final class Group {
        private final UUID id;
        private UUID ownerId;
        private final UUID creatorId;
        private String ownerName;
        @SuppressWarnings("unused")
        private final int jukeboxSlot;
        private final DJPlaylistSequencer playlist;
        private final Set<String> distinctTrackIds;
        private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, String> pending = new LinkedHashMap<>();
        private final Set<UUID> invites = new LinkedHashSet<>();
        private Playback playback;
        private MinecraftServer server;

        private Group(UUID id, UUID ownerId, UUID creatorId, String ownerName, int jukeboxSlot,
                List<DiscPlaybackReference> playlist) {
            this.id = id;
            this.ownerId = ownerId;
            this.creatorId = creatorId;
            this.ownerName = ownerName;
            this.jukeboxSlot = jukeboxSlot;
            this.playlist = new DJPlaylistSequencer(playlist);
            LinkedHashSet<String> tracks = new LinkedHashSet<>();
            playlist.forEach(entry -> tracks.add(entry.trackId()));
            this.distinctTrackIds = Set.copyOf(tracks);
        }

        private GroupView view() {
            return new GroupView(id, ownerId, ownerName, List.copyOf(members.values()),
                    List.copyOf(pending.values()), playlist.entries(), playlist.mode(),
                    playlist.currentIndex(), playback == null ? 0L : playback.id,
                    playback == null ? "" : playback.track.id());
        }
    }

    private record Playback(long id, otto.djgun.djcraft.data.TrackPack track,
            GroupPlaybackClock clock, Map<UUID, Long> sessionIds,
            GroupAudioRecoveryTracker recovery) {
    }
}
