package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class CyberGrindDefaultProfileResourceTest {
    @Test
    void bundledPresetContainsTieredPoolAndBossCosts() throws Exception {
        Path path = Path.of("src/main/resources/data/djcraft/djcraft/cyber_grind/default.json");
        CyberGrindProfile profile = CyberGrindProfile.parse(
                ResourceLocation.fromNamespaceAndPath("djcraft", "default"),
                JsonParser.parseString(Files.readString(path)));
        assertEquals(5, profile.advanceThreshold());
        assertEquals(40, profile.warningTicks());
        assertTrue(profile.entries().stream().anyMatch(entry ->
                entry.entityId().toString().equals("minecraft:warden") && entry.cost() == 30));
        assertTrue(profile.entries().stream().anyMatch(entry ->
                entry.entityId().toString().equals("minecraft:wither") && entry.cost() == 40));
        assertTrue(CyberGrindWavePlanner.plan(profile, 30, 4, new java.util.Random(7)).spent()
                <= profile.budgetFor(30, 4));
    }
}
