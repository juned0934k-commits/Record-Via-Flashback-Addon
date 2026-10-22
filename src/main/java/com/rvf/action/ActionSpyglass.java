package com.rvf.action;

import com.rvf.packet.FlashbackRemoteSpyglass;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionSpyglass implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/spyglass");
    public static final ActionSpyglass INSTANCE = new ActionSpyglass();
    private ActionSpyglass() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        FlashbackRemoteSpyglass payload = new FlashbackRemoteSpyglass(replayServer.getLocalPlayerId(), buf.readVarInt() != 0);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
