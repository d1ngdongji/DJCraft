package otto.djgun.djcraft.combat.client;

import otto.djgun.djcraft.init.ModGameRules;

/** Pure client snapshot of the server-authoritative DJ combo and energy state. */
public final class DJClientSessionResourceState {
    private final long sessionId;
    private int combo;
    private double energy;
    private double maxEnergy;
    private int toleranceChances;
    private int maxToleranceChances;

    public DJClientSessionResourceState(long sessionId, double defaultMaxEnergy) {
        this.sessionId = sessionId;
        this.maxEnergy = Math.max(0.0, defaultMaxEnergy);
        this.toleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
        this.maxToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
    }

    public boolean apply(long stateSessionId, int combo, double energy, double maxEnergy,
            int toleranceChances, int maxToleranceChances) {
        if (stateSessionId != sessionId) {
            return false;
        }
        this.combo = Math.max(0, combo);
        this.maxEnergy = Double.isFinite(maxEnergy) ? Math.max(0.0, maxEnergy) : 0.0;
        this.energy = Double.isFinite(energy) ? Math.clamp(energy, 0.0, this.maxEnergy) : 0.0;
        this.maxToleranceChances = Math.max(0, maxToleranceChances);
        this.toleranceChances = Math.clamp(toleranceChances, 0, this.maxToleranceChances);
        return true;
    }

    public boolean apply(long stateSessionId, int combo, double energy, double maxEnergy) {
        return apply(stateSessionId, combo, energy, maxEnergy,
                ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES,
                ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES);
    }

    public void predictMiss() {
        if (toleranceChances > 0) {
            toleranceChances--;
        } else {
            combo = 0;
        }
    }

    public void reset(double defaultMaxEnergy) {
        combo = 0;
        energy = 0.0;
        maxEnergy = Math.max(0.0, defaultMaxEnergy);
        toleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
        maxToleranceChances = ModGameRules.DEFAULT_BASE_MAX_TOLERANCE_CHANCES;
    }

    public int getCombo() {
        return combo;
    }

    public double getEnergy() {
        return energy;
    }

    public double getMaxEnergy() {
        return maxEnergy;
    }

    public int getToleranceChances() {
        return toleranceChances;
    }

    public int getMaxToleranceChances() {
        return maxToleranceChances;
    }
}
