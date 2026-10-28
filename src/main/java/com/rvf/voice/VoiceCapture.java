package com.rvf.voice;

import com.moulberry.flashback.io.AsyncReplaySaver;
import com.rvf.AddonConfig;
import com.rvf.RVF;
import com.rvf.action.ActionVoice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records the player's microphone while a Flashback recording is running.
 * A background thread reads the mic and applies a noise gate; the recorder tick (game thread) collects what was
 * captured since the last tick and writes it into the replay as one action.
 * The mic is only open while recording ticks keep arriving, and only if "recordVoice" is on.
 */
public final class VoiceCapture {

    private VoiceCapture() {}

    private static final int[] SAMPLE_RATES = {16000, 24000, 44100, 48000};
    private static final int FRAME_MS = 20;
    private static final int PRE_ROLL_FRAMES = 6;                 // 120 ms kept from before the voice starts
    private static final long HOLD_NANOS = 400_000_000L;          // gate stays open 400 ms after the last loud frame
    private static final long IDLE_STOP_NANOS = 3_000_000_000L;   // close the mic when recording ticks stop
    private static final long RETRY_NANOS = 5_000_000_000L;
    private static final int MAX_CHUNK_BYTES = 32 * 1024;
    private static final int MAX_QUEUED_BYTES = 512 * 1024;

    private static final ConcurrentLinkedQueue<byte[]> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED_BYTES = new AtomicInteger();

    private static volatile long lastTickNanos = 0L;
    private static volatile long failedAtNanos = 0L;
    private static volatile boolean failed = false;
    private static volatile int sampleRate = 16000;
    private static volatile float levelDb = -120f;
    private static volatile long lastVoiceNanos = 0L;

    /** True while a recording is capturing the microphone and a voice was heard in the last moment. */
    public static boolean isSpeaking() {
        long now = System.nanoTime();
        return now - lastTickNanos < 500_000_000L && now - lastVoiceNanos < 250_000_000L;
    }
    private static Thread thread = null;
    private static boolean failureLogged = false;
    private static boolean notFoundLogged = false;

