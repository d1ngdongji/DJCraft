package otto.djgun.djcraft.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import otto.djgun.djcraft.combat.DJItemBehavior;
import otto.djgun.djcraft.combat.DJItemBehaviorManager;
import otto.djgun.djcraft.combat.DJItemCooldownManager;
import otto.djgun.djcraft.session.DJModeManagerClient;

/** Scales the active DJ bow's charge curve to its data-driven beat duration. */
@OnlyIn(Dist.CLIENT)
@Mixin(BowItem.class)
public class BowItemMixin {
    private static final int VANILLA_FULL_POWER_TICKS = 20;

    @Inject(method = "getPowerForTime", at = @At("HEAD"), cancellable = true)
    private static void djScaleBowPower(int charge, CallbackInfoReturnable<Float> cir) {
        var session = DJModeManagerClient.getInstance().getActiveSession().orElse(null);
        var player = Minecraft.getInstance().player;
        ItemStack activeStack = player == null ? ItemStack.EMPTY : player.getUseItem();
        if (session == null || !DJItemBehaviorManager.is(activeStack, DJItemBehavior.BOW)) {
            return;
        }

        int fullPowerBeats = DJItemCooldownManager.getBeatCooldown(activeStack);
        int fullPowerTicks = otto.djgun.djcraft.util.BeatGridUtil.getDurationTicks(
                session.getCurrentTimeMs(), session.getTrackPack().timeline().combatLine(), fullPowerBeats);
        if (fullPowerTicks <= 0) {
            return;
        }

        int scaledCharge = (int) ((float) charge / fullPowerTicks * VANILLA_FULL_POWER_TICKS);
        float power = (float) scaledCharge / VANILLA_FULL_POWER_TICKS;
        power = (power * power + power * 2.0F) / 3.0F;
        cir.setReturnValue(Math.min(power, 1.0F));
    }
}
