package otto.djgun.djcraft.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DJMaceTimingAssetTest {
    @Test
    void bundledMaceUseCostsTenEnergy() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/data/minecraft/djcraft/item_timing/mace.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(10.0, json.get("use_energy_cost").getAsDouble());
            assertEquals(2, json.get("use_beat_cooldown").getAsInt());
        }
    }
}
