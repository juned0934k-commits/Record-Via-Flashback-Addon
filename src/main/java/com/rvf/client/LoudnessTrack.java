package com.rvf.client;

import com.rvf.RVF;
import com.rvf.voice.VoiceLoudness;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Draws the lime voice row in Flashback's timeline window, in the spare row under the keyframe tracks
 * (the timeline part of that row is empty; the "add keyframe" button is in the left column).
 * The row is two lines tall and the waveform is mirrored around its center line and smoothed between ticks.
 * Flashback's private timeline fields are read with reflection so no Flashback types are needed at compile time.
 */
public final class LoudnessTrack {

    private LoudnessTrack() {}

    private static final int WAVE_COLOUR = 0xFF30E09C;
    private static final int BACKGROUND_COLOUR = 0x3030E09C;
    private static final float ROW_LINES = 2.0f;

    private static byte[] smoothSource = null;
    private static float[] smoothValues = null;

    /**
     * Turns the raw per-tick loudness into a smooth envelope: gaps between loud ticks are filled by letting each peak
     * fade out over the neighbouring ticks, and the loudest part of the clip is scaled to the full row height.
     */
    private static float[] smoothed(byte[] data) {
        if (data == smoothSource && smoothValues != null) {
            return smoothValues;
        }
        int n = data.length;
        float[] env = new float[n];
        float peak = 1.0f;
        for (int i = 0; i < n; i++) {
            env[i] = data[i] & 0xFF;
            peak = Math.max(peak, env[i]);
        }
        for (int i = 1; i < n; i++) {
            env[i] = Math.max(env[i], env[i - 1] * 0.85f);
        }
        for (int i = n - 2; i >= 0; i--) {
            env[i] = Math.max(env[i], env[i + 1] * 0.85f);
        }
        float gain = 255.0f / Math.max(peak, 64.0f);
        for (int i = 0; i < n; i++) {
            env[i] = Math.min(255.0f, env[i] * gain);
        }
        smoothSource = data;
        smoothValues = env;
        return env;
    }

    private static boolean ready = false;
    private static boolean broken = false;
    private static Field fieldScene;
    private static Field fieldWidth;
    private static Field fieldMiddleX;
    private static Field fieldTimelineWidth;
    private static Field fieldTracks;
    private static Method methodTickToX;

    private static void init() throws ReflectiveOperationException {
        Class<?> window = Class.forName("com.moulberry.flashback.editor.ui.windows.TimelineWindow");
        fieldScene = field(window, "editorScene");
        fieldWidth = field(window, "width");
        fieldMiddleX = field(window, "middleX");
        fieldTimelineWidth = field(window, "timelineWidth");
        methodTickToX = window.getDeclaredMethod("replayTickToTimelineX", int.class);
        methodTickToX.setAccessible(true);
        ready = true;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static float number(Field f) throws IllegalAccessException {
        return ((Number) f.get(null)).floatValue();
    }

    private static float at(float[] values, int index) {
        return index < 0 || index >= values.length ? 0.0f : values[index];
    }

    /** Called at the end of TimelineWindow.renderKeyframes (inside the timeline's clip rectangle). */
    public static void draw(float x, float y, int minTicks, float availableTicks) {
        byte[] data = VoiceLoudness.getPlayback();
        if (broken || data == null || data.length == 0) {
            return;
        }
        try {
            if (!ready) {
                init();
            }
            float[] wave = smoothed(data);
            Object scene = fieldScene.get(null);
            if (scene == null) {
                return;
            }
            if (fieldTracks == null) {
                fieldTracks = field(scene.getClass(), "keyframeTracks");
            }
            int tracks = ((List<?>) fieldTracks.get(scene)).size();

            float middleX = number(fieldMiddleX);
            float width = number(fieldWidth);
            float timelineWidth = number(fieldTimelineWidth);
            if (availableTicks <= 0 || timelineWidth <= 0) {
                return;
            }

            float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
            float top = y + 2 + tracks * lineHeight;
            float bottom = top + lineHeight * ROW_LINES - 2;
            float middle = (top + bottom) / 2.0f;
            float halfHeight = (bottom - top) / 2.0f;
            float left = x + middleX + 1;
            float right = x + width;

            float pixelsPerTick = timelineWidth / availableTicks;
            float originX = x + (Integer) methodTickToX.invoke(null, minTicks);

            float startX = Math.max(left, originX + (0 - minTicks) * pixelsPerTick);
            float endX = Math.min(right, originX + (data.length - minTicks) * pixelsPerTick);
            if (endX <= startX) {
                return;
            }

            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.addRectFilled(startX, top, endX, bottom, BACKGROUND_COLOUR);

            for (int px = (int) Math.floor(startX); px < endX; px++) {
                if (px < left) {
                    continue;
                }
                float t = minTicks + (px + 0.5f - originX) / pixelsPerTick;
                float value;
                if (pixelsPerTick >= 1.0f) {
                    // zoomed in: blend smoothly between neighbouring ticks
                    int i0 = (int) Math.floor(t);
                    float frac = t - i0;
                    frac = frac * frac * (3.0f - 2.0f * frac);
                    float a = at(wave, i0);
                    float b = at(wave, i0 + 1);
                    value = a + (b - a) * frac;
                } else {
                    // zoomed out: loudest tick under this pixel column
                    int from = Math.max(0, (int) Math.floor(t - 0.5f / pixelsPerTick));
                    int to = Math.min(data.length - 1, (int) Math.floor(t + 0.5f / pixelsPerTick));
                    value = 0.0f;
                    for (int i = from; i <= to; i++) {
                        value = Math.max(value, wave[i]);
                    }
                }
                if (value < 1.0f) {
                    continue;
                }
                float h = Math.max(1.0f, (bottom - top) * value / 255.0f);
                drawList.addRectFilled(px, bottom - h, px + 1, bottom, WAVE_COLOUR);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            RVF.LOGGER.error("Could not draw the voice track in the timeline", e);
        }
    }

}
