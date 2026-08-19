package otto.djgun.djcraft.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class RendMobEffect extends MobEffect {
    public static final int COLOR = 0x9B1B30;

    public RendMobEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
