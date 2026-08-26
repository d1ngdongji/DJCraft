package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class WeaponRecipeAssetTest {
    @Test
    void builtInWeaponRecipesKeepTheirProgressionAndBaseWeapons() throws Exception {
        assertRecipe("assault_crossbow", "IRI/HCH/IRI", Map.of(
                "I", "minecraft:iron_block",
                "R", "minecraft:redstone_block",
                "H", "minecraft:tripwire_hook",
                "C", "minecraft:crossbow"));
        assertRecipe("magic_crossbow", "EAE/DCD/ESE", Map.of(
                "E", "minecraft:echo_shard",
                "A", "minecraft:enchanting_table",
                "D", "minecraft:diamond_block",
                "C", "minecraft:crossbow",
                "S", "minecraft:nether_star"));
        assertRecipe("laser_crossbow", "OBO/NCN/ODO", Map.of(
                "O", "minecraft:observer",
                "B", "minecraft:beacon",
                "N", "minecraft:netherite_ingot",
                "C", "minecraft:crossbow",
                "D", "minecraft:diamond_block"));
        assertRecipe("explosive_bow", "TET/NBN/TST", Map.of(
                "T", "minecraft:tnt",
                "E", "minecraft:end_crystal",
                "N", "minecraft:netherite_ingot",
                "B", "minecraft:bow",
                "S", "minecraft:nether_star"));
    }

    private static void assertRecipe(String id, String expectedPattern, Map<String, String> expectedKey)
            throws Exception {
        JsonObject root = load("/data/djcraft/recipe/" + id + ".json");
        assertEquals("minecraft:crafting_shaped", root.get("type").getAsString());
        assertEquals("combat", root.get("category").getAsString());
        assertEquals(expectedPattern, String.join("/", root.getAsJsonArray("pattern").asList().stream()
                .map(value -> value.getAsString()).toList()));
        assertEquals(expectedKey.size(), root.getAsJsonObject("key").size());
        expectedKey.forEach((symbol, item) -> assertEquals(item,
                root.getAsJsonObject("key").getAsJsonObject(symbol).get("item").getAsString()));
        assertEquals("djcraft:" + id, root.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, root.getAsJsonObject("result").get("count").getAsInt());
    }

    private static JsonObject load(String path) throws Exception {
        try (var input = WeaponRecipeAssetTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
