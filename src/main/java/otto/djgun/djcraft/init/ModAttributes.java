package otto.djgun.djcraft.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import otto.djgun.djcraft.DJCraft;

public final class ModAttributes {
    public static final double DEFAULT_MAX_ENERGY = 50.0;

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(
            BuiltInRegistries.ATTRIBUTE, DJCraft.MODID);

    public static final Holder<Attribute> MAX_ENERGY = ATTRIBUTES.register("max_energy",
            () -> new RangedAttribute("attribute.djcraft.max_energy", DEFAULT_MAX_ENERGY, 0.0, 1024.0)
                    .setSyncable(true));

    private ModAttributes() {
    }

    public static void modifyEntityAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MAX_ENERGY, DEFAULT_MAX_ENERGY);
    }
}
