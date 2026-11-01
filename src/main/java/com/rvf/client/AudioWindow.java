package com.rvf.client;

import com.rvf.AddonConfig;
import com.rvf.voice.SystemAudioCapture;
import com.rvf.voice.WasapiLoopback;
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
    private static boolean testSystem = false;
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

            FLOAT_BUFFER[0] = config.voiceVolume;
            if (ImGui.sliderFloat("Volume##RvfVoiceVolume", FLOAT_BUFFER, 0.0f, 4.0f, "%.2f")) {
                config.voiceVolume = FLOAT_BUFFER[0];
                changed = true;
            }
            FLOAT_BUFFER[0] = config.voiceBassDb;
            if (ImGui.sliderFloat("Bass boost (dB)##RvfVoiceBass", FLOAT_BUFFER, 0.0f, 12.0f, "%.1f")) {
                config.voiceBassDb = FLOAT_BUFFER[0];
                changed = true;
            }
            ImGui.textUnformatted("Volume and bass apply when you watch the replay or export a video.");

            changed |= renderSystemAudio(config);

            ImGuiHelper.separatorWithText("Test microphone");

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

    /** The "Discord / system audio" section. Returns true if a setting was changed. */
    private static boolean renderSystemAudio(AddonConfig config) {
        boolean changed = false;
        ImGuiHelper.separatorWithText("Discord / system audio");

        if (ImGui.checkbox("Record Discord / system audio##RvfRecordSystem", config.recordSystemAudio)) {
            config.recordSystemAudio = !config.recordSystemAudio;
            changed = true;
        }
        ImGui.textUnformatted("Choose \"Default speakers (loopback)\" to record what your speakers play. It records everything, game sound too.");
        ImGui.textUnformatted("Use headphones, so your microphone does not pick up Discord a second time.");

        boolean none = config.systemDevice == null || config.systemDevice.isBlank();
        String current = none ? "(none chosen)" : config.systemDevice;
        if (ImGui.beginCombo("Input##RvfSystemInput", current)) {
            if (ImGui.selectable(WasapiLoopback.DEVICE_NAME + "##RvfSystemLoopback", WasapiLoopback.DEVICE_NAME.equals(config.systemDevice))) {
                config.systemDevice = WasapiLoopback.DEVICE_NAME;
                changed = true;
            }
            if (ImGui.selectable("(none)##RvfSystemNone", none)) {
                config.systemDevice = "";
                changed = true;
            }
            List<String> names = devices();
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                if (ImGui.selectable(name + "##RvfSystemInput" + i, name.equals(config.systemDevice))) {
                    config.systemDevice = name;
                    changed = true;
                }
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh##RvfSystemRefresh")) {
            devices = null;
        }

        FLOAT_BUFFER[0] = config.systemThresholdDb;
        if (ImGui.sliderFloat("Threshold (dB)##RvfSystemThreshold", FLOAT_BUFFER, -80.0f, 0.0f, "%.0f")) {
            config.systemThresholdDb = FLOAT_BUFFER[0];
            changed = true;
        }

        FLOAT_BUFFER[0] = config.systemVolume;
        if (ImGui.sliderFloat("Volume##RvfSystemVolume", FLOAT_BUFFER, 0.0f, 4.0f, "%.2f")) {
            config.systemVolume = FLOAT_BUFFER[0];
            changed = true;
        }

        if (ImGui.checkbox("Test input##RvfTestSystem", testSystem)) {
            testSystem = !testSystem;
        }
        if (testSystem) {
            if (none) {
                ImGui.textUnformatted("Choose an input first.");
            } else {
                SystemAudioCapture.testTick();
                float db = SystemAudioCapture.currentLevelDb();
                float fraction = Math.max(0.0f, Math.min(1.0f, (db + 80.0f) / 80.0f));
                String label = String.format("%.0f dB  %s", db, db >= config.systemThresholdDb ? "(recorded)" : "(below threshold)");
                ImGui.progressBar(fraction, -1.0f, 0.0f, label);
            }
        }
        return changed;
    }

    private static List<String> devices() {
        if (devices == null) {
            devices = new ArrayList<>(VoiceCapture.listDevices());
        }
        return devices;
    }

}