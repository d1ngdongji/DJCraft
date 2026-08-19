package otto.djgun.djcraft.api.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DJItemBehaviorRegistryTest {
    @Test
    void builtInsDeclareCanAttackGatePolicy() {
        assertFalse(DJItemBehaviorRegistry.SHIELD.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.NONE.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.BOW.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.CROSSBOW.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.CHARGE.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.TRIGGER.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.TRIDENT.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.MACE.disabledByCanAttack());
        assertTrue(DJItemBehaviorRegistry.MELEE.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.MINING.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.EATING.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.DASH.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.GROUND_JUMP.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.DOUBLE_JUMP.disabledByCanAttack());
        assertFalse(DJItemBehaviorRegistry.GROUND_SLAM.disabledByCanAttack());
    }

    @Test
    void triggerRegistrationPublishesCanonicalDefinitionAndExecutor() {
        ResourceLocation id = ResourceLocation.parse("example:public_trigger_api");
        DJTriggerBehaviorExecutor executor = context ->
                net.minecraft.world.InteractionResultHolder.success(context.sourceStack());

        var definition = new RegisterDJItemBehaviorsEvent().registerTrigger(
                id, item -> true, false, executor);

        assertSame(definition, DJItemBehaviorRegistry.require(id));
        assertSame(executor, definition.triggerExecutor().orElseThrow());
        assertFalse(definition.disabledByCanAttack());
        assertTrue(definition.isTrigger());
    }

    @Test
    void areaMeleeRegistrationPublishesItsCapsuleStrategy() {
        ResourceLocation id = ResourceLocation.parse("example:long_capsule");
        DJAreaMeleeBehavior strategy = new DJAreaMeleeBehavior(6.0, 0.75);

        var definition = new RegisterDJItemBehaviorsEvent().registerAreaMelee(
                id, otto.djgun.djcraft.combat.DJItemBehavior.TRIDENT,
                item -> true, false, strategy);

        assertSame(definition, DJItemBehaviorRegistry.require(id));
        assertSame(strategy, definition.meleeBehavior().orElseThrow());
        assertFalse(definition.disabledByCanAttack());
    }
}
