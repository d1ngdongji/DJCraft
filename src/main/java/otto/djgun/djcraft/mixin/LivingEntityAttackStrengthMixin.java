package otto.djgun.djcraft.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import otto.djgun.djcraft.combat.access.PlayerAttackStrengthAccess;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAttackStrengthMixin implements PlayerAttackStrengthAccess {
    @Shadow
    protected int attackStrengthTicker;

    @Override
    @Unique
    public int djcraft$getAttackStrengthTicker() {
        return attackStrengthTicker;
    }

    @Override
    @Unique
    public void djcraft$setAttackStrengthTicker(int ticks) {
        attackStrengthTicker = ticks;
    }
}
