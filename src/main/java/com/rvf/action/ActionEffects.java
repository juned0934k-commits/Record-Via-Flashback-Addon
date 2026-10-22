package com.rvf.action;

import com.rvf.packet.FlashbackRemoteEffects;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

public class ActionEffects implements Action {

    private static final ResourceLocation NAME = Flashback.createResourceLocation("action/effects");
    public static final ActionEffects INSTANCE = new ActionEffects();
    private ActionEffects() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<MobEffectInstance> effects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            effects.add(MobEffectInstance.STREAM_CODEC.decode(buf));
        }

        FlashbackRemoteEffects payload = new FlashbackRemoteEffects(replayServer.getLocalPlayerId(), effects);
        for (ReplayPlayer replayViewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(replayViewer, payload);
        }
    }

}
