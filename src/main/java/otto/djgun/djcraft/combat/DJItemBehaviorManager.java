package otto.djgun.djcraft.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.api.combat.DJItemBehaviorDefinition;
import otto.djgun.djcraft.api.combat.DJItemBehaviorRegistry;
import otto.djgun.djcraft.api.combat.DJMeleeBehavior;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves immutable, server-authored item behavior overrides with inheritance fallback. */
public final class DJItemBehaviorManager {
    private static final Comparator<DJItemBehaviorProfile> PROFILE_ORDER =
            Comparator.comparingInt(DJItemBehaviorProfile::priority).reversed()
                    .thenComparing(profile -> profile.id().toString());
    private static volatile Snapshot snapshot = Snapshot.empty();

    private DJItemBehaviorManager() {
    }

    public static DJItemBehavior resolve(ItemStack stack) {
        return resolveDefinition(stack).family();
    }

    public static DJItemBehavior resolve(Item item) {
        return resolveDefinition(item).family();
    }

    public static DJItemBehaviorDefinition resolveDefinition(ItemStack stack) {
        return stack.isEmpty() ? DJItemBehaviorRegistry.NONE : resolveDefinition(stack.getItem());
    }

    public static DJItemBehaviorDefinition resolveDefinition(Item item) {
        DJItemBehaviorDefinition explicit = snapshot.byItem().get(item);
        return explicit != null ? explicit : DJItemBehaviorRegistry.inherited(item);
    }

    /** Resolves the immutable melee strategy and any server datapack override for this item. */
    public static DJMeleeBehavior resolveMeleeBehavior(ItemStack stack) {
        if (stack.isEmpty()) {
            return DJItemBehaviorRegistry.MELEE.meleeBehavior().orElseThrow();
        }
        DJMeleeBehavior explicit = snapshot.meleeByItem().get(stack.getItem());
        if (explicit != null) {
            return explicit;
        }
        return resolveDefinition(stack).meleeBehavior()
                .orElseGet(() -> DJItemBehaviorRegistry.MELEE.meleeBehavior().orElseThrow());
    }

    public static boolean is(ItemStack stack, DJItemBehavior expected) {
        return resolve(stack) == expected;
    }

    public static void replaceProfiles(Collection<DJItemBehaviorProfile> profiles) {
        List<DJItemBehaviorProfile> ordered = profiles.stream().sorted(PROFILE_ORDER).toList();
        Map<ResourceLocation, ResourceLocation> byId = new LinkedHashMap<>();
        Map<Item, DJItemBehaviorDefinition> byItem = new LinkedHashMap<>();
        Map<Item, DJMeleeBehavior> meleeByItem = new LinkedHashMap<>();

        ordered.forEach(profile -> profile.items().forEach(itemId -> {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                DJCraft.LOGGER.error("Ignoring unknown item {} in DJ item behavior profile {}",
                        itemId, profile.id());
            }
        }));
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            ResourceLocation itemId = entry.getKey().location();
            Item item = entry.getValue();
            DJItemBehaviorProfile selected = select(itemId, item, ordered, true);
            if (selected == null) {
                selected = select(itemId, item, ordered, false);
            }
            if (selected != null) {
                byId.put(itemId, selected.behavior().id());
                byItem.put(item, selected.behavior());
                meleeByItem.put(item, selected.meleeBehavior());
            }
        });

        snapshot = new Snapshot(byId, byItem, meleeByItem);
        DJCraft.LOGGER.info("Loaded {} explicit DJ item behavior assignments", byId.size());
    }

    public static void replaceOverrides(Map<ResourceLocation, ResourceLocation> overrides) {
        Map<ResourceLocation, ResourceLocation> byId = new LinkedHashMap<>();
        Map<Item, DJItemBehaviorDefinition> byItem = new LinkedHashMap<>();
        Map<Item, DJMeleeBehavior> meleeByItem = new LinkedHashMap<>();
        overrides.forEach((itemId, behaviorId) ->
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(item -> {
                    DJItemBehaviorDefinition behavior = DJItemBehaviorRegistry.get(behaviorId).orElse(null);
                    if (behavior == null) {
                        DJCraft.LOGGER.error("Ignoring unknown synced DJ item behavior {} for {}",
                                behaviorId, itemId);
                        return;
                    }
                    if (!behavior.supports(item)) {
                        DJCraft.LOGGER.error("Ignoring incompatible synced DJ item behavior {} for {}",
                                behavior.id(), itemId);
                        return;
                    }
                    byId.put(itemId, behaviorId);
                    byItem.put(item, behavior);
                    DJMeleeBehavior preserved = snapshot.byItem().get(item) == behavior
                            ? snapshot.meleeByItem().get(item)
                            : null;
                    meleeByItem.put(item, preserved != null ? preserved : behavior.meleeBehavior()
                            .orElseGet(() -> DJItemBehaviorRegistry.MELEE.meleeBehavior().orElseThrow()));
                }, () -> DJCraft.LOGGER.error("Ignoring DJ item behavior for unknown item {}", itemId)));
        snapshot = new Snapshot(byId, byItem, meleeByItem);
    }

    public static Map<ResourceLocation, ResourceLocation> getOverridesSnapshot() {
        return snapshot.byId();
    }

    private static DJItemBehaviorProfile select(ResourceLocation itemId, Item item,
            List<DJItemBehaviorProfile> profiles, boolean exact) {
        List<DJItemBehaviorProfile> candidates = new ArrayList<>();
        for (DJItemBehaviorProfile profile : profiles) {
            boolean matches = exact
                    ? profile.items().contains(itemId)
                    : profile.tags().stream().anyMatch(item.builtInRegistryHolder()::is);
            if (!matches) {
                continue;
            }
            if (!profile.behavior().supports(item)) {
                DJCraft.LOGGER.error("Ignoring incompatible DJ item behavior {} from profile {} for {}",
                        profile.behavior().id(), profile.id(), itemId);
                continue;
            }
            candidates.add(profile);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        DJItemBehaviorProfile selected = candidates.getFirst();
        if (candidates.size() > 1 && candidates.get(1).priority() == selected.priority()) {
            DJCraft.LOGGER.warn("DJ item behavior conflict for {}: selected {}, ignored {}",
                    itemId, selected.id(), candidates.get(1).id());
        }
        return selected;
    }

    private record Snapshot(Map<ResourceLocation, ResourceLocation> byId,
            Map<Item, DJItemBehaviorDefinition> byItem,
            Map<Item, DJMeleeBehavior> meleeByItem) {
        private Snapshot {
            byId = Map.copyOf(byId);
            byItem = Map.copyOf(byItem);
            meleeByItem = Map.copyOf(meleeByItem);
        }

        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of());
        }
    }
}
