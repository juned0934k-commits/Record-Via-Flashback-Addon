package com.rvf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rvf.voice.ExportVoice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.FloatBuffer;

/**
 * Wraps the call where Flashback's export renders the game audio of a frame, and adds the recorded voice to it.
 * The target class is named by string and the method by a regular expression, so only the call itself must match.
 */
@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public abstract class MixinExportJobVoice {

    @WrapOperation(
        method = "/.*/",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/SOFTLoopback;alcRenderSamplesSOFT(JLjava/nio/FloatBuffer;I)V", remap = false),
        remap = false,
        require = 1
    )
    private void rvf$mixVoice(long device, FloatBuffer buffer, int samples, Operation<Void> original) {
        original.call(device, buffer, samples);
        ExportVoice.mix(buffer, samples);
    }

}
