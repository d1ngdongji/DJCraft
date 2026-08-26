package otto.djgun.djcraft.item;

import java.util.function.Predicate;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.PotionContents;

/** A trigger ray crossbow that can consume a tipped arrow to transfer its effects. */
public final class AssaultCrossbowItem extends CrossbowItem {
    private static final Predicate<ItemStack> TIPPED_ARROW = stack -> stack.is(Items.TIPPED_ARROW);

    public AssaultCrossbowItem(Item.Properties properties) {
        super(properties);
    }

    public static PotionContents consumeTippedArrow(ServerPlayer player) {
        ItemStack arrow = ProjectileWeaponItem.getHeldProjectile(player, TIPPED_ARROW);
        if (arrow.isEmpty()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = player.getInventory().getItem(slot);
                if (TIPPED_ARROW.test(candidate)) {
                    arrow = candidate;
                    break;
                }
            }
        }
        if (arrow.isEmpty()) {
            return PotionContents.EMPTY;
        }

        PotionContents contents = arrow.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        if (!player.hasInfiniteMaterials()) {
            arrow.shrink(1);
        }
        return contents;
    }

    public static void applyPotionEffects(ServerPlayer shooter, LivingEntity target, PotionContents contents) {
        AssaultCrossbowPotionEffects.apply(shooter, target, contents);
    }
}
