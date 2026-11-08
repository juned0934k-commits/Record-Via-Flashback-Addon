package com.rvf.action;

import com.rvf.packet.FlashbackRemoteSystemAudio;
import com.rvf.voice.ExportVoice;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** A chunk of recorded system audio, for example Discord voices (16-bit mono PCM). */
public class ActionSystemAudio implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/rvf_system_audio");
    public static final ActionSystemAudio INSTANCE = new ActionSystemAudio();
    private ActionSystemAudio() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        int sampleRate = buf.readVarInt();
        byte[] pcm = buf.readByteArray();

        // While exporting a video the audio is mixed into the export instead of being played
        if (Flashback.EXPORT_JOB != null) {
            ExportVoice.addSystem(sampleRate, pcm);
            return;
        }
        if (replayServer.fastForwarding || replayServer.replayPaused) {
            return;
        }

        FlashbackRemoteSystemAudio payload = new FlashbackRemoteSystemAudio(sampleRate, pcm);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
