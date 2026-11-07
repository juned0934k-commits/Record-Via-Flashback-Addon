package com.rvf.record;

import com.rvf.AddonConfig;
import com.rvf.RVF;
import com.rvf.action.ActionCameraType;
import com.rvf.action.ActionChatSuggestions;
import com.rvf.action.ActionChatTyping;
import com.rvf.action.ActionCreativeScreen;
import com.rvf.action.ActionEffects;
import com.rvf.action.ActionInventoryExtras;
import com.rvf.action.ActionInventoryScreen;
import com.rvf.action.ActionSpyglass;
import com.rvf.client.InventoryScreenState;
import com.rvf.client.ReplayChatSuggestions;
import com.rvf.client.ReplayChatTyping;
import com.rvf.client.ReplayCreativeScreen;
import com.rvf.client.ReplayInventoryExtras;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Everything the addon records. Called from MixinRecorder while a recording is running. */
public final class RecorderHooks {

    private RecorderHooks() {}

    private static InventoryScreenState lastInventory = null;
    private static String lastInventoryExtrasKey = null;
    private static String lastCreativeKey = null;
    private static int lastCameraType = -1;
    private static String lastEffectsKey = null;
    private static int effectsTick = 0;
    private static int lastSpyglass = -1;
    private static String lastChatKey = null;
    private static String lastChatSuggestionsKey = null;
    private static boolean errorLogged = false;

    public static void reset() {
        com.rvf.voice.VoiceCapture.reset();
        com.rvf.voice.SystemAudioCapture.reset();
        lastInventory = null;
        lastInventoryExtrasKey = null;
        lastCreativeKey = null;
        lastCameraType = -1;
        lastEffectsKey = null;
        lastSpyglass = -1;
        lastChatKey = null;
        lastChatSuggestionsKey = null;
    }

    public static void tick(AsyncReplaySaver saver, int replayTick) {
        write(saver, false, replayTick);
    }

    public static void snapshot(AsyncReplaySaver saver, int replayTick) {
        write(saver, true, replayTick);
    }

