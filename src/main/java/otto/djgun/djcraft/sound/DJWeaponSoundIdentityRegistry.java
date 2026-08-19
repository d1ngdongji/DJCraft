package otto.djgun.djcraft.sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class DJWeaponSoundIdentityRegistry {
    public static final ResourceLocation GENERIC = ResourceLocation.fromNamespaceAndPath("djcraft", "generic");
    private static final List<Entry> RESOLVERS = new ArrayList<>();
    private static volatile List<Entry> snapshot = List.of();
    private static boolean frozen;

    private DJWeaponSoundIdentityRegistry() {
    }

    static synchronized void register(int priority, DJWeaponSoundIdentityResolver resolver) {
        if (frozen) {
            throw new IllegalStateException("Weapon sound identity resolvers are already frozen");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver must not be null");
        }
        RESOLVERS.add(new Entry(priority, resolver));
    }

    public static synchronized void freeze() {
        frozen = true;
        RESOLVERS.sort(Comparator.comparingInt(Entry::priority).reversed());
        snapshot = List.copyOf(RESOLVERS);
    }

    public static ResourceLocation resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return GENERIC;
        }
        for (Entry entry : snapshot) {
            try {
                ResourceLocation resolved = entry.resolver().resolve(stack);
                if (resolved != null) {
                    return resolved;
                }
            } catch (RuntimeException exception) {
                otto.djgun.djcraft.DJCraft.LOGGER.error("Weapon sound identity resolver failed for {}",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), exception);
            }
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? GENERIC : itemId;
    }

    public static boolean playerOwnsProfile(Player player, ResourceLocation profile) {
        if (player == null || profile == null) {
            return false;
        }
        if (resolve(player.getMainHandItem()).equals(profile) || resolve(player.getOffhandItem()).equals(profile)) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && resolve(stack).equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private record Entry(int priority, DJWeaponSoundIdentityResolver resolver) {
    }
}
