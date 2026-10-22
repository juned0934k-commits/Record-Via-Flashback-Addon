package com.rvf.action;

import com.rvf.packet.FlashbackRemoteChatTyping;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionChatTyping implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/chat_typing");
    public static final ActionChatTyping INSTANCE = new ActionChatTyping();
    private ActionChatTyping() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        boolean open = buf.readVarInt() != 0;
        String text = "";
        int cursor = 0;
        if (open) {
            text = buf.readUtf();
            cursor = buf.readVarInt();
        }

        FlashbackRemoteChatTyping payload = new FlashbackRemoteChatTyping(replayServer.getLocalPlayerId(), open, text, cursor);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
