package otto.djgun.djcraft.client.animation;

import java.util.Map;

/** Thread-safe snapshot of curves supplied by the latest client resource reload. */
final class DJAnimationClips {
    static final String EQUIP = "animation.djcraft.first_person.equip";
    static final String UNEQUIP = "animation.djcraft.first_person.unequip";
    static final String MELEE_STRIKE = "animation.djcraft.first_person.melee_strike";
    static final String MELEE_THRUST = "animation.djcraft.first_person.melee_thrust";
    static final String TRIDENT_THRUST = "animation.djcraft.first_person.trident_thrust";
    static final String MELEE_SWEEP = "animation.djcraft.first_person.melee_sweep";
    static final String MELEE_CRITICAL = "animation.djcraft.first_person.melee_critical";
    static final String PARRY = "animation.djcraft.first_person.parry";
    static final String USE = "animation.djcraft.first_person.use";

    private static volatile Map<String, DJAnimationCurve> curves = Map.of();

    private DJAnimationClips() {
    }

    static DJAnimationCurve curve(String id) {
        return curves.get(id);
    }

    static void install(Map<String, DJAnimationCurve> loaded) {
        curves = Map.copyOf(loaded);
    }
}
