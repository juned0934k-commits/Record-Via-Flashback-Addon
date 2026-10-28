package com.rvf.voice;

import com.rvf.RVF;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Plays recorded microphone audio while watching a replay. Chunks are queued and written to the sound card
 * on their own thread, so the game never waits for the audio device.
 */
public final class VoicePlayer {

    private VoicePlayer() {}

    private record Chunk(int sampleRate, byte[] pcm) {}

    private static final int MAX_QUEUED_CHUNKS = 400;
    private static final int PREBUFFER_MS = 80;

    private static final BlockingQueue<Chunk> QUEUE = new LinkedBlockingQueue<>();
    private static Thread thread = null;
    private static boolean errorLogged = false;

    public static void play(int sampleRate, byte[] pcm) {
        if (pcm.length < 2 || sampleRate < 8000 || sampleRate > 96000) {
            return;
        }
        ensureThread();
        if (QUEUE.size() >= MAX_QUEUED_CHUNKS) {
            return;
        }
        QUEUE.add(new Chunk(sampleRate, pcm));
    }

    public static void clear() {
        QUEUE.clear();
    }

    private static synchronized void ensureThread() {
        if (thread != null && thread.isAlive()) {
            return;
        }
        thread = new Thread(VoicePlayer::run, "RVF-VoicePlayer");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        SourceDataLine line = null;
        int lineRate = 0;
        try {
            while (true) {
                Chunk chunk = QUEUE.poll(5, TimeUnit.SECONDS);
                if (chunk == null) {
                    closeLine(line);
                    line = null;
                    continue;
                }
                if (line == null || lineRate != chunk.sampleRate()) {
                    closeLine(line);
                    line = openLine(chunk.sampleRate());
                    lineRate = chunk.sampleRate();
                    if (line == null) {
                        continue;
                    }
                }
                // Line ran empty: add a little silence first so small timing gaps do not crackle
                if (line.available() >= line.getBufferSize() - 2) {
                    int silence = (chunk.sampleRate() * PREBUFFER_MS / 1000) * 2;
                    line.write(new byte[silence], 0, silence);
                }
                byte[] pcm = chunk.pcm();
                line.write(pcm, 0, pcm.length - (pcm.length % 2));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            if (!errorLogged) {
                errorLogged = true;
                RVF.LOGGER.error("Voice playback stopped because of an error", e);
            }
        } finally {
            closeLine(line);
        }
    }

    private static SourceDataLine openLine(int sampleRate) {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, sampleRate); // about half a second of buffer
            line.start();
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            if (!errorLogged) {
                errorLogged = true;
                RVF.LOGGER.error("Could not open an audio output for the recorded voice", e);
            }
            return null;
        }
    }

    private static void closeLine(SourceDataLine line) {
        if (line != null) {
            line.stop();
            line.close();
        }
    }

}