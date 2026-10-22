package com.rvf.action;

import com.rvf.packet.FlashbackRemoteCameraType;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class ActionCameraType implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/camera_type");
    public static final ActionCameraType INSTANCE = new ActionCameraType();
    private ActionCameraType() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        FlashbackRemoteCameraType payload = new FlashbackRemoteCameraType(buf.readVarInt());
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
