package otto.djgun.djcraft.api.combat;

/** Immutable server-authoritative targeting strategy for one DJ melee action. */
public sealed interface DJMeleeBehavior permits DJSoftTargetMeleeBehavior, DJAreaMeleeBehavior {
    boolean area();
}
