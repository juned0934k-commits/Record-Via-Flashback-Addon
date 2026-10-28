package com.rvf.mixin;

import com.rvf.record.RecorderHooks;
import com.rvf.voice.VoiceLoudness;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/**
 * Hooks into Flashback's recorder so the addon can write its own actions into the replay.
 * The target is Flashback's own class, so no name remapping is needed.
 */
@Mixin(value = Recorder.class, remap = false)
public abstract class MixinRecorder {

    @Shadow(remap = false)
    @Final
    private AsyncReplaySaver asyncReplaySaver;

    /** Number of replay ticks written so far (the index of the tick being written). */
    @Shadow(remap = false)
    private int writtenTicks;

    /** A new recording starts: forget the loudness of the previous one. */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void rvf$afterCreate(RegistryAccess registryAccess, CallbackInfo ci) {
        VoiceLoudness.startRecording();
    }

    /** Runs every recording tick, after Flashback has written the local player data. */
    @Inject(method = "writeLocalData", at = @At("TAIL"), remap = false, require = 1)
    private void rvf$afterLocalData(CallbackInfo ci) {
        RecorderHooks.tick(this.asyncReplaySaver, this.writtenTicks);
    }

    /** Runs after a snapshot is written, so the current state is stored at every seek point. */
    @Inject(method = "writeSnapshot", at = @At("TAIL"), remap = false, require = 1)
    private void rvf$afterSnapshot(boolean asActualSnapshot, CallbackInfo ci) {
        RecorderHooks.snapshot(this.asyncReplaySaver, this.writtenTicks);
    }

    /** Runs when Flashback forgets the last local player data (start of a recording, new player object). */
    @Inject(method = "resetLastLocalData", at = @At("TAIL"), remap = false, require = 1)
    private void rvf$afterReset(CallbackInfo ci) {
        RecorderHooks.reset();
    }

    /** The recording is finished: save the loudness file into the record folder (it is zipped later by the exporter). */
    @Inject(method = "finish", at = @At("RETURN"), remap = false, require = 1)
    private void rvf$afterFinish(CallbackInfoReturnable<Path> cir) {
        VoiceLoudness.saveTo(cir.getReturnValue());
    }

}
