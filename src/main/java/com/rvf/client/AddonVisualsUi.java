package com.rvf.client;

import com.rvf.AddonConfig;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;

/** The addon's checkboxes, drawn at the bottom of Flashback's Visuals window. */
public final class AddonVisualsUi {

    private AddonVisualsUi() {}

    public static void render() {
        AddonConfig config = AddonConfig.get();
        boolean changed = false;

        ImGuiHelper.separatorWithText("Record Via Flashback");

        if (ImGui.checkbox("Inventory##AddonInventory", config.showInventory)) {
            config.showInventory = !config.showInventory;
            changed = true;
        }
        if (ImGui.checkbox("Effects##AddonEffects", config.showEffects)) {
            config.showEffects = !config.showEffects;
            changed = true;
        }
        if (ImGui.checkbox("Spyglass##AddonSpyglass", config.showSpyglass)) {
            config.showSpyglass = !config.showSpyglass;
            changed = true;
        }
        if (ImGui.checkbox("Chat Typing##AddonChatTyping", config.showChatTyping)) {
            config.showChatTyping = !config.showChatTyping;
            changed = true;
        }
        if (ImGui.checkbox("Voice##AddonVoice", config.showVoice)) {
            config.showVoice = !config.showVoice;
            changed = true;
        }
        if (ImGui.checkbox("Auto Perspective##AddonAutoPerspective", config.autoPerspective)) {
            config.autoPerspective = !config.autoPerspective;
            changed = true;
            if (config.autoPerspective) {
                ReplayPerspective.apply();
            }
        }

        if (changed) {
            AddonConfig.save();
        }
    }

}
