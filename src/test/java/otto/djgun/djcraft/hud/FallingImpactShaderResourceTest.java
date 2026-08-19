package otto.djgun.djcraft.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FallingImpactShaderResourceTest {
    @Test
    void shaderDescriptorMatchesTheRendererUniformContract() throws Exception {
        String descriptorPath = "/assets/djcraft/shaders/core/falling_impact_ring.json";
        try (var input = FallingImpactShaderResourceTest.class.getResourceAsStream(descriptorPath)) {
            assertNotNull(input, descriptorPath);
            var root = JsonParser.parseReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:position_tex_color", root.get("vertex").getAsString());
            assertEquals("djcraft:falling_impact_ring", root.get("fragment").getAsString());

            Set<String> uniforms = new HashSet<>();
            root.getAsJsonArray("uniforms").forEach(element ->
                    uniforms.add(element.getAsJsonObject().get("name").getAsString()));
            assertTrue(uniforms.containsAll(Set.of(
                    "ModelViewMat", "ProjMat", "ColorModulator", "RingColor", "Progress")));
        }
    }
}
