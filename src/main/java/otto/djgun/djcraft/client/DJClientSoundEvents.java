package otto.djgun.djcraft.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import otto.djgun.djcraft.DJCraft;
import otto.djgun.djcraft.sound.DJChannelSourceAccess;
import otto.djgun.djcraft.sound.OpenALHelper;

/** Associates the exact SoundInstance selected by SoundEngine with its OpenAL channel. */
@EventBusSubscriber(modid = DJCraft.MODID, value = Dist.CLIENT)
public final class DJClientSoundEvents {
    private DJClientSoundEvents() {
    }

    @SubscribeEvent
    public static void onStaticSound(PlaySoundSourceEvent event) {
        bind(event.getSound(), event.getChannel());
    }

    @SubscribeEvent
    public static void onStreamingSound(PlayStreamingSourceEvent event) {
        bind(event.getSound(), event.getChannel());
    }

    private static void bind(SoundInstance sound, Channel channel) {
        if (channel instanceof DJChannelSourceAccess access) {
            OpenALHelper.onSoundSourceStarted(sound, access.djcraft$getSourceId(), channel);
        }
    }
}
