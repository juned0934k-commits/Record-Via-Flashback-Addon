package com.rvf.mixin;

import com.rvf.client.LoudnessTrack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the lime voice row in Flashback's timeline. The target is named by string so no stub class is needed. */
@Mixin(targets = "com.moulberry.flashback.editor.ui.windows.TimelineWindow", remap = false)
public abstract class MixinTimelineLoudness {

    @Inject(method = "renderKeyframes", at = @At("TAIL"), remap = false, require = 1)
    private static void rvf$drawVoiceTrack(float x, float y, float mouseX, int minTicks, float availableTicks, int totalTicks, CallbackInfo ci) {
        LoudnessTrack.draw(x, y, minTicks, availableTicks);
    }

}
