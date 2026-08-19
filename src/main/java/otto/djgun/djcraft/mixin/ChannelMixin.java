package otto.djgun.djcraft.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import otto.djgun.djcraft.sound.DJChannelSourceAccess;
import otto.djgun.djcraft.sound.OpenALHelper;

/** Exposes the Channel source ID and reports source destruction to the DJ clock. */
@Mixin(Channel.class)
public abstract class ChannelMixin implements DJChannelSourceAccess {
    @Shadow
    private int source;

    @Override
    public int djcraft$getSourceId() {
        return source;
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void djcraft$onStop(CallbackInfo ci) {
        OpenALHelper.onSourceStopped(source, (Channel) (Object) this);
    }
}
