package com.rvf;

import com.rvf.action.ActionCameraType;
import com.rvf.action.ActionChatTyping;
import com.rvf.action.ActionEffects;
import com.rvf.action.ActionInventoryScreen;
import com.rvf.action.ActionSpyglass;
import com.rvf.client.ReplayChatTyping;
import com.rvf.client.ReplayEffects;
import com.rvf.client.ReplayInventoryOverlay;
import com.rvf.client.ReplayPerspective;
import com.rvf.client.ReplaySpyglass;
import com.rvf.packet.FlashbackRemoteCameraType;
import com.rvf.packet.FlashbackRemoteChatTyping;
import com.rvf.packet.FlashbackRemoteEffects;
import com.rvf.packet.FlashbackRemoteInventoryScreen;
import com.rvf.packet.FlashbackRemoteSpyglass;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RVF implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("rvf");

    @Override
    public void onInitializeClient() {
        AddonConfig.load();

        // Recorded actions (same names as the original patched Flashback, so old recordings keep working)
        ActionRegistry.register(ActionInventoryScreen.INSTANCE);
        ActionRegistry.register(ActionCameraType.INSTANCE);
        ActionRegistry.register(ActionEffects.INSTANCE);
        ActionRegistry.register(ActionSpyglass.INSTANCE);
        ActionRegistry.register(ActionChatTyping.INSTANCE);

        // Server -> replay viewer payloads
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteInventoryScreen.TYPE, FlashbackRemoteInventoryScreen.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteCameraType.TYPE, FlashbackRemoteCameraType.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteEffects.TYPE, FlashbackRemoteEffects.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteSpyglass.TYPE, FlashbackRemoteSpyglass.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FlashbackRemoteChatTyping.TYPE, FlashbackRemoteChatTyping.STREAM_CODEC);

        // Receivers
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteInventoryScreen.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayInventoryOverlay.set(payload.entityId(), payload.state());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteCameraType.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayPerspective.onRecorded(payload.perspective());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteEffects.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayEffects.set(payload.entityId(), payload.effects());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteSpyglass.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplaySpyglass.set(payload.entityId(), payload.scoping());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteChatTyping.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayChatTyping.set(payload.entityId(), payload.open(), payload.text(), payload.cursor());
            }
        });

        LOGGER.info("Record Via Flashback loaded");
    }

}
