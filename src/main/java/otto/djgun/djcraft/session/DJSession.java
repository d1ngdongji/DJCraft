package otto.djgun.djcraft.session;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.data.BeatDefinition;
import otto.djgun.djcraft.data.BeatEvent;
import otto.djgun.djcraft.data.TrackPack;
import otto.djgun.djcraft.event.OnBeatEvent;
import otto.djgun.djcraft.init.ModAttributes;
import otto.djgun.djcraft.init.ModItems;
import otto.djgun.djcraft.init.ModGameRules;
import otto.djgun.djcraft.init.ModEnchantments;
import otto.djgun.djcraft.item.BandOfEnergyItem;
import otto.djgun.djcraft.item.DJFumoItem;
import otto.djgun.djcraft.item.NoteInABottleItem;
import otto.djgun.djcraft.network.packet.DJSessionStatePayload;
import otto.djgun.djcraft.network.packet.DJMovementStatePayload;
import otto.djgun.djcraft.combat.DJShieldRules;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.combat.HitResult;
import otto.djgun.djcraft.util.BeatGridUtil;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import otto.djgun.djcraft.data.DiscStatistics;

/**
 * DJ播放会话
 * 管理单个曲目的播放状态和节拍检测
 */
public class DJSession {

    private final long sessionId;
    private final Player player;
    private final TrackPack trackPack;
    private final DJServerClock clock;
    private int lastBeatIndex = -1;
    private boolean playing = true;
    private final DJSessionValidationState validationState = new DJSessionValidationState();
    private final DJSessionResourceState resourceState;
    private final DJMovementAbilityState movementState;
    private final DJShieldState<InteractionHand, Item> shieldState = new DJShieldState<>();
    private final UUID discId;
    private final DiscStatistics initialDiscStatistics;
    private int currentTrackMaxCombo;
    private long lastStatisticsWriteMs = -1L;
    private Long statisticsClockOffsetMs;
    private Long statisticsPlaybackStartMs;
    private int lastSyncedCombo = Integer.MIN_VALUE;
    private int lastSyncedCurrentTrackCombo = Integer.MIN_VALUE;
    private double lastSyncedEnergy = Double.NaN;
    private double lastSyncedMaxEnergy = Double.NaN;
    private int lastSyncedToleranceChances = Integer.MIN_VALUE;
    private int lastSyncedMaxToleranceChances = Integer.MIN_VALUE;
    private int lastSyncedOffBeatAttackDamagePercent = Integer.MIN_VALUE;

    public DJSession(long sessionId, Player player, TrackPack trackPack, double initialEnergy) {
        this(sessionId, player, trackPack, initialEnergy, getMaxToleranceChances(player), 0);
    }

    public DJSession(long sessionId, Player player, TrackPack trackPack, double initialEnergy,
            int initialToleranceChances, int initialToleranceRechargeProgress) {
        this(sessionId, player, trackPack, initialEnergy, initialToleranceChances,
                initialToleranceRechargeProgress, null, DiscStatistics.EMPTY);
    }

    public DJSession(long sessionId, Player player, TrackPack trackPack, double initialEnergy,
            int initialToleranceChances, int initialToleranceRechargeProgress,
            UUID discId, DiscStatistics initialDiscStatistics) {
        this(sessionId, player, trackPack, initialEnergy, initialToleranceChances,
                initialToleranceRechargeProgress, discId, initialDiscStatistics,
                new StandaloneDJServerClock(trackPack.getPlaybackStartMs()),
                trackPack.getPlaybackStartMs() > 0);
    }

    public DJSession(long sessionId, Player player, TrackPack trackPack, double initialEnergy,
            int initialToleranceChances, int initialToleranceRechargeProgress,
            UUID discId, DiscStatistics initialDiscStatistics, DJServerClock clock,
            boolean skipElapsedBeats) {
        this(sessionId, player, trackPack, 0, initialEnergy, initialToleranceChances,
                initialToleranceRechargeProgress, discId, initialDiscStatistics, clock, skipElapsedBeats);
    }

