package otto.djgun.djcraft.api.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;

/** Common, frozen registry of built-in DJ action and addon-defined item behavior IDs. */
public final class DJItemBehaviorRegistry {
    private static final Map<ResourceLocation, DJItemBehaviorDefinition> MUTABLE = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, DJItemBehaviorDefinition> snapshot;
    private static boolean frozen;

    public static final DJItemBehaviorDefinition NONE = registerBuiltIn(DJItemBehavior.NONE, true);
    public static final DJItemBehaviorDefinition BOW = registerBuiltIn(DJItemBehavior.BOW, true);
    public static final DJItemBehaviorDefinition CROSSBOW = registerBuiltIn(DJItemBehavior.CROSSBOW, true);
    public static final DJItemBehaviorDefinition SHIELD = registerBuiltIn(DJItemBehavior.SHIELD, false);
    public static final DJItemBehaviorDefinition CHARGE = registerBuiltIn(DJItemBehavior.CHARGE, true);
    public static final DJItemBehaviorDefinition TRIGGER = registerBuiltIn(DJItemBehavior.TRIGGER, true);
    public static final DJItemBehaviorDefinition TRIDENT = registerBuiltIn(DJItemBehavior.TRIDENT, true,
            new DJAreaMeleeBehavior(5.0, 1.0));
    public static final DJItemBehaviorDefinition MACE = registerBuiltIn(DJItemBehavior.MACE, true,
            new DJAreaMeleeBehavior(2.0, 2.0, true, true));
    public static final DJItemBehaviorDefinition MELEE = registerNonItemBuiltIn("melee", true,
            DJSoftTargetMeleeBehavior.defaults());
    public static final DJItemBehaviorDefinition MINING = registerNonItemBuiltIn("mining", false);
    public static final DJItemBehaviorDefinition EATING = registerNonItemBuiltIn("eating", false);
    public static final DJItemBehaviorDefinition DASH = registerNonItemBuiltIn("dash", false);
    public static final DJItemBehaviorDefinition GROUND_JUMP = registerNonItemBuiltIn("ground_jump", false);
    public static final DJItemBehaviorDefinition DOUBLE_JUMP = registerNonItemBuiltIn("double_jump", false);
    public static final DJItemBehaviorDefinition GROUND_SLAM = registerNonItemBuiltIn("ground_slam", false);

    static {
        snapshot = Map.copyOf(MUTABLE);
    }

    private DJItemBehaviorRegistry() {
    }

    static synchronized DJItemBehaviorDefinition register(ResourceLocation id, DJItemBehavior family,
            Predicate<Item> supports, boolean disabledByCanAttack,
            DJTriggerBehaviorExecutor triggerExecutor, DJMeleeBehavior meleeBehavior) {
        if (frozen) {
            throw new IllegalStateException("DJ item behavior registry is already frozen");
        }
        Objects.requireNonNull(id, "id");
        if (MUTABLE.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate DJ item behavior: " + id);
        }
        DJItemBehaviorDefinition definition = new DJItemBehaviorDefinition(
                id, family, supports, disabledByCanAttack, triggerExecutor,
                meleeBehavior != null ? meleeBehavior : inheritedMeleeBehavior(family));
        MUTABLE.put(id, definition);
        snapshot = Map.copyOf(MUTABLE);
        return definition;
    }

    public static Optional<DJItemBehaviorDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(snapshot.get(id));
    }

    public static DJItemBehaviorDefinition require(ResourceLocation id) {
        DJItemBehaviorDefinition definition = snapshot.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown DJ item behavior: " + id);
        }
        return definition;
    }

    public static Map<ResourceLocation, DJItemBehaviorDefinition> values() {
        return snapshot;
    }

    /** Resolves the server-authored datapack assignment or the Java inheritance fallback. */
    public static DJItemBehaviorDefinition resolve(ItemStack stack) {
        return DJItemBehaviorManager.resolveDefinition(stack);
    }

    /** Resolves the server-authored datapack assignment or the Java inheritance fallback. */
    public static DJItemBehaviorDefinition resolve(Item item) {
        return DJItemBehaviorManager.resolveDefinition(item);
    }

    public static DJItemBehaviorDefinition inherited(Item item) {
        return require(DJItemBehavior.inherited(item).id());
    }

    /** Lifecycle-owned freeze called by DJCraft after the registration event returns. */
    public static synchronized void freeze() {
        frozen = true;
        snapshot = Map.copyOf(MUTABLE);
    }

    public static boolean isFrozen() {
        return frozen;
    }

    private static DJItemBehaviorDefinition registerBuiltIn(DJItemBehavior family, boolean disabledByCanAttack) {
        return registerBuiltIn(family, disabledByCanAttack, null);
    }

    private static DJItemBehaviorDefinition registerNonItemBuiltIn(String path, boolean disabledByCanAttack) {
        return registerNonItemBuiltIn(path, disabledByCanAttack, null);
    }

    private static DJItemBehaviorDefinition registerBuiltIn(DJItemBehavior family, boolean disabledByCanAttack,
            DJMeleeBehavior meleeBehavior) {
        return register(family.id(), family, family::supports, disabledByCanAttack, null, meleeBehavior);
    }

    private static DJItemBehaviorDefinition registerNonItemBuiltIn(String path, boolean disabledByCanAttack,
            DJMeleeBehavior meleeBehavior) {
        return register(ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, path), DJItemBehavior.NONE,
                item -> false, disabledByCanAttack, null, meleeBehavior);
    }

    private static DJMeleeBehavior inheritedMeleeBehavior(DJItemBehavior family) {
        return switch (family) {
            case TRIDENT -> new DJAreaMeleeBehavior(5.0, 1.0);
            case MACE -> new DJAreaMeleeBehavior(2.0, 2.0, true, true);
            default -> null;
        };
    }
}
