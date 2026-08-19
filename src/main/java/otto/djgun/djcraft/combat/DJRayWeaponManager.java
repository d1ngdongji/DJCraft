package otto.djgun.djcraft.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.DJCraft;

/** Immutable lookup snapshot for server-authored ray weapon profiles. */
public final class DJRayWeaponManager {
    private static volatile Snapshot snapshot = Snapshot.empty();

    private DJRayWeaponManager() {
    }

    public static void replaceProfiles(Map<ResourceLocation, DJRayWeaponProfile> profiles) {
        Map<ResourceLocation, DJRayWeaponProfile> byId = new LinkedHashMap<>();
        Map<Item, DJRayWeaponProfile> byItem = new LinkedHashMap<>();
        profiles.forEach((itemId, profile) -> BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(item -> {
            byId.put(itemId, profile);
            byItem.put(item, profile);
        }, () -> DJCraft.LOGGER.error("Ignoring ray weapon profile for unknown item {}", itemId)));
        snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byItem));
        DJCraft.LOGGER.info("Loaded {} ray weapon profiles", byId.size());
    }

    public static Optional<DJRayWeaponProfile> resolve(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(snapshot.byItem().get(stack.getItem()));
    }

    public static Map<ResourceLocation, DJRayWeaponProfile> getProfilesSnapshot() {
        return snapshot.byId();
    }

    private record Snapshot(Map<ResourceLocation, DJRayWeaponProfile> byId,
            Map<Item, DJRayWeaponProfile> byItem) {
        private static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }
}