    public DJSession(long sessionId, Player player, TrackPack trackPack, int initialCombo, double initialEnergy,
            int initialToleranceChances, int initialToleranceRechargeProgress,
            UUID discId, DiscStatistics initialDiscStatistics, DJServerClock clock,
            boolean skipElapsedBeats) {
        this.sessionId = sessionId;
        this.player = player;
        this.trackPack = trackPack;
        this.clock = Objects.requireNonNull(clock);
        this.discId = discId;
        this.initialDiscStatistics = initialDiscStatistics == null ? DiscStatistics.EMPTY : initialDiscStatistics;
        double maxEnergy = getMaxEnergy();
        double startingEnergy = player.isCreative() ? maxEnergy : initialEnergy;
        this.resourceState = new DJSessionResourceState(startingEnergy, maxEnergy, initialToleranceChances,
                initialToleranceRechargeProgress, getMaxToleranceChances());
        this.movementState = new DJMovementAbilityState(getMaxAirJumps());
        if (skipElapsedBeats) {
            this.lastBeatIndex = findElapsedBeatIndex(getCurrentTimeMs());
        }
        this.resourceState.restoreCombo(initialCombo, lastBeatIndex);

        DJCraft.LOGGER.info("DJSession started for {} with track {}",
                player.getName().getString(), trackPack.id());

        updateAttackSpeed(true);
    }

    /**
     * 获取原始播放时间（毫秒，不含 offset 修正）
     */
    private long getRawCurrentTimeMs() {
        return clock.currentTimeMs();
    }

    private long toTimelineTimeMs(long rawTimeMs) {
        long adjusted = rawTimeMs - trackPack.getOffsetMs();
        return Math.max(0, adjusted);
    }

    /**
     * 获取时间轴时间（毫秒，已应用 TrackMeta.offsetMs 修正）
     */
    public long getCurrentTimeMs() {
        return toTimelineTimeMs(getRawCurrentTimeMs());
    }

    /**
     * 设置暂停状态
     */
    public void setPaused(boolean paused) {
        boolean wasPaused = clock.isPaused();
        clock.setPaused(paused);
        if (paused && !wasPaused) {
            DJCraft.LOGGER.debug("DJSession paused");
        } else if (!paused && wasPaused) {
            DJCraft.LOGGER.debug("DJSession resumed");
        }
    }

    /**
     * 是否处于暂停状态
     */
    public boolean isPaused() {
        return clock.isPaused();
    }

    /**
     * 每tick更新，检测节拍并发送事件
     */
    public void tick() {
        if (!playing)
            return;

        long rawCurrentTime = getRawCurrentTimeMs();
        if (trackPack.hasReachedPlaybackEnd(rawCurrentTime)) {
            stop();
            return;
        }

        double maxEnergy = getMaxEnergy();
        int maxToleranceChances = getMaxToleranceChances();
        resourceState.tick(maxEnergy, maxToleranceChances, Config.toleranceRechargeTicks());
        if (player.isCreative()) {
            resourceState.fillEnergy(maxEnergy);
        }
        tickShieldState();
        boolean movementStateChanged = movementState.setMaxAirJumps(getMaxAirJumps());
        if (movementState.resetAirJumpsIfGrounded(player.onGround())) {
            movementStateChanged = true;
        }
        if (movementStateChanged) {
            sendMovementState();
        }

        long currentTime = toTimelineTimeMs(rawCurrentTime);
        if (rawCurrentTime - lastStatisticsWriteMs >= 1_000L) {
            persistDiscStatistics(rawCurrentTime);
        }

        // 检测经过的节拍
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty()) {
            syncResourceStateIfChanged(maxEnergy, maxToleranceChances);
            return;
        }

