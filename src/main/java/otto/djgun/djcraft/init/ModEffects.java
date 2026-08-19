package otto.djgun.djcraft.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.effect.RendMobEffect;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            BuiltInRegistries.MOB_EFFECT, DJCraft.MODID);

    public static final Holder<MobEffect> REND = EFFECTS.register("rend", RendMobEffect::new);

    private ModEffects() {
    }
}
