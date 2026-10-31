package com.rvf.voice;

import com.moulberry.flashback.Flashback;
import com.rvf.AddonConfig;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Puts the recorded microphone voice into the audio of an exported video.
 * While exporting, ActionVoice hands each voice chunk to add(), which converts it to 48 kHz mono floats and queues it.
 * After Flashback renders the game audio of each frame, mix() adds the next queued samples to that frame's buffer.
 * When the queue is empty (the player was silent) nothing is added, so silence stays silent.
 */
public final class ExportVoice {

    private ExportVoice() {}

    private static final int OUTPUT_RATE = 48000;
    private static final int MAX_PENDING = OUTPUT_RATE * 10;

    private static final Object LOCK = new Object();
    private static float[] pending = new float[OUTPUT_RATE];
    private static int pendingLength = 0;
    private static Object lastJob = null;

    /** Called by ActionVoice (replay thread) for every voice chunk while an export is running. */
    public static void add(int sampleRate, byte[] pcm) {
        if (!AddonConfig.get().showVoice || sampleRate < 8000 || sampleRate > 96000 || pcm.length < 2) {
            return;
        }
        int inSamples = pcm.length / 2;
        int outSamples = (int) ((long) inSamples * OUTPUT_RATE / sampleRate);
        if (outSamples <= 0) {
            return;
        }

        synchronized (LOCK) {
            if (pendingLength + outSamples > MAX_PENDING) {
                return;
            }
            if (pendingLength + outSamples > pending.length) {
                pending = Arrays.copyOf(pending, Math.max(pendingLength + outSamples, pending.length * 2));
            }
            double step = (double) sampleRate / OUTPUT_RATE;
            for (int i = 0; i < outSamples; i++) {
                double position = i * step;
                int i0 = (int) position;
                int i1 = Math.min(i0 + 1, inSamples - 1);
                double fraction = position - i0;
                float a = sample(pcm, i0);
                float b = sample(pcm, i1);
                pending[pendingLength + i] = (float) (a + (b - a) * fraction);
            }
            pendingLength += outSamples;
        }
    }

    /** Called on the render thread right after Flashback filled the frame's audio buffer with the game sound. */
    public static void mix(FloatBuffer buffer, int samples) {
        if (buffer == null || samples <= 0) {
            return;
        }
        int channels = Math.max(1, buffer.capacity() / samples);

        synchronized (LOCK) {
            Object job = Flashback.EXPORT_JOB;
            if (job != lastJob) {
                // A new export started: drop voice that was queued while the replay jumped to the start
                lastJob = job;
                pendingLength = 0;
            }

            int count = Math.min(samples, pendingLength);
            for (int i = 0; i < count; i++) {
                float voice = pending[i];
                for (int c = 0; c < channels; c++) {
                    int index = i * channels + c;
                    float mixed = buffer.get(index) + voice;
                    buffer.put(index, Math.max(-1.0f, Math.min(1.0f, mixed)));
                }
            }
            if (count > 0) {
                System.arraycopy(pending, count, pending, 0, pendingLength - count);
                pendingLength -= count;
            }
        }
    }

    private static float sample(byte[] pcm, int index) {
        short value = (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8));
        return value / 32768.0f;
    }

}
