package otto.djgun.djcraft.api.combat;

import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;
import otto.djgun.djcraft.combat.DJItemBehavior;

/** Fired once on the NeoForge game bus during common setup; registrations must be side-safe. */
public final class RegisterDJItemBehaviorsEvent extends Event {
    /** Registers a new behavior ID that reuses one of DJCraft's standard action families. */
    public DJItemBehaviorDefinition register(ResourceLocation id, DJItemBehavior family,
            Predicate<Item> supports) {
        return register(id, family, supports, true);
    }

    /** Registers a behavior and controls whether can_attack=false disables its judgments. */
    public DJItemBehaviorDefinition register(ResourceLocation id, DJItemBehavior family,
            Predicate<Item> supports, boolean disabledByCanAttack) {
        return DJItemBehaviorRegistry.register(id, family, supports, disabledByCanAttack, null, null);
    }

    /** Registers a server-authoritative capsule left-click behavior. */
    public DJItemBehaviorDefinition registerAreaMelee(ResourceLocation id, DJItemBehavior family,
            Predicate<Item> supports, DJAreaMeleeBehavior meleeBehavior) {
        return registerAreaMelee(id, family, supports, true, meleeBehavior);
    }

    /** Registers a capsule left-click behavior and its can_attack gate policy. */
    public DJItemBehaviorDefinition registerAreaMelee(ResourceLocation id, DJItemBehavior family,
            Predicate<Item> supports, boolean disabledByCanAttack, DJAreaMeleeBehavior meleeBehavior) {
        return DJItemBehaviorRegistry.register(id, family, supports, disabledByCanAttack, null,
                java.util.Objects.requireNonNull(meleeBehavior, "meleeBehavior"));
    }

    /** Registers a trigger-family behavior with addon-owned authoritative execution. */
    public DJItemBehaviorDefinition registerTrigger(ResourceLocation id, Predicate<Item> supports,
            DJTriggerBehaviorExecutor executor) {
        return registerTrigger(id, supports, true, executor);
    }

    /** Registers an addon-executed trigger behavior and its can_attack gate policy. */
    public DJItemBehaviorDefinition registerTrigger(ResourceLocation id, Predicate<Item> supports,
            boolean disabledByCanAttack, DJTriggerBehaviorExecutor executor) {
        return DJItemBehaviorRegistry.register(id, DJItemBehavior.TRIGGER, supports,
                disabledByCanAttack, executor, null);
    }
}
