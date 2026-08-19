package otto.djgun.djcraft.cybergrind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

class CyberGrindProfileTest {
    private static final String PROFILE = """
            {
              "display_name":{"text":"Test"},
              "advance_threshold":5,
              "warning_ticks":40,
              "party_budget_per_extra_player":0.75,
              "budget_ranges":[
                {"min_wave":1,"max_wave":10,"base_budget":8,"budget_per_wave":2,"max_budget":26},
                {"min_wave":11,"base_budget":30,"budget_per_wave":3,"max_budget":60}
              ],
              "entries":[
                {"entity":"minecraft:zombie","cost":1,"draw_weight":10,"min_wave":1,"chance":1,"min_count":1,"max_count":12},
                {"entity":"minecraft:warden","cost":30,"draw_weight":1,"min_wave":20,"chance":0,"min_count":1,"max_count":1}
              ]
            }
            """;

    @Test
    void resolvesSegmentedAndPartyScaledBudget() {
        CyberGrindProfile profile = parse(PROFILE);
        assertEquals(8, profile.budgetFor(1, 1));
        assertEquals(26, profile.budgetFor(10, 1));
        assertEquals(30, profile.budgetFor(11, 1));
        assertEquals(52, profile.budgetFor(11, 2));
    }

    @Test
    void plannerNeverOverspendsOrExceedsCountCaps() {
        CyberGrindProfile profile = parse(PROFILE);
        CyberGrindWavePlanner.Plan plan = CyberGrindWavePlanner.plan(profile, 10, 1, new Random(42));
        assertTrue(plan.spent() <= plan.budget());
        assertTrue(plan.spawns().size() <= 12);
        assertTrue(plan.spawns().stream().allMatch(spawn ->
                spawn.entry().entityId().toString().equals("minecraft:zombie")));
    }

    @Test
    void rejectsGapsAndStripsReservedNbt() {
        assertThrows(IllegalArgumentException.class, () -> parse(PROFILE.replace(
                "{\"min_wave\":11", "{\"min_wave\":12")));
        CyberGrindProfile profile = parse(PROFILE.replace(
                "\"max_count\":12}", "\"max_count\":12,\"nbt\":\"{UUID:[I;1,2,3,4],Tags:['safe']}\"}"));
        var tag = profile.entries().getFirst().nbt();
        assertTrue(!tag.contains("UUID") && tag.contains("Tags"));
    }

    private static CyberGrindProfile parse(String json) {
        return CyberGrindProfile.parse(ResourceLocation.fromNamespaceAndPath("test", "profile"),
                JsonParser.parseString(json));
    }
}
