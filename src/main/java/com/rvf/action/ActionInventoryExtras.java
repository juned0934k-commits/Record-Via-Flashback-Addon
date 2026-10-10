package com.rvf.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import com.rvf.client.ReplayInventoryExtras;
import com.rvf.packet.FlashbackRemoteInventoryExtras;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

public class ActionInventoryExtras implements Action {

    private static final Identifier NAME = Flashback.createIdentifier("action/inventory_extras");
    public static final ActionInventoryExtras INSTANCE = new ActionInventoryExtras();
    private ActionInventoryExtras() {
    }

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        ReplayInventoryExtras.Snapshot snapshot = ReplayInventoryExtras.decode(buf);
        FlashbackRemoteInventoryExtras payload = new FlashbackRemoteInventoryExtras(replayServer.getLocalPlayerId(), snapshot);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
