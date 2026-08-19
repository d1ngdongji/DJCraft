package otto.djgun.djcraft.session;

import java.util.HashSet;
import java.util.Set;

import otto.djgun.djcraft.Config;
import otto.djgun.djcraft.init.ModGameRules;

/** Pure server-side DJ combo and energy bookkeeping. */
public final class DJSessionResourceState {
    private final Set<Long> countedSequences = new HashSet<>();
    private int combo;
    private int currentTrackCombo;
    private int lastHitBeatIndex = -1;
    private int ignoredIdleBeatCount;
    private int lastIgnoredIdleBeatIndex = -1;
    private long ignoreThroughSequence;
    private long elapsedTicks;
    private double energy;
    private int toleranceChances;
    private int toleranceRechargeProgress;

    public DJSessionResourceState() {
        this(0.0, 0.0, ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES, 0,
                ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES);
    }

    public DJSessionResourceState(double initialEnergy, double maxEnergy) {
        this(initialEnergy, maxEnergy, ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES, 0,
                ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES);
    }

    public DJSessionResourceState(double initialEnergy, double maxEnergy, int initialToleranceChances,
            int initialToleranceRechargeProgress, int maxToleranceChances) {
        energy = Double.isFinite(initialEnergy)
                ? Math.clamp(initialEnergy, 0.0, sanitizeMax(maxEnergy))
                : 0.0;
        int sanitizedMax = sanitizeToleranceMax(maxToleranceChances);
        toleranceChances = Math.clamp(initialToleranceChances, 0, sanitizedMax);
        toleranceRechargeProgress = toleranceChances < sanitizedMax
                ? Math.max(0, initialToleranceRechargeProgress)
                : 0;
    }

    public boolean confirmHit(long actionSequence, int currentBeatIndex, double maxEnergy) {
        if (!confirmCombo(actionSequence, currentBeatIndex)) {
            return false;
        }
        grantEnergy(combo >= 10 ? 2.0 : 1.0, maxEnergy);
        return true;
    }

    public boolean confirmProjectileDamage(long actionSequence, int currentBeatIndex, double maxEnergy) {
        if (actionSequence <= ignoreThroughSequence) {
            return false;
        }
        incrementCombo(currentBeatIndex);
        if (countedSequences.add(actionSequence)) {
            grantEnergy(combo >= 10 ? 2.0 : 1.0, maxEnergy);
        }
        return true;
    }

    public boolean confirmCombo(long actionSequence, int currentBeatIndex) {
        if (actionSequence <= ignoreThroughSequence || !countedSequences.add(actionSequence)) {
            return false;
        }
        incrementCombo(currentBeatIndex);
        return true;
    }

    public void confirmParry(int currentBeatIndex) {
        incrementCombo(currentBeatIndex);
    }

    private void incrementCombo(int currentBeatIndex) {
        if (combo < Integer.MAX_VALUE) {
            combo++;
        }
        if (currentTrackCombo < Integer.MAX_VALUE) {
            currentTrackCombo++;
        }
        lastHitBeatIndex = currentBeatIndex;
        ignoredIdleBeatCount = 0;
        lastIgnoredIdleBeatIndex = -1;
    }

    public void ignoreBeatForComboReset(int beatIndex) {
        if (combo > 0 && lastHitBeatIndex >= 0 && beatIndex > lastHitBeatIndex
                && beatIndex > lastIgnoredIdleBeatIndex) {
            if (ignoredIdleBeatCount < Integer.MAX_VALUE) {
                ignoredIdleBeatCount++;
            }
            lastIgnoredIdleBeatIndex = beatIndex;
        }
    }

    public boolean judgmentFailed(long actionSequence, int maxToleranceChances) {
        if (actionSequence <= ignoreThroughSequence) {
            return false;
        }
        ignoreThroughSequence = Math.max(ignoreThroughSequence, actionSequence);
        clampTolerance(maxToleranceChances);
        if (toleranceChances > 0) {
            toleranceChances--;
            toleranceRechargeProgress = 0;
            return true;
        }
        return resetCombo();
    }

    public boolean judgmentFailed(long actionSequence) {
        return judgmentFailed(actionSequence, ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES);
    }

    public boolean onBeat(int beatIndex, int idleBeatsBeforeReset) {
        return onBeat(beatIndex, true, idleBeatsBeforeReset);
    }

    public boolean onBeat(int beatIndex, boolean countsTowardComboReset, int idleBeatsBeforeReset) {
        if (!countsTowardComboReset && combo > 0 && lastHitBeatIndex >= 0 && beatIndex > lastHitBeatIndex) {
            ignoreBeatForComboReset(beatIndex);
            return false;
        }
        if (combo > 0 && lastHitBeatIndex >= 0
                && (long) beatIndex - lastHitBeatIndex - ignoredIdleBeatCount
                        >= Math.max(1, idleBeatsBeforeReset)) {
            return resetCombo();
        }
        return false;
    }

