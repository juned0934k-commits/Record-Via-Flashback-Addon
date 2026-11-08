package com.rvf.client;

import com.rvf.AddonConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** In-game menu (opened with /rvf config) where the player chooses what gets recorded. Saved to config/rvf.json when closed. */
public class RvfConfigScreen extends Screen {

    private final Screen parent;

    public RvfConfigScreen(Screen parent) {
        super(Component.literal("Record Via Flashback"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AddonConfig config = AddonConfig.get();

        int w = 220;
        int x = this.width / 2 - w / 2;
        int top = Math.max(20, this.height / 2 - 100);

        this.addRenderableWidget(new StringWidget(x, top, w, 9, Component.literal("What to record"), this.font));

        int y = top + 20;
        toggle(x, y, w, "Inventory", config.recordInventory, v -> config.recordInventory = v);
        y += 24;
        toggle(x, y, w, "Potion effects", config.recordEffects, v -> config.recordEffects = v);
        y += 24;
        toggle(x, y, w, "Spyglass", config.recordSpyglass, v -> config.recordSpyglass = v);
        y += 24;
        toggle(x, y, w, "Chat typing", config.recordChatTyping, v -> config.recordChatTyping = v);
        y += 24;
        toggle(x, y, w, "Camera perspective", config.recordPerspective, v -> config.recordPerspective = v);
        y += 24;
        toggle(x, y, w, "Microphone voice", config.recordVoice, v -> config.recordVoice = v);
        y += 24;
        toggle(x, y, w, "Discord / system audio", config.recordSystemAudio, v -> config.recordSystemAudio = v);
        y += 30;

        this.addRenderableWidget(new StringWidget(x, y, w, 9, Component.literal("Applies to new recordings."), this.font));
        y += 16;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
            .bounds(x, y, w, 20).build());
    }

    private void toggle(int x, int y, int w, String name, boolean initial, Consumer<Boolean> setter) {
        boolean[] state = {initial};
        Button button = Button.builder(label(name, state[0]), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            b.setMessage(label(name, state[0]));
        }).bounds(x, y, w, 20).build();
        this.addRenderableWidget(button);
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void removed() {
        AddonConfig.save();
    }

}
