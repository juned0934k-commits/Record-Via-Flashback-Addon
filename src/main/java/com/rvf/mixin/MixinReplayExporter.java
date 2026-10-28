package com.rvf.mixin;

import com.rvf.voice.VoiceLoudness;
import com.moulberry.flashback.record.ReplayExporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/** Adds rvf_loudness.bin to the replay zip after Flashback has exported it. */
@Mixin(value = ReplayExporter.class, remap = false)
public abstract class MixinReplayExporter {

    @Inject(method = "export", at = @At("HEAD"), remap = false, require = 1)
    private static void rvf$beforeExport(Path recordFolder, Path outputFile, String name, CallbackInfo ci) {
        VoiceLoudness.beginExport(recordFolder);
    }

    @Inject(method = "export", at = @At("RETURN"), remap = false, require = 1)
    private static void rvf$afterExport(Path recordFolder, Path outputFile, String name, CallbackInfo ci) {
        VoiceLoudness.finishExport(outputFile);
    }

}
