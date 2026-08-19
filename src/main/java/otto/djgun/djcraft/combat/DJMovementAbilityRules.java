package otto.djgun.djcraft.combat;

import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

public final class DJMovementAbilityRules {
    public static final double NORMAL_DASH_ENERGY_COST = 0.0;
    public static final double FLOWERY_DASH_ENERGY_COST = 3.0;
    public static final double DOUBLE_JUMP_ENERGY_COST = 0.0;
    public static final double DOUBLE_JUMP_MOMENTUM_MULTIPLIER = 1.8;
    public static final double GROUND_SLAM_DOWNWARD_VELOCITY = 3.0;
    public static final double GROUND_SLAM_MIN_EFFECT_FALL_DISTANCE = 3.0;
    public static final double GROUND_SLAM_MAX_DAMAGE_FALL_DISTANCE = 12.0;
    public static final float GROUND_SLAM_MIN_DAMAGE = 4.0F;
    public static final float GROUND_SLAM_MAX_DAMAGE = 16.0F;
    public static final double GROUND_SLAM_RADIUS = 4.0;
    public static final double GROUND_SLAM_LAUNCH_SPEED = 1.0;
    public static final int MAX_CONSECUTIVE_DASHES = 3;
    public static final int DASH_COOLDOWN_TICKS = 30;
    public static final double NORMAL_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER = 3.0 / 4.0;
    public static final double FLOWERY_DASH_DIRECTIONAL_IMPULSE_MULTIPLIER = 4.0 / 5.0;
    public static final int NORMAL_DASH_MOMENTUM_LOCK_TICKS = 3;
    public static final int FLOWERY_DASH_MOMENTUM_LOCK_TICKS = 5;
    public static final int DASH_MINIMUM_FOOD_LEVEL = 6;
    public static final int DASH_HUNGER_COST = 2;

    private DJMovementAbilityRules() {
    }

    public static boolean canDash(Player player) {
        return canUseMovementAbility(player)
                && player.getFoodData().getFoodLevel() > DASH_MINIMUM_FOOD_LEVEL;
    }

    public static void consumeDashHunger(Player player) {
        FoodData foodData = player.getFoodData();
        if (foodData.getSaturationLevel() > 0.0F) {
            foodData.setSaturation(Math.max(0.0F,
                    foodData.getSaturationLevel() - DASH_HUNGER_COST));
        } else {
            foodData.setFoodLevel(Math.max(0,
                    foodData.getFoodLevel() - DASH_HUNGER_COST));
        }
    }

    public static boolean canDoubleJump(Player player) {
        return canUseMovementAbility(player) && !player.onGround() && !player.onClimbable();
    }

    public static boolean canGroundJump(Player player) {
        return canUseMovementAbility(player) && player.onGround() && !player.onClimbable();
    }

    public static boolean canGroundSlam(Player player) {
        return canUseMovementAbility(player) && !player.onGround() && !player.onClimbable();
    }

    public static double doubleJumpPower(Player player) {
        return (player.getAttributeValue(Attributes.JUMP_STRENGTH) + player.getJumpBoostPower())
                * DOUBLE_JUMP_MOMENTUM_MULTIPLIER;
    }

    public static float groundSlamDamage(double fallDistance) {
        double progress = Math.clamp(
                (fallDistance - GROUND_SLAM_MIN_EFFECT_FALL_DISTANCE)
                        / (GROUND_SLAM_MAX_DAMAGE_FALL_DISTANCE - GROUND_SLAM_MIN_EFFECT_FALL_DISTANCE),
                0.0, 1.0);
        return (float) (GROUND_SLAM_MIN_DAMAGE
                + (GROUND_SLAM_MAX_DAMAGE - GROUND_SLAM_MIN_DAMAGE) * progress);
    }

    private static boolean canUseMovementAbility(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.getAbilities().flying && !player.isFallFlying();
    }
}
