package com.rvf.voice;

import com.moulberry.flashback.Flashback;
import com.rvf.AddonConfig;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Puts the recorded voices into the audio of an exported video.
 * While exporting, the voice actions hand each chunk to add() (microphone) or addSystem() (Discord / system audio),
 * which convert it to 48 kHz mono floats and queue it, one queue per source so the two can overlap.
 * After Flashback renders the game audio of each frame, mix() adds the next queued samples of each source to that
 * frame's buffer. When a queue is empty (nobody was talking) nothing is added, so silence stays silent.
 */
public final class ExportVoice {

    private ExportVoice() {}

    private static final int OUTPUT_RATE = 48000;
    private static final int MAX_PENDING = OUTPUT_RATE * 10;

    private static final class Pending {
        float[] data = new float[OUTPUT_RATE];
        int length = 0;
    }

    private static final Object LOCK = new Object();
    private static final Pending[] SOURCES = {new Pending(), new Pending()};
    private static Object lastJob = null;

    /** Microphone chunk, called by ActionVoice (replay thread) while an export is running. */
    public static void add(int sampleRate, byte[] pcm) {
        append(SOURCES[0], sampleRate, VoiceFx.micExport(sampleRate, pcm));
    }

    /** Discord / system audio chunk, called by ActionSystemAudio while an export is running. */
    public static void addSystem(int sampleRate, byte[] pcm) {
        append(SOURCES[1], sampleRate, scale(pcm, AddonConfig.get().systemVolume));
    }

    private static void append(Pending target, int sampleRate, byte[] pcm) {
        if (!AddonConfig.get().showVoice || sampleRate < 8000 || sampleRate > 96000 || pcm.length < 2) {
            return;
        }
        int inSamples = pcm.length / 2;
        int outSamples = (int) ((long) inSamples * OUTPUT_RATE / sampleRate);
        if (outSamples <= 0) {
            return;
        }

        synchronized (LOCK) {
            if (target.length + outSamples > MAX_PENDING) {
                return;
            }
            if (target.length + outSamples > target.data.length) {
                target.data = Arrays.copyOf(target.data, Math.max(target.length + outSamples, target.data.length * 2));
            }
            double step = (double) sampleRate / OUTPUT_RATE;
            for (int i = 0; i < outSamples; i++) {
                double position = i * step;
                int i0 = (int) position;
                int i1 = Math.min(i0 + 1, inSamples - 1);
                double fraction = position - i0;
                float a = sample(pcm, i0);
                float b = sample(pcm, i1);
                target.data[target.length + i] = (float) (a + (b - a) * fraction);
            }
            target.length += outSamples;
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
                // A new export started: drop audio that was queued while the replay jumped to the start
                lastJob = job;
                for (Pending source : SOURCES) {
                    source.length = 0;
                }
            }

            for (Pending source : SOURCES) {
                int count = Math.min(samples, source.length);
                for (int i = 0; i < count; i++) {
                    float value = source.data[i];
                    for (int c = 0; c < channels; c++) {
                        int index = i * channels + c;
                        float mixed = buffer.get(index) + value;
                        buffer.put(index, Math.max(-1.0f, Math.min(1.0f, mixed)));
                    }
                }
                if (count > 0) {
                    System.arraycopy(source.data, count, source.data, 0, source.length - count);
                    source.length -= count;
                }
            }
        }
    }

    /** Returns the 16-bit mono PCM multiplied by the gain (clipped), or the same array if the gain is 1. */
    public static byte[] scale(byte[] pcm, float gain) {
        if (Math.abs(gain - 1.0f) < 0.001f) {
            return pcm;
        }
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int v = Math.max(-32768, Math.min(32767, Math.round(s * gain)));
            out[i] = (byte) (v & 0xFF);
            out[i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    private static float sample(byte[] pcm, int index) {
        short value = (short) ((pcm[index * 2] & 0xFF) | (pcm[index * 2 + 1] << 8));
        return value / 32768.0f;
    }

}
