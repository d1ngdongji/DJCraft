package otto.djgun.djcraft.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.sound.DJSoundInstance;
import otto.djgun.djcraft.sound.OpenALHelper;

/**
 * Mixin 注入 SoundEngine 类
 * 用于在 DJ 声音实例播放时关联 SoundInstance 和 Channel
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    /**
     * 注入到 stop 方法
     * 当声音停止时，清理关联
     */
    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void onStopSound(SoundInstance soundInstance, CallbackInfo ci) {
        if (soundInstance instanceof DJSoundInstance djSound) {
            OpenALHelper.onDJSoundStop(djSound);
        }
    }
}
