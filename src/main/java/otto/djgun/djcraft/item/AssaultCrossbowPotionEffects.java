package otto.djgun.djcraft.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.alchemy.PotionContents;

final class AssaultCrossbowPotionEffects {
    static final double INSTANT_EFFECT_STRENGTH = 1.0;
    static final int BASE_POTION_DURATION_DIVISOR = 16;
    static final int CUSTOM_EFFECT_DURATION_DIVISOR = 2;

    private AssaultCrossbowPotionEffects() {
    }

    static void apply(ServerPlayer shooter, LivingEntity target, PotionContents contents) {
        contents.potion().ifPresent(potion -> {
            for (MobEffectInstance effect : potion.value().getEffects()) {
                applyEffect(shooter, target, effect, BASE_POTION_DURATION_DIVISOR);
            }
        });
        for (MobEffectInstance effect : contents.customEffects()) {
            applyEffect(shooter, target, effect, CUSTOM_EFFECT_DURATION_DIVISOR);
        }
    }

    private static void applyEffect(ServerPlayer shooter, LivingEntity target,
            MobEffectInstance effect, int durationDivisor) {
        if (effect.getEffect().value().isInstantenous()) {
            effect.getEffect().value().applyInstantenousEffect(
                    shooter, shooter, target, effect.getAmplifier(), INSTANT_EFFECT_STRENGTH);
        } else {
            target.addEffect(scaleTimedEffect(effect, durationDivisor), shooter);
        }
    }

    static MobEffectInstance scaleTimedEffect(MobEffectInstance effect, int durationDivisor) {
        return new MobEffectInstance(effect.getEffect(), scaleDuration(effect.getDuration(), durationDivisor),
                effect.getAmplifier(), effect.isAmbient(), effect.isVisible(), effect.showIcon());
    }

    static int scaleDuration(int duration, int divisor) {
        if (duration < 0) {
            return duration;
        }
        return Math.max(duration / divisor, 1);
    }
}
