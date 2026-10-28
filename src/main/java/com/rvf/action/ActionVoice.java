package com.rvf.action;

import com.rvf.packet.FlashbackRemoteVoice;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/** A chunk of the recorded player's microphone audio (16-bit mono PCM). */
public class ActionVoice implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/rvf_voice");
    public static final ActionVoice INSTANCE = new ActionVoice();
    private ActionVoice() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        int sampleRate = buf.readVarInt();
        byte[] pcm = buf.readByteArray();

        // Like Flashback's own voice chat: no sound while paused, seeking or exporting
        if (replayServer.fastForwarding || replayServer.replayPaused || Flashback.EXPORT_JOB != null) {
            return;
        }

        FlashbackRemoteVoice payload = new FlashbackRemoteVoice(sampleRate, pcm);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}