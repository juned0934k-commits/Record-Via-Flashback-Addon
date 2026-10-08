package com.rvf.action;

import com.rvf.packet.FlashbackRemoteInventoryScreen;
import com.rvf.client.InventoryScreenState;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public class ActionInventoryScreen implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/inventory_screen");
    public static final ActionInventoryScreen INSTANCE = new ActionInventoryScreen();
    private ActionInventoryScreen() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        FlashbackRemoteInventoryScreen payload = new FlashbackRemoteInventoryScreen(replayServer.getLocalPlayerId(), InventoryScreenState.decode(buf));
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
