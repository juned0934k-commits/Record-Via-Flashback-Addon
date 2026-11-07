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
 * Plays recorded audio while watching a replay. Chunks are queued and written to the sound card on their own
 * thread, so the game never waits for the audio device.
 * There are two independent players: VOICE (the recorded microphone) and SYSTEM (recorded Discord / system audio),
 * so both can play at the same time.
 */
public final class VoicePlayer {

    public static final VoicePlayer VOICE = new VoicePlayer("RVF-VoicePlayer");
    public static final VoicePlayer SYSTEM = new VoicePlayer("RVF-SystemPlayer");

    private record Chunk(int sampleRate, byte[] pcm) {}

    private static final int MAX_QUEUED_CHUNKS = 400;
    private static final int PREBUFFER_MS = 80;

    private final String threadName;
    private final BlockingQueue<Chunk> queue = new LinkedBlockingQueue<>();
    private Thread thread = null;
    private boolean errorLogged = false;

    private VoicePlayer(String threadName) {
        this.threadName = threadName;
    }

    /** Plays a microphone chunk (kept so existing callers keep working). */
    public static void play(int sampleRate, byte[] pcm) {
        VOICE.playChunk(sampleRate, pcm);
    }

    /** Drops everything that is still queued, on both players. */
    public static void clear() {
        VOICE.queue.clear();
        SYSTEM.queue.clear();
    }

    public void playChunk(int sampleRate, byte[] pcm) {
        if (pcm.length < 2 || sampleRate < 8000 || sampleRate > 96000) {
            return;
        }
        ensureThread();
        if (this.queue.size() >= MAX_QUEUED_CHUNKS) {
            return;
        }
        this.queue.add(new Chunk(sampleRate, pcm));
    }

    private synchronized void ensureThread() {
        if (this.thread != null && this.thread.isAlive()) {
            return;
        }
        this.thread = new Thread(this::run, this.threadName);
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void run() {
        SourceDataLine line = null;
        int lineRate = 0;
        try {
            while (true) {
                Chunk chunk = this.queue.poll(5, TimeUnit.SECONDS);
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
            if (!this.errorLogged) {
                this.errorLogged = true;
                RVF.LOGGER.error("Audio playback stopped because of an error", e);
            }
        } finally {
            closeLine(line);
        }
    }

    private SourceDataLine openLine(int sampleRate) {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format, sampleRate); // about half a second of buffer
            line.start();
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            if (!this.errorLogged) {
                this.errorLogged = true;
                RVF.LOGGER.error("Could not open an audio output for the recorded audio", e);
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
