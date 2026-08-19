package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
class CyberGrindDimensionResourceTest {
    @Test
    void monsterLightProviderUsesMinecraft121FlatUniformShape() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/djcraft/dimension_type/cyber_grind.json")) {
            assertTrue(stream != null, "missing Cyber Grind dimension type");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(18000L, json.get("fixed_time").getAsLong());
            var light = json.getAsJsonObject("monster_spawn_light_level");
            assertEquals("minecraft:uniform", light.get("type").getAsString());
            assertEquals(0, light.get("min_inclusive").getAsInt());
            assertEquals(7, light.get("max_inclusive").getAsInt());
            assertFalse(light.has("value"), "1.21.1 IntProvider fields must not be wrapped in value");
        }
    }
}