    private static void write(AsyncReplaySaver saver, boolean force, int replayTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        AddonConfig config = AddonConfig.get();
        try {
            if (config.recordInventory) {
                writeInventoryScreen(saver, minecraft, player, force);
                writeInventoryExtras(saver, minecraft, force);
                writeCreativeScreen(saver, minecraft, force);
            }
            if (config.recordPerspective) {
                writeCameraType(saver, minecraft, force);
            }
            if (config.recordEffects) {
                writeEffects(saver, player, force);
            }
            if (config.recordSpyglass) {
                writeSpyglass(saver, player, force);
            }
            if (config.recordChatTyping) {
                writeChatTyping(saver, minecraft, force);
                writeChatSuggestions(saver, minecraft, force);
            }
            if (config.recordVoice) {
                com.rvf.voice.VoiceCapture.tick(saver, replayTick);
            }
            if (config.recordSystemAudio) {
                com.rvf.voice.SystemAudioCapture.tick(saver);
            }
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                RVF.LOGGER.error("Failed to record addon data", e);
            }
        }
    }

    /** Records the open container screen (all slots, the cursor-held item and the mouse position). */
    private static void writeInventoryScreen(AsyncReplaySaver saver, Minecraft minecraft, LocalPlayer player, boolean force) {
        InventoryScreenState state = InventoryScreenState.capture(minecraft, player);
        if (!force && state.sameAs(lastInventory)) {
            return;
        }
        lastInventory = state;

        saver.submit(writer -> {
            writer.startAction(ActionInventoryScreen.INSTANCE);
            state.encode(writer.friendlyByteBuf());
            writer.finishAction(ActionInventoryScreen.INSTANCE);
        });
    }

    /** Records extras of the open inventory screen (the recipe book). */
    private static void writeInventoryExtras(AsyncReplaySaver saver, Minecraft minecraft, boolean force) {
        ReplayInventoryExtras.Snapshot snapshot = ReplayInventoryExtras.captureCurrent(minecraft);
        String key = snapshot.key();
        if (!force && key.equals(lastInventoryExtrasKey)) {
            return;
        }
        lastInventoryExtrasKey = key;

        saver.submit(writer -> {
            writer.startAction(ActionInventoryExtras.INSTANCE);
            ReplayInventoryExtras.encode(writer.friendlyByteBuf(), snapshot);
            writer.finishAction(ActionInventoryExtras.INSTANCE);
        });
    }

    /** Records the tabs, background, scroll bar and search text of the creative inventory. */
    private static void writeCreativeScreen(AsyncReplaySaver saver, Minecraft minecraft, boolean force) {
        ReplayCreativeScreen.Snapshot snapshot = ReplayCreativeScreen.captureCurrent(minecraft);
        String key = snapshot.key();
        if (!force && key.equals(lastCreativeKey)) {
            return;
        }
        lastCreativeKey = key;

        saver.submit(writer -> {
            writer.startAction(ActionCreativeScreen.INSTANCE);
            ReplayCreativeScreen.encode(writer.friendlyByteBuf(), snapshot);
            writer.finishAction(ActionCreativeScreen.INSTANCE);
        });
    }

    /** Records the camera perspective (F5). */
    private static void writeCameraType(AsyncReplaySaver saver, Minecraft minecraft, boolean force) {
        int type = minecraft.options.getCameraType().ordinal();
        if (!force && type == lastCameraType) {
            return;
        }
        lastCameraType = type;

        saver.submit(writer -> {
            writer.startAction(ActionCameraType.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(type);
            writer.finishAction(ActionCameraType.INSTANCE);
        });
    }

    /** Records the local player's active potion effects. Writes when they change, once per second, and on snapshots. */
    private static void writeEffects(AsyncReplaySaver saver, LocalPlayer player, boolean force) {
        List<MobEffectInstance> effects = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
            key.append(effect.getDescriptionId()).append(':').append(effect.getAmplifier()).append(':')
                .append(effect.isAmbient()).append(':').append(effect.isVisible()).append(';');
        }

        boolean periodic = (++effectsTick % 20) == 0;
        String keyString = key.toString();
        if (!force && !periodic && keyString.equals(lastEffectsKey)) {
            return;
        }
        lastEffectsKey = keyString;

        saver.submit(writer -> {
            writer.startAction(ActionEffects.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(effects.size());
            for (MobEffectInstance effect : effects) {
                MobEffectInstance.STREAM_CODEC.encode(writer.friendlyByteBuf(), effect);
            }
            writer.finishAction(ActionEffects.INSTANCE);
        });
    }

    /** Records whether the player is looking through a spyglass. */
    private static void writeSpyglass(AsyncReplaySaver saver, LocalPlayer player, boolean force) {
        int scoping = player.isScoping() ? 1 : 0;
        if (!force && scoping == lastSpyglass) {
            return;
        }
        lastSpyglass = scoping;

        saver.submit(writer -> {
            writer.startAction(ActionSpyglass.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(scoping);
            writer.finishAction(ActionSpyglass.INSTANCE);
        });
    }

    /**
     * Records whether the chat input is open and what has been typed so far.
     * Slash commands are hidden before they are written, except messaging commands such as /msg (see ReplayChatTyping.redact).
     */
    private static void writeChatTyping(AsyncReplaySaver saver, Minecraft minecraft, boolean force) {
        boolean chatOpen = minecraft.screen instanceof ChatScreen;
        String typed = "";
        int typedCursor = 0;
        if (chatOpen) {
            for (GuiEventListener child : minecraft.screen.children()) {
                if (child instanceof EditBox box) {
                    String rawTyped = box.getValue();
                    typed = ReplayChatTyping.redact(rawTyped);
                    // When text is hidden the cursor goes to the end, so its position can't reveal the length
                    typedCursor = typed.equals(rawTyped) ? Math.min(box.getCursorPosition(), typed.length()) : typed.length();
                    break;
                }
            }
        }

        String key = chatOpen ? (typedCursor + ":" + typed) : null;
        if (!force && Objects.equals(key, lastChatKey)) {
            return;
        }
        lastChatKey = key;

        final boolean writeOpen = chatOpen;
        final String writeText = typed;
        final int writeCursor = typedCursor;
        saver.submit(writer -> {
            writer.startAction(ActionChatTyping.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(writeOpen ? 1 : 0);
            if (writeOpen) {
                writer.friendlyByteBuf().writeUtf(writeText);
                writer.friendlyByteBuf().writeVarInt(writeCursor);
            }
            writer.finishAction(ActionChatTyping.INSTANCE);
        });
    }

    /** Records the suggestions popup shown while typing in chat. Nothing is recorded while any part of the text is hidden. */
    private static void writeChatSuggestions(AsyncReplaySaver saver, Minecraft minecraft, boolean force) {
        ReplayChatSuggestions.Snapshot snapshot = ReplayChatSuggestions.captureCurrent(minecraft);
        String key = snapshot.key();
        if (!force && key.equals(lastChatSuggestionsKey)) {
            return;
        }
        lastChatSuggestionsKey = key;

        saver.submit(writer -> {
            writer.startAction(ActionChatSuggestions.INSTANCE);
            ReplayChatSuggestions.encode(writer.friendlyByteBuf(), snapshot);
            writer.finishAction(ActionChatSuggestions.INSTANCE);
        });
    }

}
