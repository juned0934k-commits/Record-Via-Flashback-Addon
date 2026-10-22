package com.rvf.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import com.rvf.client.ReplayCreativeScreen;
import com.rvf.packet.FlashbackRemoteCreativeScreen;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionCreativeScreen implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/creative_screen");
    public static final ActionCreativeScreen INSTANCE = new ActionCreativeScreen();
    private ActionCreativeScreen() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        ReplayCreativeScreen.Snapshot snapshot = ReplayCreativeScreen.decode(buf);
        FlashbackRemoteCreativeScreen payload = new FlashbackRemoteCreativeScreen(replayServer.getLocalPlayerId(), snapshot);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
