package com.rvf.client;

import com.rvf.AddonConfig;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Set;

/**
 * Remembers whether the recorded player had the chat input open and what they had typed,
 * and draws a chat input bar for it in the replay.
 */
public final class ReplayChatTyping {

    private ReplayChatTyping() {}

    /**
     * Commands whose arguments are chat text and are shown as typed (private messages and the like).
     * Every OTHER slash command has its arguments hidden, so passwords, PINs and 2FA codes are never recorded,
     * even for commands this list has never heard of.
     */
    private static final Set<String> VISIBLE_COMMANDS = Set.of(
        "msg", "w", "whisper", "tell", "r", "reply",
        "me", "say", "teammsg", "tm"
    );

    /** Fixed length, so the length of a hidden argument can't be guessed. */
    private static final String MASK = "********";

    private static int entityId = -1;
    private static boolean open = false;
    private static String text = "";
    private static int cursor = 0;

    public static void set(int id, boolean isOpen, String newText, int newCursor) {
        entityId = id;
        open = isOpen;
        text = newText;
        cursor = newCursor;
    }

    public static void clear() {
        entityId = -1;
        open = false;
        text = "";
        cursor = 0;
    }

    /**
     * Applied while recording, before anything is written. Plain chat is returned unchanged.
     * For a slash command that is not in VISIBLE_COMMANDS, everything after the command word becomes a fixed mask,
     * for example "/login hunter2" is stored as "/login ********".
     */
    public static String redact(String input) {
        if (!input.startsWith("/")) {
            return input;
        }

        int space = input.indexOf(' ');
        if (space < 0 || space + 1 >= input.length()) {
            return input; // only the command word so far, nothing to hide yet
        }

        String command = input.substring(1, space).toLowerCase(Locale.ROOT);
        int colon = command.lastIndexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1); // "minecraft:msg" counts as "msg"
        }

        if (VISIBLE_COMMANDS.contains(command)) {
            return input;
        }
        return input.substring(0, space + 1) + MASK;
    }

    /** False if switched off in the addon config, or if Flashback's own Visuals "Chat" option is off. */
    private static boolean visible() {
        if (!AddonConfig.get().showChatTyping) {
            return false;
        }
        if (Flashback.isInReplay() && (ReplayUI.isActive() || Flashback.isExporting())) {
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState != null && !editorState.replayVisuals.showChat) {
                return false;
            }
        }
        return true;
    }

    public static void render(GuiGraphics guiGraphics) {
        if (!open || !Flashback.isInReplay()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null || camera == minecraft.player || camera.getId() != entityId) {
            return;
        }
        if (minecraft.screen instanceof ChatScreen) {
            return;
        }
        if (!visible()) {
            return;
        }

        Font font = minecraft.font;
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();

        guiGraphics.fill(2, height - 14, width - 2, height - 2, 0x80000000);

        int maxWidth = width - 12;
        int cur = Math.max(0, Math.min(cursor, text.length()));

        // If the text is too long, scroll so that the cursor stays visible
        int start = 0;
        while (start < cur && font.width(text.substring(start, cur)) > maxWidth - 6) {
            start++;
        }

        String visibleText = font.plainSubstrByWidth(text.substring(start), maxWidth);
        int caretX = 4 + font.width(text.substring(start, cur));

        guiGraphics.drawString(font, visibleText, 4, height - 12, 0xFFE0E0E0, true);

        if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            if (cur < text.length()) {
                guiGraphics.fill(caretX, height - 13, caretX + 1, height - 3, 0xFFE0E0E0);
            } else {
                guiGraphics.drawString(font, "_", caretX, height - 12, 0xFFE0E0E0, true);
            }
        }

        ReplayChatSuggestions.render(guiGraphics, font, entityId, text, start);
    }

}
