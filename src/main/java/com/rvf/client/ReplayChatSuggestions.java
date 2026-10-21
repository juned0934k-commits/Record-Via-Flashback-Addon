package com.rvf.client;

import com.mojang.brigadier.suggestion.Suggestion;
import com.rvf.RVF;
import com.rvf.mixin.MixinChatScreenAccessor;
import com.rvf.mixin.MixinCommandSuggestionsAccessor;
import com.rvf.mixin.MixinSuggestionsListAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Records and draws the suggestions popup (command names, player names...) that shows while typing in chat.
 * Nothing is recorded while any part of the typed text is hidden by ReplayChatTyping.redact().
 * If the accessors can't be used (for example after a Minecraft change), suggestions are switched off and the
 * rest of the addon keeps working.
 */
public final class ReplayChatSuggestions {

    private ReplayChatSuggestions() {}

    /** Vanilla shows at most 10 suggestions at a time. */
    private static final int WINDOW = 10;
    private static final int MAX_TEXT_LENGTH = 64;

    /** The visible part of the suggestions list. "selected" is the highlighted row inside "items". */
    public record Snapshot(List<String> items, int rangeStart, int selected) {
        public static final Snapshot EMPTY = new Snapshot(List.of(), 0, 0);

        /** Changes whenever anything visible changes. */
        public String key() {
            if (this.items.isEmpty()) {
                return "";
            }
            return this.rangeStart + ":" + this.selected + ":" + String.join("\u0001", this.items);
        }
    }

    // ---- recording ----

    private static boolean failed = false;

    public static Snapshot captureCurrent(Minecraft minecraft) {
        if (failed) {
            return Snapshot.EMPTY;
        }
        try {
            return capture(minecraft);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            RVF.LOGGER.error("Chat suggestion recording is switched off because it failed", e);
            return Snapshot.EMPTY;
        }
    }

    private static Snapshot capture(Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof ChatScreen chatScreen)) {
            return Snapshot.EMPTY;
        }

        EditBox box = null;
        for (GuiEventListener child : chatScreen.children()) {
            if (child instanceof EditBox found) {
                box = found;
                break;
            }
        }
        if (box == null) {
            return Snapshot.EMPTY;
        }

        // Never record anything that was derived from text that is hidden in the recording
        String raw = box.getValue();
        if (!ReplayChatTyping.redact(raw).equals(raw)) {
            return Snapshot.EMPTY;
        }

        CommandSuggestions commandSuggestions = ((MixinChatScreenAccessor) chatScreen).rvf$getCommandSuggestions();
        if (commandSuggestions == null) {
            return Snapshot.EMPTY;
        }
        CommandSuggestions.SuggestionsList suggestionsList = ((MixinCommandSuggestionsAccessor) commandSuggestions).rvf$getSuggestionsList();
        if (suggestionsList == null) {
            return Snapshot.EMPTY;
        }

        MixinSuggestionsListAccessor accessor = (MixinSuggestionsListAccessor) suggestionsList;
        List<Suggestion> all = accessor.rvf$getSuggestionList();
        if (all == null || all.isEmpty()) {
            return Snapshot.EMPTY;
        }
        int offset = Math.max(0, Math.min(accessor.rvf$getOffset(), all.size() - 1));
        int end = Math.min(all.size(), offset + WINDOW);

        List<String> items = new ArrayList<>(end - offset);
        for (int i = offset; i < end; i++) {
            String text = all.get(i).getText();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            items.add(text);
        }

        int selected = Math.max(0, Math.min(accessor.rvf$getCurrent() - offset, items.size() - 1));
        return new Snapshot(items, Math.max(0, all.get(0).getRange().getStart()), selected);
    }

    public static void encode(RegistryFriendlyByteBuf buf, Snapshot snapshot) {
        buf.writeVarInt(snapshot.items().size());
        for (String item : snapshot.items()) {
            buf.writeUtf(item);
        }
        if (!snapshot.items().isEmpty()) {
            buf.writeVarInt(snapshot.rangeStart());
            buf.writeVarInt(snapshot.selected());
        }
    }

    public static Snapshot decode(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 64);
        if (count <= 0) {
            return Snapshot.EMPTY;
        }
        List<String> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add(buf.readUtf());
        }
        int rangeStart = buf.readVarInt();
        int selected = buf.readVarInt();
        return new Snapshot(items, rangeStart, selected);
    }

    // ---- replay ----

    private static int entityId = -1;
    private static Snapshot snapshot = Snapshot.EMPTY;

    public static void set(int id, Snapshot newSnapshot) {
        entityId = id;
        snapshot = newSnapshot;
    }

    public static void clear() {
        entityId = -1;
        snapshot = Snapshot.EMPTY;
    }

    /** Draws the popup just above the typing bar, lined up with the word being completed. */
    public static void render(GuiGraphicsExtractor guiGraphics, Font font, int chatEntityId, String typedText, int scrollStart) {
        Snapshot current = snapshot;
        if (current.items().isEmpty() || entityId != chatEntityId) {
            return;
        }

        int rows = current.items().size();
        int width = 0;
        for (String item : current.items()) {
            width = Math.max(width, font.width(item));
        }

        int from = Math.max(scrollStart, Math.min(current.rangeStart(), typedText.length()));
        int x = 4 + font.width(typedText.substring(scrollStart, from));
        x = Math.max(0, Math.min(x, guiGraphics.guiWidth() - width));
        int y = guiGraphics.guiHeight() - 14 - rows * 12;

        guiGraphics.fill(x - 1, y, x + width + 1, y + rows * 12, 0xD0000000);
        for (int i = 0; i < rows; i++) {
            int color = i == current.selected() ? 0xFFFFFF00 : 0xFFAAAAAA;
            guiGraphics.text(font, current.items().get(i), x, y + 2 + i * 12, color, true);
        }
    }

}
