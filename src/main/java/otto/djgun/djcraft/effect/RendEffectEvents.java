package otto.djgun.djcraft.effect;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import otto.djgun.djcraft.init.ModEffects;

public final class RendEffectEvents {
    private RendEffectEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!RendEffectRules.shouldAmplify(event.getEntity().level().isClientSide(),
                event.getSource().getEntity() != null, event.getAmount())) {
            return;
        }
        var instance = event.getEntity().getEffect(ModEffects.REND);
        if (instance != null) {
            event.setAmount(event.getAmount() + RendEffectRules.bonusDamage(instance.getAmplifier()));
        }
    }
}
