package otto.djgun.djcraft.client.sound;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import otto.djgun.djcraft.sound.BeatOutcome;
import otto.djgun.djcraft.sound.DJActionOutcome;
import otto.djgun.djcraft.sound.DJWeaponSoundSemantic;
import otto.djgun.djcraft.sound.TargetOutcome;

public record DJWeaponSoundProfile(ResourceLocation fallback,
        Map<DJWeaponSoundSemantic, List<Rule>> events) {
    public DJWeaponSoundProfile {
        events = Map.copyOf(events);
    }

    public record Rule(BeatOutcome beat, boolean anyBeat, TargetOutcome target, boolean anyTarget,
            List<Choice> sounds, float volume, float minPitch, float maxPitch, boolean spatial) {
        public Rule {
            sounds = List.copyOf(sounds);
            if (sounds.isEmpty() || !Float.isFinite(volume) || volume < 0.0f || volume > 4.0f
                    || !Float.isFinite(minPitch) || !Float.isFinite(maxPitch)
                    || minPitch < 0.5f || maxPitch > 2.0f || minPitch > maxPitch) {
                throw new IllegalArgumentException("Invalid weapon sound rule");
            }
        }

        public boolean matches(DJActionOutcome outcome) {
            return (anyBeat || beat == outcome.beat()) && (anyTarget || target == outcome.target());
        }
    }

    public record Choice(ResourceLocation event, int weight) {
        public Choice {
            if (event == null || weight < 1 || weight > 100) {
                throw new IllegalArgumentException("Invalid weapon sound choice");
            }
        }
    }

    public record Selection(ResourceLocation event, float volume, float pitch, boolean spatial) {
    }
}
