package otto.djgun.djcraft.client.config;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

public enum BeatPresentationMode implements TranslatableEnum {
    FALLING,
    LEGACY;

    @Override
    public Component getTranslatedName() {
        return Component.translatable(
                "djcraft.configuration.beatPresentationMode." + name().toLowerCase(Locale.ROOT));
    }
}
