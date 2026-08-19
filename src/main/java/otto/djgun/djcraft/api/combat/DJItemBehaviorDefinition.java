package otto.djgun.djcraft.api.combat;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import otto.djgun.djcraft.combat.DJItemBehavior;

/** Canonical registered behavior; non-item built-ins use a predicate that rejects every item. */
public final class DJItemBehaviorDefinition {
    private final ResourceLocation id;
    private final DJItemBehavior family;
    private final Predicate<Item> supports;
    private final boolean disabledByCanAttack;
    private final DJTriggerBehaviorExecutor triggerExecutor;
    private final DJMeleeBehavior meleeBehavior;

    DJItemBehaviorDefinition(ResourceLocation id, DJItemBehavior family, Predicate<Item> supports,
            boolean disabledByCanAttack, DJTriggerBehaviorExecutor triggerExecutor,
            DJMeleeBehavior meleeBehavior) {
        this.id = Objects.requireNonNull(id, "id");
        this.family = Objects.requireNonNull(family, "family");
        this.supports = Objects.requireNonNull(supports, "supports");
        this.disabledByCanAttack = disabledByCanAttack;
        if (triggerExecutor != null && !family.isTrigger()) {
            throw new IllegalArgumentException("A trigger executor requires a trigger behavior family");
        }
        this.triggerExecutor = triggerExecutor;
        this.meleeBehavior = meleeBehavior;
    }

    public ResourceLocation id() {
        return id;
    }

    /** Selects the DJCraft-owned input, proof and lifecycle path reused by this behavior. */
    public DJItemBehavior family() {
        return family;
    }

    public boolean supports(Item item) {
        if (item == null) {
            return false;
        }
        try {
            return supports.test(item);
        } catch (RuntimeException exception) {
            otto.djgun.djcraft.DJCraft.LOGGER.error(
                    "DJ item behavior {} compatibility predicate failed for {}",
                    id, BuiltInRegistries.ITEM.getKey(item), exception);
            return false;
        }
    }

    /** True when a beat definition with can_attack=false must force this behavior to miss. */
    public boolean disabledByCanAttack() {
        return disabledByCanAttack;
    }

    /** Present only when the addon replaces the standard trigger-family ItemStack#use execution. */
    public Optional<DJTriggerBehaviorExecutor> triggerExecutor() {
        return Optional.ofNullable(triggerExecutor);
    }

    /** Present when this registered item behavior owns a specialized left-click executor. */
    public Optional<DJMeleeBehavior> meleeBehavior() {
        return Optional.ofNullable(meleeBehavior);
    }

    public boolean isCharge() {
        return family.isCharge();
    }

    public boolean isTrigger() {
        return family.isTrigger();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
