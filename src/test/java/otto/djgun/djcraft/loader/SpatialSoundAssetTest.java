package otto.djgun.djcraft.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpatialSoundAssetTest {
    private static final List<String> SPATIAL_SOUND_PATHS = List.of(
            "/assets/djcraft/sounds/abilities/dash.ogg",
            "/assets/djcraft/sounds/abilities/double_jump.ogg",
            "/assets/djcraft/sounds/abilities/flowery_dash_1.ogg",
            "/assets/djcraft/sounds/abilities/flowery_dash_2.ogg",
            "/assets/djcraft/sounds/abilities/flowery_dash_3.ogg",
            "/assets/djcraft/sounds/abilities/flowery_dash_4.ogg",
            "/assets/djcraft/sounds/abilities/ground_slam_land.ogg",
            "/assets/djcraft/sounds/abilities/ground_slam_impact.ogg",
            "/assets/djcraft/sounds/combat/parry.ogg",
            "/assets/djcraft/sounds/weapons/explosive_bow_charge.ogg",
            "/assets/djcraft/sounds/weapons/explosive_bow_shoot.ogg",
            "/assets/djcraft/sounds/weapons/laser_crossbow_shoot.ogg",
            "/assets/djcraft/sounds/weapons/magic_crossbow_shoot.ogg",
            "/assets/djcraft/sounds/weapons/ray_hit.ogg",
            "/assets/djcraft/sounds/weapons/trident_redirect.ogg");

    @Test
    void worldPositionedSoundsAreMonoOggVorbis() throws Exception {
        for (String path : SPATIAL_SOUND_PATHS) {
            try (var input = getClass().getResourceAsStream(path)) {
                assertNotNull(input, path);
                byte[] bytes = input.readAllBytes();
                assertTrue(TrackPackAudioValidator.isPlayableOggVorbis(new ByteArrayInputStream(bytes)), path);
                assertEquals(1, vorbisChannelCount(bytes),
                        path + " must be mono so OpenAL distance attenuation applies");
            }
        }
    }

    private static int vorbisChannelCount(byte[] bytes) {
        byte[] signature = { 1, 'v', 'o', 'r', 'b', 'i', 's' };
        for (int offset = 0; offset <= bytes.length - signature.length - 5; offset++) {
            boolean matches = true;
            for (int index = 0; index < signature.length; index++) {
                if (bytes[offset + index] != signature[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return Byte.toUnsignedInt(bytes[offset + 11]);
            }
        }
        return -1;
    }
}
