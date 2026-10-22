package com.rvf.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import com.rvf.client.ReplayChatSuggestions;
import com.rvf.packet.FlashbackRemoteChatSuggestions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionChatSuggestions implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/chat_suggestions");
    public static final ActionChatSuggestions INSTANCE = new ActionChatSuggestions();
    private ActionChatSuggestions() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        ReplayChatSuggestions.Snapshot snapshot = ReplayChatSuggestions.decode(buf);
        FlashbackRemoteChatSuggestions payload = new FlashbackRemoteChatSuggestions(replayServer.getLocalPlayerId(), snapshot);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
