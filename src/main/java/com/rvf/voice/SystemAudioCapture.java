package com.rvf.voice;

import com.moulberry.flashback.io.AsyncReplaySaver;
import com.rvf.AddonConfig;
import com.rvf.RVF;
import com.rvf.action.ActionSystemAudio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records a second audio source while a Flashback recording is running: either a recording device (for example
 * Stereo Mix or a virtual audio cable) or, with the entry "Default speakers (loopback)", whatever the default
 * speakers play. Works like VoiceCapture, but with its own device, threshold and replay action.
 * Unlike the microphone there is no default: nothing is recorded until a source is chosen.
 */
public final class SystemAudioCapture {

    private SystemAudioCapture() {}

    private static final int[] SAMPLE_RATES = {16000, 24000, 44100, 48000};
    private static final int FRAME_MS = 20;
    private static final int PRE_ROLL_FRAMES = 6;
    private static final long HOLD_NANOS = 400_000_000L;
    private static final long IDLE_STOP_NANOS = 3_000_000_000L;
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
    private static Thread thread = null;
    private static boolean failureLogged = false;
    private static boolean notFoundLogged = false;

    /** Something that delivers frames of 16-bit mono audio. */
    private interface FrameSource {
        int sampleRate();

        /** Fills the frame completely. Returns false if the source stopped. */
        boolean read(byte[] frame);

        void close();
    }

    /** Called every recording tick while "recordSystemAudio" is on. */
    public static void tick(AsyncReplaySaver saver) {
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

    /** Keeps the source open for the settings window's level meter (nothing is recorded). */
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

    /** Loudness of the last captured frame in dB (about -120 = silence, 0 = full scale). */
    public static float currentLevelDb() {
        return levelDb;
    }

    private static void submit(AsyncReplaySaver saver, byte[] data) {
        final int rate = sampleRate;
        saver.submit(writer -> {
            writer.startAction(ActionSystemAudio.INSTANCE);
            writer.friendlyByteBuf().writeVarInt(rate);
            writer.friendlyByteBuf().writeByteArray(data);
            writer.finishAction(ActionSystemAudio.INSTANCE);
        });
    }

    private static boolean hasDevice() {
        String wanted = AddonConfig.get().systemDevice;
        return wanted != null && !wanted.isBlank();
    }

    private static synchronized void ensureRunning() {
        if (!hasDevice()) {
            return;
        }
        if (thread != null && thread.isAlive()) {
            return;
        }
        if (failed && System.nanoTime() - failedAtNanos < RETRY_NANOS) {
            return;
        }
        failed = false;
        QUEUE.clear();
        QUEUED_BYTES.set(0);
        thread = new Thread(SystemAudioCapture::run, "RVF-SystemAudio");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        FrameSource source = null;
        try {
            source = openSource();
            if (source == null) {
                fail("The chosen system audio input could not be opened", null);
                return;
            }

            sampleRate = source.sampleRate();
            RVF.LOGGER.info("System audio recording started at {} Hz", sampleRate);

            int frameSamples = sampleRate * FRAME_MS / 1000;
            byte[] frame = new byte[frameSamples * 2];
            ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
            long lastVoice = System.nanoTime() - HOLD_NANOS - 1L;
            boolean wasOpen = false;

            while (System.nanoTime() - lastTickNanos < IDLE_STOP_NANOS) {
                if (!source.read(frame)) {
                    if (System.nanoTime() - lastTickNanos < IDLE_STOP_NANOS) {
                        fail("The system audio input stopped delivering audio", null);
                    }
                    return;
                }

                double db = frameDb(frame, frameSamples);
                levelDb = (float) db;
                long now = System.nanoTime();
                if (db >= AddonConfig.get().systemThresholdDb) {
                    lastVoice = now;
                }
                boolean gateOpen = now - lastVoice < HOLD_NANOS;

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
        } catch (RuntimeException | LinkageError e) {
            fail("System audio recording stopped because of an error", e);
        } finally {
            if (source != null) {
                source.close();
            }
            levelDb = -120f;
            RVF.LOGGER.info("System audio recording stopped");
        }
    }

    private static FrameSource openSource() {
        String wanted = AddonConfig.get().systemDevice;

        if (WasapiLoopback.DEVICE_NAME.equals(wanted)) {
            try {
                WasapiLoopback loopback = WasapiLoopback.open();
                return new FrameSource() {
                    @Override
                    public int sampleRate() {
                        return loopback.sampleRate();
                    }

                    @Override
                    public boolean read(byte[] frame) {
                        return loopback.read(frame, () -> System.nanoTime() - lastTickNanos < IDLE_STOP_NANOS);
                    }

                    @Override
                    public void close() {
                        loopback.close();
                    }
                };
            } catch (RuntimeException | LinkageError e) {
                fail("Could not start recording the default speakers", e);
                return null;
            }
        }

        for (int rate : SAMPLE_RATES) {
            AudioFormat candidate = new AudioFormat(rate, 16, 1, true, false);
            TargetDataLine line = openLine(candidate);
            if (line != null) {
                line.start();
                return new FrameSource() {
                    @Override
                    public int sampleRate() {
                        return rate;
                    }

                    @Override
                    public boolean read(byte[] frame) {
                        int read = 0;
                        while (read < frame.length) {
                            int n = line.read(frame, read, frame.length - read);
                            if (n <= 0) {
                                return false;
                            }
                            read += n;
                        }
                        return true;
                    }

                    @Override
                    public void close() {
                        line.stop();
                        line.close();
                    }
                };
            }
        }
        return null;
    }

    private static void enqueue(byte[] pcm) {
        if (QUEUED_BYTES.get() > MAX_QUEUED_BYTES) {
            return;
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

    /** Opens the chosen recording device only. There is no fallback to the default microphone. */
    private static TargetDataLine openLine(AudioFormat format) {
        String wanted = AddonConfig.get().systemDevice;
        if (wanted == null || wanted.isBlank()) {
            return null;
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            String needle = wanted.toLowerCase(Locale.ROOT);
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (!mixerInfo.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                if (mixer.isLineSupported(info)) {
                    TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                    line.open(format);
                    return line;
                }
            }
            if (!notFoundLogged) {
                notFoundLogged = true;
                RVF.LOGGER.warn("System audio input '{}' was not found or does not support recording", wanted);
            }
            return null;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            return null;
        }
    }

}