        // 查找当前应该触发的节拍
        for (int i = lastBeatIndex + 1; i < combatLine.size(); i++) {
            BeatEvent beat = combatLine.get(i);
            if (beat.t() <= currentTime) {
                // 触发节拍事件
                boolean canAttack = fireBeatEvent(beat, i, currentTime);
                lastBeatIndex = i;
                resourceState.onBeat(i, canAttack, getComboGraceBeats());
            } else {
                break;
            }
        }
        syncResourceStateIfChanged(maxEnergy, maxToleranceChances);
    }

    /**
     * 发送节拍事件
     */
    private boolean fireBeatEvent(BeatEvent beat, int index, long currentTime) {
        BeatDefinition definition = definitionFor(beat);

        OnBeatEvent event = new OnBeatEvent(player, beat, definition, currentTime, index);
        NeoForge.EVENT_BUS.post(event);

        // Debug日志
        DJCraft.LOGGER.debug("Beat #{} at t={}ms, type={}", index, beat.t(), beat.type());
        return definition.canAttack();
    }

    private BeatDefinition definitionFor(BeatEvent beat) {
        return trackPack.resolveDefinition(beat);
    }

    /**
     * 获取下一个节拍
     */
    public BeatEvent getNextBeat() {
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty())
            return null;

        int nextIndex = lastBeatIndex + 1;
        if (nextIndex < combatLine.size()) {
            return combatLine.get(nextIndex);
        }
        return null;
    }

    /**
     * 获取上一个节拍
     */
    public BeatEvent getPreviousBeat() {
        List<BeatEvent> combatLine = trackPack.timeline().combatLine();
        if (combatLine == null || combatLine.isEmpty())
            return null;

        if (lastBeatIndex >= 0 && lastBeatIndex < combatLine.size()) {
            return combatLine.get(lastBeatIndex);
        }
        return null;
    }

    /**
     * 获取距下一个节拍的毫秒数
     */
    public long getMsToNextBeat() {
        BeatEvent next = getNextBeat();
        if (next == null)
            return -1;
        return next.t() - getCurrentTimeMs();
    }

    /**
     * 获取距上一个节拍的毫秒数
     */
    public long getMsSincePreviousBeat() {
        BeatEvent prev = getPreviousBeat();
        if (prev == null)
            return -1;
        return getCurrentTimeMs() - prev.t();
    }

    /**
     * 停止播放
     */
    public void stop() {
        if (!this.playing)
            return;
        this.playing = false;
        persistDiscStatistics(getRawCurrentTimeMs());
        shieldState.clear();
        DJCraft.LOGGER.info("DJSession stopped for {}", player.getName().getString());
        updateAttackSpeed(false);
    }

    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * 获取曲目包
     */
    public TrackPack getTrackPack() {
        return trackPack;
    }

    /**
     * 获取玩家
     */
    public Player getPlayer() {
        return player;
    }

    public long getSessionId() {
        return sessionId;
    }

    public int getCombo() {
        return resourceState.getCombo();
    }

    public int getCurrentTrackCombo() {
        return resourceState.getCurrentTrackCombo();
    }

    public double getEnergy() {
        return resourceState.getEnergy();
    }

    public double getMaxEnergy() {
        return player.getAttributeValue(ModAttributes.MAX_ENERGY)
                + (ModItems.BAND_OF_ENERGY.get().isActiveFor(player) ? BandOfEnergyItem.MAX_ENERGY_BONUS : 0.0);
    }

    public void setCombo(int combo) {
        if (resourceState.setCombo(combo, lastBeatIndex)) {
            recordCurrentTrackCombo();
            syncResourceStateIfChanged(getMaxEnergy());
        }
    }

    public void setEnergy(double energy) {
        double maxEnergy = getMaxEnergy();
        if (resourceState.setEnergy(energy, maxEnergy)) {
            syncResourceStateIfChanged(maxEnergy);
        }
    }

    public int getToleranceChances() {
        return resourceState.getToleranceChances();
    }

    public int getMaxToleranceChances() {
        return getMaxToleranceChances(player);
    }

    public int getToleranceRechargeProgress() {
        return resourceState.getToleranceRechargeProgress();
    }

    public boolean tryConsumeEnergy(double amount) {
        double maxEnergy = getMaxEnergy();
        if (player.isCreative()) {
            boolean energyChanged = resourceState.fillEnergy(maxEnergy);
            boolean accepted = resourceState.tryConsumeEnergy(amount);
            if (accepted) {
                resourceState.fillEnergy(maxEnergy);
            }
            if (energyChanged) {
                syncResourceStateIfChanged(maxEnergy);
            }
            return accepted;
        }
        if (!resourceState.tryConsumeEnergy(amount)) {
            return false;
        }
        syncResourceStateIfChanged(maxEnergy);
        return true;
    }

    public void grantEnergy(double amount) {
        if (resourceState.grantEnergy(amount, getMaxEnergy())) {
            syncResourceStateIfChanged(getMaxEnergy());
        }
    }

    public void authorizeShieldStart(InteractionHand hand, Item item, HitResult result,
            long actionSequence, long gameTime) {
        var beats = trackPack.timeline().combatLine();
        long judgedAtMs = result.judgedAtMs();
        shieldState.authorizeStart(hand, item, actionSequence, judgedAtMs,
                BeatGridUtil.calculateTargetTime(judgedAtMs, beats, DJShieldRules.PARRY_DURATION_BEATS),
                result.isHit(), BeatGridUtil.getVirtualBeat(judgedAtMs, beats),
                gameTime + DJShieldRules.START_AUTHORIZATION_TTL_TICKS);
    }

    public boolean beginShieldUse(InteractionHand hand, Item item, long gameTime) {
        var pending = shieldState.takeStartAuthorization(hand, item, gameTime).orElse(null);
        if (pending == null || !tryConsumeEnergy(DJShieldRules.START_ENERGY_COST)) {
            return false;
        }
        shieldState.activate(pending, DJShieldRules.SUSTAIN_INTERVAL_BEATS);
        return true;
    }

    public void stopShieldUse() {
        finishShieldUse();
    }

    public java.util.Optional<DJShieldState.ParryResult> tryShieldParry() {
        return shieldState.tryParry(getCurrentTimeMs());
    }

    public void confirmParry(boolean rewardEnergy) {
        resourceState.confirmParry(lastBeatIndex);
        recordCurrentTrackCombo();
        applyComboRewards();
        if (rewardEnergy) {
            resourceState.grantEnergy(DJShieldRules.PARRY_ENERGY_REWARD, getMaxEnergy());
        }
        syncResourceStateIfChanged(getMaxEnergy());
    }

    public long getShieldParryExpiresAtMs() {
        return shieldState.activeParryExpiresAtMs();
    }

    private void tickShieldState() {
        if (!shieldState.hasActiveShield()) {
            return;
        }
        if (!player.isUsingItem()
                || !shieldState.isActiveFor(player.getUsedItemHand(), player.getUseItem().getItem())) {
            finishShieldUse();
            return;
        }

        double currentVirtualBeat = BeatGridUtil.getVirtualBeat(getCurrentTimeMs(),
                trackPack.timeline().combatLine());
        while (shieldState.isSustainChargeDue(currentVirtualBeat)) {
            if (!tryConsumeEnergy(DJShieldRules.SUSTAIN_ENERGY_COST)) {
                finishShieldUse();
                player.stopUsingItem();
                return;
            }
            shieldState.recordSustainCharge(DJShieldRules.SUSTAIN_INTERVAL_BEATS);
        }
    }

    private void finishShieldUse() {
        shieldState.finishActiveShield().ifPresent(result -> {
            if (!result.applyMissedParryCooldown()) {
                return;
            }
            int ticks = BeatGridUtil.getDurationTicks(getCurrentTimeMs(),
                    trackPack.timeline().combatLine(),
                    DJItemCooldownManager.getUseBeatCooldown(new ItemStack(result.item())));
            if (ticks > 0) {
                player.getCooldowns().addCooldown(result.item(), ticks);
            }
        });
    }

    public DJMovementAbilityState getMovementState() {
        return movementState;
    }

    private int getMaxAirJumps() {
        return Config.MAX_AIR_JUMPS.get()
                + (ModItems.NOTE_IN_A_BOTTLE.get().isActiveFor(player) ? NoteInABottleItem.AIR_JUMP_BONUS : 0)
                + ModEnchantments.level(player.level().registryAccess(),
                        player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),
                        ModEnchantments.AERIAL_STEP);
    }

    public void sendMovementState() {
        if (player instanceof ServerPlayer serverPlayer) {
            long gameTime = player.level().getGameTime();
            PacketDistributor.sendToPlayer(serverPlayer, new DJMovementStatePayload(sessionId,
                    movementState.dashCooldownTicks(gameTime), movementState.consecutiveDashes(),
                    movementState.remainingAirJumps()));
        }
    }

    public void confirmComboHit(long actionSequence) {
        if (resourceState.confirmHit(actionSequence, lastBeatIndex, getMaxEnergy())) {
            recordCurrentTrackCombo();
            applyComboRewards();
            syncResourceStateIfChanged(getMaxEnergy());
        }
    }

    public void confirmProjectileDamage(long actionSequence) {
        if (!resourceState.confirmProjectileDamage(actionSequence, lastBeatIndex, getMaxEnergy())) {
            return;
        }
        recordCurrentTrackCombo();
        applyComboRewards();
        syncResourceStateIfChanged(getMaxEnergy());
    }

    public void ignoreCurrentBeatForComboReset() {
        resourceState.ignoreBeatForComboReset(lastBeatIndex);
    }

    private void applyComboRewards() {
        int combo = resourceState.getCombo();
        if (DJComboHungerRules.shouldReward(combo)) {
            DJComboHungerRules.reward(player);
        }
        if (DJComboAbsorptionRules.shouldReward(combo)) {
            DJComboAbsorptionRules.reward(player);
        }
    }

    public void recordJudgmentMiss(long actionSequence) {
        if (resourceState.judgmentFailed(actionSequence, getMaxToleranceChances())) {
            syncResourceStateIfChanged(getMaxEnergy());
        }
    }

    public void sendResourceState() {
        lastSyncedCombo = Integer.MIN_VALUE;
        lastSyncedCurrentTrackCombo = Integer.MIN_VALUE;
        lastSyncedToleranceChances = Integer.MIN_VALUE;
        lastSyncedOffBeatAttackDamagePercent = Integer.MIN_VALUE;
        syncResourceStateIfChanged(getMaxEnergy());
    }

    private void syncResourceStateIfChanged(double maxEnergy) {
        syncResourceStateIfChanged(maxEnergy, getMaxToleranceChances());
    }

    private static int getMaxToleranceChances(Player player) {
        int bonus = ModItems.DJFUMO.get().isActiveFor(player) ? DJFumoItem.TOLERANCE_CHANCE_BONUS : 0;
        return ModGameRules.maxToleranceChances(player.level().getGameRules(), bonus);
    }

    private int getComboGraceBeats() {
        int bonus = ModItems.DJFUMO.get().isActiveFor(player) ? DJFumoItem.COMBO_GRACE_BEATS_BONUS : 0;
        return ModGameRules.idleAttackableBeatsBeforeComboReset(player.level().getGameRules(), bonus);
    }

    public int getOffBeatAttackDamagePercent() {
        return ModGameRules.offBeatAttackDamagePercent(player.level().getGameRules());
    }

    private void syncResourceStateIfChanged(double maxEnergy, int maxToleranceChances) {
        int combo = resourceState.getCombo();
        int currentTrackCombo = resourceState.getCurrentTrackCombo();
        double energy = resourceState.getEnergy();
        int toleranceChances = resourceState.getToleranceChances();
        int offBeatAttackDamagePercent = getOffBeatAttackDamagePercent();
        if (combo == lastSyncedCombo
                && currentTrackCombo == lastSyncedCurrentTrackCombo
                && Double.compare(energy, lastSyncedEnergy) == 0
                && Double.compare(maxEnergy, lastSyncedMaxEnergy) == 0
                && toleranceChances == lastSyncedToleranceChances
                && maxToleranceChances == lastSyncedMaxToleranceChances
                && offBeatAttackDamagePercent == lastSyncedOffBeatAttackDamagePercent) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new DJSessionStatePayload(sessionId, combo, currentTrackCombo, energy, maxEnergy,
                            toleranceChances, maxToleranceChances, offBeatAttackDamagePercent));
            lastSyncedCombo = combo;
            lastSyncedCurrentTrackCombo = currentTrackCombo;
            lastSyncedEnergy = energy;
            lastSyncedMaxEnergy = maxEnergy;
            lastSyncedToleranceChances = toleranceChances;
            lastSyncedMaxToleranceChances = maxToleranceChances;
            lastSyncedOffBeatAttackDamagePercent = offBeatAttackDamagePercent;
        }
    }

    public void synchronizeClientClock(long clientTimeMs) {
        validationState.synchronizeClock(getCurrentTimeMs(), clientTimeMs);
        statisticsClockOffsetMs = clientTimeMs - getRawCurrentTimeMs();
        if (statisticsPlaybackStartMs == null) {
            statisticsPlaybackStartMs = clientTimeMs;
        }
    }

    /**
     * Re-aligns the authoritative timeline to the audio clock of a local client.
     * This is only called by the server handler for singleplayer sessions, where
     * opening the pause menu freezes OpenAL and the integrated server together.
     */
    public void alignToLocalClientClock(long clientTimeMs) {
        long serverTimeMs = getCurrentTimeMs();
        long correctionMs = serverTimeMs > clientTimeMs ? serverTimeMs - clientTimeMs : 0L;
        clock.alignBackward(correctionMs);
        DJCraft.LOGGER.debug("Realigned singleplayer DJ clock by {}ms", correctionMs);
    }

    private int findElapsedBeatIndex(long timelineTimeMs) {
        List<BeatEvent> beats = trackPack.timeline().combatLine();
        int low = 0;
        int high = beats == null ? 0 : beats.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (beats.get(mid).t() <= timelineTimeMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low - 1;
    }

    public boolean acceptActionSequence(long sequence) {
        return validationState.acceptActionSequence(sequence);
    }

    public DJSessionValidationState.ClockAudit auditClientClock(long clientTimeMs, int pingMs) {
        return validationState.auditClock(getCurrentTimeMs(), clientTimeMs, pingMs);
    }

    /**
     * 获取已触发的节拍数量
     */
    public int getTriggeredBeatCount() {
        return lastBeatIndex + 1;
    }

    /**
     * 获取最后一个已触发节拍的索引（用于判定器）
     */
    public int getLastBeatIndex() {
        return lastBeatIndex;
    }

    /**
     * 获取总节拍数量
     */
    public int getTotalBeatCount() {
        return trackPack.getCombatBeatCount();
    }

    public UUID getDiscId() {
        return discId;
    }

    public int getSessionMaxCombo() {
        return currentTrackMaxCombo;
    }

    public int getCurrentTrackMaxCombo() {
        return currentTrackMaxCombo;
    }

    private void recordCurrentTrackCombo() {
        currentTrackMaxCombo = Math.max(currentTrackMaxCombo, resourceState.getCurrentTrackCombo());
    }

    private void persistDiscStatistics(long rawCurrentTimeMs) {
        if (discId == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long playbackPositionMs = statisticsClockOffsetMs == null
                ? 0L : Math.max(0L, rawCurrentTimeMs + statisticsClockOffsetMs);
        long playbackStartMs = statisticsPlaybackStartMs == null
                ? playbackPositionMs : statisticsPlaybackStartMs;
        long elapsedPlaybackMs = Math.max(0L, playbackPositionMs - playbackStartMs);
        long maximumPlayableDurationMs = Math.max(0L,
                (long) trackPack.getTotalDurationMs() - trackPack.getPlaybackStartMs());
        long duration = Math.min(elapsedPlaybackMs, maximumPlayableDurationMs);
        DiscStatistics candidate = new DiscStatistics(
                Math.max(initialDiscStatistics.maxCombo(), currentTrackMaxCombo),
                saturatedAdd(initialDiscStatistics.totalPlayTimeMs(), duration));
        DiscStatisticsService.write(serverPlayer, discId, candidate);
        lastStatisticsWriteMs = rawCurrentTimeMs;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + Math.max(0L, right);
    }

    /**
     * 计算经过指定节拍数后的时间戳（相对于曲目开始）
     */
    public long getCompletionTimeMs(double beats) {
        return otto.djgun.djcraft.util.BeatGridUtil.calculateTargetTime(getCurrentTimeMs(),
                trackPack.timeline().combatLine(), beats);
    }

    // 定义高攻速修饰符ID
    private static final net.minecraft.resources.ResourceLocation DJ_ATTACK_SPEED_ID = net.minecraft.resources.ResourceLocation
            .fromNamespaceAndPath(DJCraft.MODID, "dj_mode_speed");

    // 应用/移除攻速修饰符
    private void updateAttackSpeed(boolean enable) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = player
                .getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);

        if (instance != null) {
            if (enable) {
                if (!instance.hasModifier(DJ_ATTACK_SPEED_ID)) {
                    instance.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            DJ_ATTACK_SPEED_ID, 100.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                instance.removeModifier(DJ_ATTACK_SPEED_ID);
            }
        }
    }
}
