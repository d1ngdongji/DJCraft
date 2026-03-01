package otto.djgun.djcraft.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.sound.OpenALHelper;

/**
 * Mixin 注入 Minecraft 的 Channel 类
 * 用于在声音播放时捕获 OpenAL source ID
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {

    @Shadow
    private int source;

    /**
     * 注入到 attachStaticBuffer 方法，当静态音频缓冲区附加到通道时调用
     * 这时候 source 已经分配好了
     */
    @Inject(method = "attachStaticBuffer", at = @At("TAIL"))
    private void onAttachStaticBuffer(CallbackInfo ci) {
        // 通知 OpenALHelper 有新的 source 被使用
        OpenALHelper.onSourceAttached(this.source, (Channel) (Object) this);
    }

    /**
     * 注入到 attachBufferStream 方法，当流式音频附加到通道时调用
     */
    @Inject(method = "attachBufferStream", at = @At("TAIL"))
    private void onAttachBufferStream(CallbackInfo ci) {
        // 通知 OpenALHelper 有新的 source 被使用
        OpenALHelper.onSourceAttached(this.source, (Channel) (Object) this);
    }

    /**
     * 注入到 stop 方法，当通道停止时调用
     */
    @Inject(method = "stop", at = @At("HEAD"))
    private void onStop(CallbackInfo ci) {
        OpenALHelper.onSourceStopped(this.source);
    }
}
