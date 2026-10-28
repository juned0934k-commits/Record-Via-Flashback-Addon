package com.rvf.client;

import com.rvf.AddonConfig;
import com.rvf.voice.VoiceCapture;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;

import java.util.ArrayList;
import java.util.List;

/**
 * The "RVF Audio" window in Flashback's replay editor: settings for recording the player's microphone.
 * It is drawn right after the Visuals window and, the first time it appears, docked next to it.
 * Changes apply to new recordings.
 */
public final class AudioWindow {

    private AudioWindow() {}

    private static final float[] FLOAT_BUFFER = new float[]{0};
    private static final long SAVE_DELAY_NANOS = 600_000_000L;

    private static int visualsDockId = 0;
    private static List<String> devices = null;
    private static boolean testMic = false;
    private static boolean dirty = false;
    private static long lastChangeNanos = 0L;

    /** Called while the Visuals window is being drawn, to learn which dock slot it sits in. */
    public static void rememberVisualsDock() {
        int id = ImGui.getWindowDockID();
        if (id != 0) {
            visualsDockId = id;
        }
    }

    public static void render() {
        AddonConfig config = AddonConfig.get();

        if (visualsDockId != 0) {
            ImGui.setNextWindowDockID(visualsDockId, ImGuiCond.FirstUseEver);
        }
        ImGui.setNextWindowSize(340.0f, 280.0f, ImGuiCond.FirstUseEver);

        boolean changed = false;
        if (ImGui.begin("RVF Audio###RvfAudio")) {
            ImGuiHelper.separatorWithText("Microphone recording");

            if (ImGui.checkbox("Record my microphone##RvfRecordVoice", config.recordVoice)) {
                config.recordVoice = !config.recordVoice;
                changed = true;
            }
            ImGui.textUnformatted("Applies to new recordings. The microphone is only used while recording.");

            boolean defaultMic = config.micDevice == null || config.micDevice.isBlank();
            String current = defaultMic ? "Default microphone" : config.micDevice;
            if (ImGui.beginCombo("Microphone##RvfMic", current)) {
                if (ImGui.selectable("Default microphone##RvfMicDefault", defaultMic)) {
                    config.micDevice = "";
                    changed = true;
                }
                List<String> names = devices();
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    if (ImGui.selectable(name + "##RvfMic" + i, name.equals(config.micDevice))) {
                        config.micDevice = name;
                        changed = true;
                    }
                }
                ImGui.endCombo();
            }
            ImGui.sameLine();
            if (ImGui.button("Refresh##RvfMicRefresh")) {
                devices = null;
            }

            FLOAT_BUFFER[0] = config.voiceThresholdDb;
            if (ImGui.sliderFloat("Threshold (dB)##RvfThreshold", FLOAT_BUFFER, -80.0f, 0.0f, "%.0f")) {
                config.voiceThresholdDb = FLOAT_BUFFER[0];
                changed = true;
            }

            ImGuiHelper.separatorWithText("Test");

            if (ImGui.checkbox("Test microphone##RvfTestMic", testMic)) {
                testMic = !testMic;
            }
            if (testMic) {
                VoiceCapture.testTick();
                float db = VoiceCapture.currentLevelDb();
                float fraction = Math.max(0.0f, Math.min(1.0f, (db + 80.0f) / 80.0f));
                String label = String.format("%.0f dB  %s", db, db >= config.voiceThresholdDb ? "(recorded)" : "(below threshold)");
                ImGui.progressBar(fraction, -1.0f, 0.0f, label);
            } else {
                ImGui.textUnformatted("Turn on the test to see the level and set the threshold.");
            }
        }
        ImGui.end();

        // Save a moment after the last change, so dragging the slider does not write the file every frame
        long now = System.nanoTime();
        if (changed) {
            dirty = true;
            lastChangeNanos = now;
        }
        if (dirty && now - lastChangeNanos > SAVE_DELAY_NANOS) {
            dirty = false;
            AddonConfig.save();
        }
    }

    private static List<String> devices() {
        if (devices == null) {
            devices = new ArrayList<>(VoiceCapture.listDevices());
        }
        return devices;
    }

}