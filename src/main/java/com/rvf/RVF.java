package com.rvf;

import com.rvf.action.ActionCameraType;
import com.rvf.action.ActionChatSuggestions;
import com.rvf.action.ActionChatTyping;
import com.rvf.action.ActionCreativeScreen;
import com.rvf.action.ActionEffects;
import com.rvf.action.ActionInventoryExtras;
import com.rvf.action.ActionInventoryScreen;
import com.rvf.action.ActionSpyglass;
import com.rvf.client.ReplayChatSuggestions;
import com.rvf.client.ReplayChatTyping;
import com.rvf.client.ReplayCreativeScreen;
import com.rvf.client.ReplayEffects;
import com.rvf.client.ReplayInventoryExtras;
import com.rvf.client.ReplayInventoryOverlay;
import com.rvf.client.ReplayPerspective;
import com.rvf.client.ReplaySpyglass;
import com.rvf.packet.FlashbackRemoteCameraType;
import com.rvf.packet.FlashbackRemoteChatSuggestions;
import com.rvf.packet.FlashbackRemoteChatTyping;
import com.rvf.packet.FlashbackRemoteCreativeScreen;
import com.rvf.packet.FlashbackRemoteEffects;
import com.rvf.packet.FlashbackRemoteInventoryExtras;
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
        com.rvf.client.RvfCommand.register();
        com.rvf.client.MicIndicator.register();

        // Recorded actions (same names as the original patched Flashback, so old recordings keep working)
        ActionRegistry.register(ActionInventoryScreen.INSTANCE);
        ActionRegistry.register(ActionInventoryExtras.INSTANCE);
        ActionRegistry.register(ActionCreativeScreen.INSTANCE);
        ActionRegistry.register(ActionCameraType.INSTANCE);
        ActionRegistry.register(ActionEffects.INSTANCE);
        ActionRegistry.register(ActionSpyglass.INSTANCE);
        ActionRegistry.register(ActionChatTyping.INSTANCE);
        ActionRegistry.register(ActionChatSuggestions.INSTANCE);
        ActionRegistry.register(com.rvf.action.ActionVoice.INSTANCE);
        ActionRegistry.register(com.rvf.action.ActionSystemAudio.INSTANCE);

        // Server -> replay viewer payloads
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteInventoryScreen.TYPE, FlashbackRemoteInventoryScreen.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteInventoryExtras.TYPE, FlashbackRemoteInventoryExtras.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteCreativeScreen.TYPE, FlashbackRemoteCreativeScreen.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteCameraType.TYPE, FlashbackRemoteCameraType.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteEffects.TYPE, FlashbackRemoteEffects.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteSpyglass.TYPE, FlashbackRemoteSpyglass.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteChatTyping.TYPE, FlashbackRemoteChatTyping.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashbackRemoteChatSuggestions.TYPE, FlashbackRemoteChatSuggestions.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.rvf.packet.FlashbackRemoteVoice.TYPE, com.rvf.packet.FlashbackRemoteVoice.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.rvf.packet.FlashbackRemoteSystemAudio.TYPE, com.rvf.packet.FlashbackRemoteSystemAudio.STREAM_CODEC);

        // Receivers
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteInventoryScreen.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayInventoryOverlay.set(payload.entityId(), payload.state());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteInventoryExtras.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayInventoryExtras.set(payload.entityId(), payload.snapshot());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteCreativeScreen.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayCreativeScreen.set(payload.entityId(), payload.snapshot());
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
        ClientPlayNetworking.registerGlobalReceiver(FlashbackRemoteChatSuggestions.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ReplayChatSuggestions.set(payload.entityId(), payload.snapshot());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(com.rvf.packet.FlashbackRemoteVoice.TYPE, (payload, context) -> {
            if (Flashback.isInReplay() && AddonConfig.get().showVoice) {
                com.rvf.voice.VoicePlayer.play(payload.sampleRate(), com.rvf.voice.VoiceFx.micPlayback(payload.sampleRate(), payload.pcm()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(com.rvf.packet.FlashbackRemoteSystemAudio.TYPE, (payload, context) -> {
            if (Flashback.isInReplay() && AddonConfig.get().showVoice) {
                com.rvf.voice.VoicePlayer.SYSTEM.playChunk(payload.sampleRate(), com.rvf.voice.ExportVoice.scale(payload.pcm(), AddonConfig.get().systemVolume));
            }
        });
        com.rvf.voice.VoiceCapture.logDevices();

        LOGGER.info("Record Via Flashback loaded");
    }

}
