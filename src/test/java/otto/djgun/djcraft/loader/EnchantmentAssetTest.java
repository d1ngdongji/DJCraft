package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class EnchantmentAssetTest {
    @Test
    void enchantmentsDefineRequestedItemsAndMaximumLevels() throws Exception {
        assertEnchantment("aerial_step", 2, "#minecraft:enchantable/foot_armor");
        assertEnchantment("rending", 3, "#minecraft:enchantable/trident");
        assertEnchantment("ray_overcharge", 4, "#djcraft:enchantable/ray_weapon");
        assertEnchantment("lingering_sweep", 2, "#minecraft:enchantable/mace");
    }

    @Test
    void rayWeaponTagContainsAllBuiltInRayWeapons() throws Exception {
        JsonObject root = load("/data/djcraft/tags/item/enchantable/ray_weapon.json");
        var values = root.getAsJsonArray("values").asList().stream()
                .map(value -> value.getAsString()).toList();
        assertEquals(4, values.size());
        assertTrue(values.contains("djcraft:laser_crossbow"));
        assertTrue(values.contains("djcraft:magic_crossbow"));
        assertTrue(values.contains("djcraft:assault_crossbow"));
        assertTrue(values.contains("djcraft:explosive_bow"));
    }

    @Test
    void builtInRayWeaponsSupportVanillaDurabilityEnchantments() throws Exception {
        JsonObject root = load("/data/minecraft/tags/item/enchantable/durability.json");
        var values = root.getAsJsonArray("values").asList().stream()
                .map(value -> value.getAsString()).toList();
        assertEquals(false, root.get("replace").getAsBoolean());
        assertEquals(4, values.size());
        assertTrue(values.contains("djcraft:laser_crossbow"));
        assertTrue(values.contains("djcraft:magic_crossbow"));
        assertTrue(values.contains("djcraft:assault_crossbow"));
        assertTrue(values.contains("djcraft:explosive_bow"));
    }

    @Test
    void allEnchantmentsJoinTheVanillaEnchantingTablePool() throws Exception {
        JsonObject root = load("/data/minecraft/tags/enchantment/non_treasure.json");
        var values = root.getAsJsonArray("values").asList().stream()
                .map(value -> value.getAsString()).toList();
        assertEquals(false, root.get("replace").getAsBoolean());
        assertTrue(values.contains("djcraft:aerial_step"));
        assertTrue(values.contains("djcraft:rending"));
        assertTrue(values.contains("djcraft:ray_overcharge"));
        assertTrue(values.contains("djcraft:lingering_sweep"));
    }

    private static void assertEnchantment(String id, int maxLevel, String supportedItems) throws Exception {
        JsonObject root = load("/data/djcraft/enchantment/" + id + ".json");
        assertEquals(maxLevel, root.get("max_level").getAsInt());
        assertEquals(supportedItems, root.get("supported_items").getAsString());
        assertTrue(root.getAsJsonObject("effects").isEmpty());
    }

    private static JsonObject load(String path) throws Exception {
        try (var input = EnchantmentAssetTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
