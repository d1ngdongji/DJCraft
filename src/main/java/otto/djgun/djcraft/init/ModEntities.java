package otto.djgun.djcraft.init;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.entity.DJThrownMace;
import otto.djgun.djcraft.combat.DJMaceThrowRules;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            BuiltInRegistries.ENTITY_TYPE, DJCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DJThrownMace>> THROWN_MACE = ENTITY_TYPES.register(
            "thrown_mace", () -> EntityType.Builder.<DJThrownMace>of(DJThrownMace::new, MobCategory.MISC)
                    .sized(DJMaceThrowRules.COLLISION_DIAMETER, DJMaceThrowRules.COLLISION_DIAMETER)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .noSave()
                    .build("djcraft:thrown_mace"));

    private ModEntities() {
    }
}
