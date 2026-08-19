package otto.djgun.djcraft.cybergrind;

final class CyberGrindRunRules {
    private CyberGrindRunRules() {
    }

    static boolean canAdvance(boolean warningsComplete, long gameTime, long earliestAdvanceTick,
            int livingCost, int advanceThreshold) {
        return warningsComplete && gameTime >= earliestAdvanceTick && livingCost < advanceThreshold;
    }

    static boolean shouldEliminateForLocation(boolean inCyberDimension, boolean insideArenaBounds) {
        return !inCyberDimension;
    }
}
