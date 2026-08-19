package otto.djgun.djcraft.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
            BuiltInRegistries.SOUND_EVENT, DJCraft.MODID);

    public static final Holder<SoundEvent> GROUND_SLAM_WHOOSH = register("ability.ground_slam_whoosh");
    public static final Holder<SoundEvent> GROUND_SLAM_LAND = register("ability.ground_slam_land");
    public static final Holder<SoundEvent> GROUND_SLAM_IMPACT = register("ability.ground_slam_impact");
    public static final Holder<SoundEvent> TRIDENT_REDIRECT = register("weapon.trident_redirect");

    private ModSounds() {
    }

    private static Holder<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(DJCraft.MODID, name)));
    }
}
