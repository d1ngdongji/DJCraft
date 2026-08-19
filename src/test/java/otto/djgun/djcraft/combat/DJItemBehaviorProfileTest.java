package otto.djgun.djcraft.combat;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import otto.djgun.djcraft.api.combat.RegisterDJItemBehaviorsEvent;
import otto.djgun.djcraft.api.combat.DJAreaMeleeBehavior;
import otto.djgun.djcraft.api.combat.DJSoftTargetMeleeBehavior;

class DJItemBehaviorProfileTest {
    @Test
    void parsesItemsTagsPriorityAndBehavior() {
        DJItemBehaviorProfile profile = parse("""
                {
                  "priority": 100,
                  "selectors": {
                    "items": ["example:steel_crossbow"],
                    "tags": ["example:crossbows"]
                  },
                  "behavior": "djcraft:crossbow"
                }
                """);

        assertEquals(100, profile.priority());
        assertEquals(DJItemBehavior.CROSSBOW, profile.behavior().family());
        assertEquals(1, profile.items().size());
        assertEquals(1, profile.tags().size());
    }

    @Test
    void rejectsMalformedProfilesIndependently() {
        assertInvalid("{}");
        assertInvalid("""
                {"selectors": {}, "behavior": "djcraft:bow"}
                """);
        assertInvalid("""
                {"priority": 1001, "selectors":{"items":["minecraft:bow"]},"behavior":"djcraft:bow"}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:bow"]},"behavior":"example:script"}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:bow"],"extra":[]},"behavior":"djcraft:bow"}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:bow","minecraft:bow"]},"behavior":"djcraft:bow"}
                """);
    }

    @Test
    void parsesEveryBuiltInBehaviorId() {
        assertEquals(DJItemBehavior.BOW, DJItemBehavior.parse(ResourceLocation.parse("djcraft:bow")));
        assertEquals(DJItemBehavior.CROSSBOW, DJItemBehavior.parse(ResourceLocation.parse("djcraft:crossbow")));
        assertEquals(DJItemBehavior.SHIELD, DJItemBehavior.parse(ResourceLocation.parse("djcraft:shield")));
        assertEquals(DJItemBehavior.CHARGE, DJItemBehavior.parse(ResourceLocation.parse("djcraft:charge")));
        assertEquals(DJItemBehavior.TRIGGER, DJItemBehavior.parse(ResourceLocation.parse("djcraft:trigger")));
        assertEquals(DJItemBehavior.TRIDENT, DJItemBehavior.parse(ResourceLocation.parse("djcraft:trident")));
        assertEquals(DJItemBehavior.MACE, DJItemBehavior.parse(ResourceLocation.parse("djcraft:mace")));
        assertEquals(DJItemBehavior.NONE, DJItemBehavior.parse(ResourceLocation.parse("djcraft:none")));
    }

    @Test
    void genericBehaviorsDeclareTheirActionFamilies() {
        assertEquals(true, DJItemBehavior.CHARGE.isCharge());
        assertEquals(true, DJItemBehavior.BOW.isCharge());
        assertEquals(true, DJItemBehavior.TRIGGER.isTrigger());
        assertEquals(true, DJItemBehavior.CROSSBOW.isTrigger());
    }

    @Test
    void appliesPartialSoftTargetAndCapsuleOverrides() {
        DJItemBehaviorProfile soft = parse("""
                {
                  "selectors":{"items":["minecraft:diamond_sword"]},
                  "behavior":"djcraft:none",
                  "melee":{"reach":5.5,"horizontal_angle_degrees":25}
                }
                """);
        DJSoftTargetMeleeBehavior softBehavior = (DJSoftTargetMeleeBehavior) soft.meleeBehavior();
        assertEquals(5.5, softBehavior.reach());
        assertEquals(25.0, softBehavior.horizontalAngleDegrees());
        assertEquals(20.0, softBehavior.verticalAngleDegrees());

        DJItemBehaviorProfile area = parse("""
                {
                  "selectors":{"items":["minecraft:trident"]},
                  "behavior":"djcraft:trident",
                  "melee":{"cylinder_length":7}
                }
                """);
        DJAreaMeleeBehavior areaBehavior = (DJAreaMeleeBehavior) area.meleeBehavior();
        assertEquals(7.0, areaBehavior.cylinderLength());
        assertEquals(1.0, areaBehavior.radius());
    }

    @Test
    void rejectsInvalidOrShapeIncompatibleMeleeOverrides() {
        assertInvalid("""
                {"selectors":{"items":["minecraft:stick"]},"behavior":"djcraft:none",
                 "melee":{"radius":1}}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:trident"]},"behavior":"djcraft:trident",
                 "melee":{"horizontal_angle_degrees":30}}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:stick"]},"behavior":"djcraft:none",
                 "melee":{"reach":65}}
                """);
        assertInvalid("""
                {"selectors":{"items":["minecraft:stick"]},"behavior":"djcraft:none",
                 "melee":{"vertical_angle_degrees":90}}
                """);
    }

    @Test
    void addonBehaviorCanBeSelectedAndConfigureCanAttackGate() {
        ResourceLocation behaviorId = ResourceLocation.parse("example:unguarded_trigger");
        var definition = new RegisterDJItemBehaviorsEvent().register(
                behaviorId, DJItemBehavior.TRIGGER, item -> true, false);

        DJItemBehaviorProfile profile = parse("""
                {
                  "selectors":{"items":["minecraft:stick"]},
                  "behavior":"example:unguarded_trigger"
                }
                """);

        assertEquals(definition, profile.behavior());
        assertFalse(definition.disabledByCanAttack());
        assertTrue(new RegisterDJItemBehaviorsEvent().register(
                ResourceLocation.parse("example:guarded_charge"),
                DJItemBehavior.CHARGE, item -> true).disabledByCanAttack());
    }

    private static DJItemBehaviorProfile parse(String json) {
        return DJItemBehaviorProfile.parse(ResourceLocation.parse("example:test"),
                JsonParser.parseString(json));
    }

    private static void assertInvalid(String json) {
        assertThrows(RuntimeException.class, () -> parse(json));
    }
}