    public boolean onBeat(int beatIndex) {
        return onBeat(beatIndex, ModGameRules.DEFAULT_IDLE_ATTACKABLE_BEATS_BEFORE_COMBO_RESET);
    }

    public boolean tick(double maxEnergy, int maxToleranceChances, int toleranceRechargeTicks) {
        elapsedTicks++;
        double oldEnergy = energy;
        int oldToleranceChances = toleranceChances;
        int oldToleranceRechargeProgress = toleranceRechargeProgress;
        clampEnergy(maxEnergy);
        int interval = combo >= 5 ? 5 : 10;
        if (elapsedTicks % interval == 0) {
            grantEnergy(1.0, maxEnergy);
        }
        tickTolerance(maxToleranceChances, toleranceRechargeTicks);
        return Double.compare(oldEnergy, energy) != 0
                || oldToleranceChances != toleranceChances
                || oldToleranceRechargeProgress != toleranceRechargeProgress;
    }

    public boolean tick(double maxEnergy) {
        return tick(maxEnergy, ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES,
                Config.DEFAULT_TOLERANCE_RECHARGE_TICKS);
    }

    public int getCombo() {
        return combo;
    }

    public int getCurrentTrackCombo() {
        return currentTrackCombo;
    }

    void restoreCombo(int value, int currentBeatIndex) {
        combo = Math.max(0, value);
        currentTrackCombo = 0;
        lastHitBeatIndex = combo == 0 ? -1 : Math.max(0, currentBeatIndex);
        ignoredIdleBeatCount = 0;
        lastIgnoredIdleBeatIndex = -1;
    }

    public double getEnergy() {
        return energy;
    }

    public boolean setCombo(int value, int currentBeatIndex) {
        int sanitized = Math.max(0, value);
        if (combo == sanitized && currentTrackCombo == sanitized) {
            return false;
        }
        combo = sanitized;
        currentTrackCombo = sanitized;
        lastHitBeatIndex = combo == 0 ? -1 : Math.max(0, currentBeatIndex);
        ignoredIdleBeatCount = 0;
        lastIgnoredIdleBeatIndex = -1;
        return true;
    }

    public boolean setEnergy(double value, double maxEnergy) {
        double sanitized = Double.isFinite(value)
                ? Math.clamp(value, 0.0, sanitizeMax(maxEnergy))
                : 0.0;
        if (Double.compare(energy, sanitized) == 0) {
            return false;
        }
        energy = sanitized;
        return true;
    }

    public int getToleranceChances() {
        return toleranceChances;
    }

    public int getToleranceRechargeProgress() {
        return toleranceRechargeProgress;
    }

    public boolean tryConsumeEnergy(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0 || energy < amount) {
            return false;
        }
        energy -= amount;
        return true;
    }

    public boolean grantEnergy(double amount, double maxEnergy) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            return false;
        }
        double oldEnergy = energy;
        energy = Math.min(sanitizeMax(maxEnergy), energy + amount);
        return Double.compare(oldEnergy, energy) != 0;
    }

    public boolean fillEnergy(double maxEnergy) {
        double oldEnergy = energy;
        energy = sanitizeMax(maxEnergy);
        return Double.compare(oldEnergy, energy) != 0;
    }

    private boolean resetCombo() {
        if (combo == 0) {
            return false;
        }
        combo = 0;
        currentTrackCombo = 0;
        lastHitBeatIndex = -1;
        ignoredIdleBeatCount = 0;
        lastIgnoredIdleBeatIndex = -1;
        return true;
    }

    private void clampEnergy(double maxEnergy) {
        energy = Math.min(energy, sanitizeMax(maxEnergy));
    }

    private void tickTolerance(int maxToleranceChances, int rechargeTicks) {
        int sanitizedMax = sanitizeToleranceMax(maxToleranceChances);
        clampTolerance(sanitizedMax);
        if (toleranceChances >= sanitizedMax) {
            toleranceRechargeProgress = 0;
            return;
        }

        toleranceRechargeProgress++;
        if (toleranceRechargeProgress >= Math.max(1, rechargeTicks)) {
            toleranceChances++;
            toleranceRechargeProgress = 0;
        }
    }

    private void clampTolerance(int maxToleranceChances) {
        int sanitizedMax = sanitizeToleranceMax(maxToleranceChances);
        toleranceChances = Math.min(toleranceChances, sanitizedMax);
        if (toleranceChances >= sanitizedMax) {
            toleranceRechargeProgress = 0;
        }
    }

    private static double sanitizeMax(double maxEnergy) {
        return Double.isFinite(maxEnergy) ? Math.max(0.0, maxEnergy) : 0.0;
    }

    private static int sanitizeToleranceMax(int maxToleranceChances) {
        return Math.max(0, maxToleranceChances);
    }
}
