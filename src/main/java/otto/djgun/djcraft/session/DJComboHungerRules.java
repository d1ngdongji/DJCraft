package otto.djgun.djcraft.session;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/** Server-side hunger rewards for earned combo increases. */
public final class DJComboHungerRules {
    public static final int HIGH_COMBO_THRESHOLD = 50;
    public static final int FOOD_REWARD = 1;
    public static final float SATURATION_REWARD = 1.0F;
    private static final int MAX_FOOD_LEVEL = 20;

    private DJComboHungerRules() {
    }

    public static boolean shouldReward(int combo) {
        return combo >= HIGH_COMBO_THRESHOLD || (combo > 0 && combo % 2 == 0);
    }

    public static void reward(Player player) {
        FoodData foodData = player.getFoodData();
        int restoredFoodLevel = Math.min(MAX_FOOD_LEVEL,
                foodData.getFoodLevel() + FOOD_REWARD);
        foodData.setFoodLevel(restoredFoodLevel);
        foodData.setSaturation(Math.min(restoredFoodLevel,
                foodData.getSaturationLevel() + SATURATION_REWARD));
    }
}
