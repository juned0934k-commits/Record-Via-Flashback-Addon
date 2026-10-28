package com.rvf.mixin;

import com.rvf.voice.VoiceLoudness;
import com.moulberry.flashback.playback.ReplayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.FileSystem;

/** Reads rvf_loudness.bin from the replay zip as soon as the replay is opened. */
@Mixin(value = ReplayServer.class, remap = false)
public abstract class MixinReplayServerLoudness {

    @Shadow(remap = false)
    private FileSystem playbackFileSystem;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void rvf$loadLoudness(CallbackInfo ci) {
        VoiceLoudness.loadFrom(this.playbackFileSystem);
        com.rvf.client.ReplaySpyglass.clear();
    }

}
