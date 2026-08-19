package otto.djgun.djcraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DJComboTextureStagesTest {
    @Test
    void usesBuiltInFiftyStageWhenThereAreNoValidCustomOverrides() {
        DJComboTextureStages<String> stages = DJComboTextureStages.create(
                digits("base"), digits("fifty"), Map.of());

        assertEquals("base3", stages.resolve(49, 3));
        assertEquals("fifty3", stages.resolve(50, 3));
        assertEquals("fifty3", stages.resolve(Integer.MAX_VALUE, 3));
    }

    @Test
    void selectsGreatestThresholdAndInheritsMissingDigits() {
        Map<Integer, Map<Integer, String>> custom = new HashMap<>();
        custom.put(1, Map.of(0, "legacy-zero"));
        custom.put(20, Map.of(1, "twenty-one"));
        custom.put(50, Map.of(0, "fifty-zero"));

        DJComboTextureStages<String> stages = DJComboTextureStages.create(
                digits("base"), digits("built-in-fifty"), custom);

        assertEquals("legacy-zero", stages.resolve(1, 0));
        assertEquals("base1", stages.resolve(19, 1));
        assertEquals("twenty-one", stages.resolve(20, 1));
        assertEquals("legacy-zero", stages.resolve(49, 0));
        assertEquals("fifty-zero", stages.resolve(50, 0));
        assertEquals("twenty-one", stages.resolve(Integer.MAX_VALUE, 1));
        assertEquals("base9", stages.resolve(Integer.MAX_VALUE, 9));
    }

    @Test
    void normalizesNonPositiveComboToFirstStage() {
        DJComboTextureStages<String> stages = DJComboTextureStages.create(
                digits("base"), digits("fifty"), Map.of());

        assertEquals("base0", stages.resolve(0, 0));
    }

    private static List<String> digits(String prefix) {
        List<String> values = new ArrayList<>();
        for (int digit = 0; digit <= 9; digit++) {
            values.add(prefix + digit);
        }
        return List.copyOf(values);
    }
}
