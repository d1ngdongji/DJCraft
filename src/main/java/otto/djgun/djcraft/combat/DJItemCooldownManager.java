package otto.djgun.djcraft.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import otto.djgun.djcraft.DJCraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages resource-driven DJ item timing and energy cost overrides.
 */
public final class DJItemCooldownManager {
    private static volatile DJItemTimingSnapshot<ResourceLocation, Item> snapshot = DJItemTimingSnapshot.empty();

    private DJItemCooldownManager() {
    }

    public static void replaceProfiles(Map<ResourceLocation, DJItemTimingProfile> profiles) {
        Map<ResourceLocation, DJItemTimingProfile> acceptedById = new LinkedHashMap<>();
        Map<Item, DJItemTimingProfile> acceptedByItem = new LinkedHashMap<>();

        profiles.forEach((itemId, profile) -> BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(item -> {
            acceptedById.put(itemId, profile);
            acceptedByItem.put(item, profile);
        }, () -> DJCraft.LOGGER.error("Ignoring item timing profile for unknown item {}", itemId)));

        snapshot = new DJItemTimingSnapshot<>(acceptedById, acceptedByItem);
        DJCraft.LOGGER.info("Loaded {} item timing profiles", acceptedById.size());
    }

    public static Map<ResourceLocation, DJItemTimingProfile> getProfilesSnapshot() {
        return snapshot.byId();
    }

    public static int getBeatCooldown(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1;
        }

        DJItemTimingProfile profile = snapshot.byKey().get(stack.getItem());
        int calculatedFallback = calcBeatsFromAttackSpeed(stack);
        return profile != null ? profile.resolveBeatCooldown(calculatedFallback) : calculatedFallback;
    }

    public static int getSwitchWarmup(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        DJItemTimingProfile profile = snapshot.byKey().get(stack.getItem());
        int resolvedBeatCooldown = getBeatCooldown(stack);
        return profile != null ? profile.resolveSwitchWarmup(resolvedBeatCooldown) : resolvedBeatCooldown;
    }

    public static double getAttackEnergyCost(ItemStack stack) {
        return getEnergyCost(stack, true);
    }

    public static double getUseEnergyCost(ItemStack stack) {
        return getEnergyCost(stack, false);
    }

    private static double getEnergyCost(ItemStack stack, boolean attack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        DJItemTimingProfile profile = snapshot.byKey().get(stack.getItem());
        if (profile == null) {
            return 0.0;
        }
        return attack ? profile.resolveAttackEnergyCost() : profile.resolveUseEnergyCost();
    }

    private static int calcBeatsFromAttackSpeed(ItemStack stack) {
        double baseAttackSpeed = 4.0;
        var acceptedModifiers = stack.getAttributeModifiers();
        final double[] speedModifier = { 0.0 };

        acceptedModifiers.forEach(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_SPEED)) {
                speedModifier[0] += modifier.amount();
            }
        });

        double finalSpeed = baseAttackSpeed + speedModifier[0];
        if (finalSpeed >= 1.5) {
            return 1;
        }
        if (finalSpeed >= 1.0) {
            return 2;
        }
        if (finalSpeed >= 0.6) {
            return 3;
        }
        return 4;
    }
}