    /** Called every recording tick while "recordVoice" is on. */
    public static void tick(AsyncReplaySaver saver, int replayTick) {
        VoiceLoudness.record(replayTick, takeTickPeak());
        lastTickNanos = System.nanoTime();
        ensureRunning();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] part;
        while ((part = QUEUE.poll()) != null) {
            QUEUED_BYTES.addAndGet(-part.length);
            out.write(part, 0, part.length);
            if (out.size() >= MAX_CHUNK_BYTES) {
                submit(saver, out.toByteArray());
                out.reset();
            }
        }
        if (out.size() > 0) {
            submit(saver, out.toByteArray());
        }
    }

    /** Keeps the microphone open for the settings window's level meter (nothing is recorded). */
    public static void testTick() {
        lastTickNanos = System.nanoTime();
        ensureRunning();
        QUEUE.clear();
        QUEUED_BYTES.set(0);
    }

    /** Called when a recording starts. */
    public static void reset() {
        QUEUE.clear();
        QUEUED_BYTES.set(0);
    }

    /** Loudness of the last captured frame in dB (about -120 = silence, 0 = full scale). For a level meter later. */
    public static float currentLevelDb() {
        return levelDb;
    }

    private static void submit(AsyncReplaySaver saver, byte[] data) {
        final int rate = sampleRate;
        saver.submit(writer -> {
            writer.startAction(ActionVoice.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(rate);
            writer.friendlyByteBuf().writeByteArray(data);
            writer.finishAction(ActionVoice.INSTANCE);
        });
    }

    private static synchronized void ensureRunning() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        if (failed && System.nanoTime() - failedAtNanos < RETRY_NANOS) {
            return;
        }
        failed = false;
        QUEUE.clear();
        QUEUED_BYTES.set(0);
        thread = new Thread(VoiceCapture::run, "RVF-Microphone");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        TargetDataLine line = null;
        try {
            AudioFormat format = null;
            for (int rate : SAMPLE_RATES) {
                AudioFormat candidate = new AudioFormat(rate, 16, 1, true, false);
                line = openLine(candidate);
                if (line != null) {
                    format = candidate;
                    break;
                }
            }
            if (line == null || format == null) {
                fail("No microphone could be opened for voice recording", null);
                return;
            }

            sampleRate = (int) format.getSampleRate();
            line.start();
            RVF.LOGGER.info("Microphone recording started at {} Hz", sampleRate);

            int frameSamples = sampleRate * FRAME_MS / 1000;
            byte[] frame = new byte[frameSamples * 2];
            ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
            long lastVoice = System.nanoTime() - HOLD_NANOS - 1L;
            boolean wasOpen = false;

            while (System.nanoTime() - lastTickNanos < IDLE_STOP_NANOS) {
                int read = 0;
                while (read < frame.length) {
                    int n = line.read(frame, read, frame.length - read);
                    if (n <= 0) {
                        fail("The microphone stopped delivering audio", null);
                        return;
                    }
                    read += n;
                }

                double db = frameDb(frame, frameSamples);
                levelDb = (float) db;
                long now = System.nanoTime();
                if (db >= AddonConfig.get().voiceThresholdDb) {
                    lastVoice = now;
                    lastVoiceNanos = now;
                }
                boolean gateOpen = now - lastVoice < HOLD_NANOS;
                if (gateOpen) {
                    notePeak(db);
                }

                if (gateOpen) {
                    if (!wasOpen) {
                        for (byte[] old : preRoll) {
                            enqueue(old);
                        }
                        preRoll.clear();
                    }
                    enqueue(frame.clone());
                } else {
                    preRoll.addLast(frame.clone());
                    while (preRoll.size() > PRE_ROLL_FRAMES) {
                        preRoll.removeFirst();
                    }
                }
                wasOpen = gateOpen;
            }
        } catch (RuntimeException e) {
            fail("Microphone recording stopped because of an error", e);
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            levelDb = -120f;
            RVF.LOGGER.info("Microphone recording stopped");
        }
    }

    private static final AtomicInteger TICK_PEAK = new AtomicInteger();

    /** Loudest voiced frame since the last recording tick, mapped from -60..0 dB to 1..255. */
    private static void notePeak(double db) {
        int v = (int) Math.round((db + 60.0) / 60.0 * 255.0);
        v = Math.max(1, Math.min(255, v));
        TICK_PEAK.accumulateAndGet(v, Math::max);
    }

    private static int takeTickPeak() {
        return TICK_PEAK.getAndSet(0);
    }

    private static void enqueue(byte[] pcm) {
        if (QUEUED_BYTES.get() > MAX_QUEUED_BYTES) {
            return; // recording ticks stalled, drop instead of growing without limit
        }
        QUEUE.add(pcm);
        QUEUED_BYTES.addAndGet(pcm.length);
    }

    private static double frameDb(byte[] frame, int samples) {
        long sum = 0;
        for (int i = 0; i < samples; i++) {
            int s = (short) ((frame[i * 2] & 0xFF) | (frame[i * 2 + 1] << 8));
            sum += (long) s * s;
        }
        double rms = Math.sqrt(sum / (double) samples);
        return rms <= 0.0 ? -120.0 : 20.0 * Math.log10(rms / 32768.0);
    }

    private static void fail(String message, Throwable error) {
        failed = true;
        failedAtNanos = System.nanoTime();
        if (!failureLogged) {
            failureLogged = true;
            if (error != null) {
                RVF.LOGGER.error(message, error);
            } else {
                RVF.LOGGER.error(message);
            }
        }
    }

    private static TargetDataLine openLine(AudioFormat format) {
        String wanted = AddonConfig.get().micDevice;
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            TargetDataLine line = null;
            if (wanted != null && !wanted.isBlank()) {
                String needle = wanted.toLowerCase(Locale.ROOT);
                for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                    if (!mixerInfo.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                        continue;
                    }
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        line = (TargetDataLine) mixer.getLine(info);
                        break;
                    }
                }
                if (line == null && !notFoundLogged) {
                    notFoundLogged = true;
                    RVF.LOGGER.warn("Microphone '{}' was not found, using the default microphone", wanted);
                }
            }
            if (line == null) {
                if (!AudioSystem.isLineSupported(info)) {
                    return null;
                }
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(format);
            return line;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            return null;
        }
    }

    /** Names of the microphones Java can record from. */
    public static List<String> listDevices() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                for (Line.Info lineInfo : mixer.getTargetLineInfo()) {
                    if (lineInfo instanceof DataLine.Info dataInfo && TargetDataLine.class.isAssignableFrom(dataInfo.getLineClass())) {
                        names.add(mixerInfo.getName());
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // skip devices that cannot be queried
            }
        }
        return names;
    }

    /** Writes the list of microphones to the log (does not open any of them). */
    public static void logDevices() {
        try {
            List<String> names = listDevices();
            RVF.LOGGER.info("Microphones available for voice recording: {}", names.isEmpty() ? "(none found)" : String.join(" | ", names));
        } catch (RuntimeException | LinkageError e) {
            RVF.LOGGER.warn("Could not list microphones", e);
        }
    }

}