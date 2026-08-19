package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class CyberGrindCataclysmProfileResourceTest {
    private static final Set<String> EXCLUDED_NON_ARENA_ENTITIES = Set.of(
            "cataclysm:the_baby_leviathan",
            "cataclysm:modern_remnant",
            "cataclysm:netherite_ministrosity",
            "cataclysm:the_leviathan",
            "cataclysm:lionfish",
            "cataclysm:cindaria",
            "cataclysm:urchinkin");

    @Test
    void presetIsConditionalBalancedAndContainsAllMajorBosses() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/djcraft/djcraft/cyber_grind/cataclysm.json")) {
            assertTrue(stream != null, "missing Cataclysm Cyber Grind preset");
            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject condition = json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
            assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
            assertEquals("cataclysm", condition.get("modid").getAsString());

            CyberGrindProfile profile = CyberGrindProfile.parse(
                    ResourceLocation.fromNamespaceAndPath("djcraft", "cataclysm"), json);
            assertEquals(30, profile.entries().size());
            assertEquals(10, profile.budgetFor(1, 1));
            assertEquals(300, profile.budgetFor(100, 1));
            assertEquals(525, profile.budgetFor(100, 2));

            Set<String> ids = profile.entries().stream()
                    .map(entry -> entry.entityId().toString()).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.containsAll(Set.of(
                    "cataclysm:ender_guardian", "cataclysm:the_harbinger",
                    "cataclysm:netherite_monstrosity", "cataclysm:ignis",
                    "cataclysm:ancient_remnant", "cataclysm:maledictus",
                    "cataclysm:scylla")));
            assertTrue(EXCLUDED_NON_ARENA_ENTITIES.stream().noneMatch(ids::contains));
            assertFalse(ids.stream().anyMatch(id -> id.contains("projectile") || id.contains("fireball")));
        }
    }
}
