package com.rvf.voice;

import com.rvf.AddonConfig;

/**
 * Volume and bass boost for the recorded microphone voice. Applied when a replay is played and when a video is
 * exported (not while recording), so the settings also work on old recordings and can be changed any time.
 * The bass boost is a low-shelf filter at 200 Hz (standard "audio EQ cookbook" biquad).
 */
public final class VoiceFx {

    private VoiceFx() {}

    private static final double SHELF_HZ = 200.0;

    private static final Shelf PLAYBACK = new Shelf();
    private static final Shelf EXPORT = new Shelf();

    /** For microphone chunks played in the replay viewer. */
    public static byte[] micPlayback(int sampleRate, byte[] pcm) {
        return process(PLAYBACK, sampleRate, pcm);
    }

    /** For microphone chunks mixed into an exported video. */
    public static byte[] micExport(int sampleRate, byte[] pcm) {
        return process(EXPORT, sampleRate, pcm);
    }

    private static byte[] process(Shelf shelf, int sampleRate, byte[] pcm) {
        AddonConfig config = AddonConfig.get();
        float gain = config.voiceVolume;
        float bassDb = config.voiceBassDb;
        if (sampleRate < 8000 || pcm.length < 2 || (Math.abs(gain - 1.0f) < 0.001f && Math.abs(bassDb) < 0.05f)) {
            return pcm;
        }

        shelf.configure(sampleRate, bassDb);
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            double x = ((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8))) / 32768.0;
            double y = shelf.step(x) * gain;
            int v = (int) Math.round(y * 32768.0);
            v = Math.max(-32768, Math.min(32767, v));
            out[i] = (byte) (v & 0xFF);
            out[i + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    /** A low-shelf filter that keeps its state between chunks, so there are no clicks at chunk borders. */
    private static final class Shelf {
        private int rate = 0;
        private float bassDb = Float.NaN;
        private boolean active = false;
        private double b0;
        private double b1;
        private double b2;
        private double a1;
        private double a2;
        private double x1;
        private double x2;
        private double y1;
        private double y2;

        void configure(int sampleRate, float db) {
            if (sampleRate == this.rate && db == this.bassDb) {
                return;
            }
            this.rate = sampleRate;
            this.bassDb = db;
            this.x1 = 0.0;
            this.x2 = 0.0;
            this.y1 = 0.0;
            this.y2 = 0.0;
            this.active = Math.abs(db) >= 0.05f;
            if (!this.active) {
                return;
            }

            double a = Math.pow(10.0, db / 40.0);
            double w0 = 2.0 * Math.PI * SHELF_HZ / sampleRate;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / Math.sqrt(2.0);
            double twoSqrtAAlpha = 2.0 * Math.sqrt(a) * alpha;

            double a0 = (a + 1.0) + (a - 1.0) * cos + twoSqrtAAlpha;
            this.b0 = a * ((a + 1.0) - (a - 1.0) * cos + twoSqrtAAlpha) / a0;
            this.b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos) / a0;
            this.b2 = a * ((a + 1.0) - (a - 1.0) * cos - twoSqrtAAlpha) / a0;
            this.a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos) / a0;
            this.a2 = ((a + 1.0) + (a - 1.0) * cos - twoSqrtAAlpha) / a0;
        }

        double step(double x) {
            if (!this.active) {
                return x;
            }
            double y = this.b0 * x + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
            this.x2 = this.x1;
            this.x1 = x;
            this.y2 = this.y1;
            this.y1 = y;
            return y;
        }
    }

}
