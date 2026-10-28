package com.rvf.voice;

import com.rvf.RVF;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * One loudness byte (0 = no voice, 1-255 = voice level) per replay tick.
 * Recording: filled by VoiceCapture, saved as rvf_loudness.bin in Flashback's record folder when the recording finishes,
 * then added to the replay zip after Flashback has exported it.
 * Playback: read back from the replay zip when the replay opens and drawn as the lime row under the timeline.
 */
public final class VoiceLoudness {

    private VoiceLoudness() {}

    public static final String ENTRY = "rvf_loudness.bin";
    private static final int MAX_TICKS = 4_000_000;

    private static final Object LOCK = new Object();
    private static byte[] recorded = new byte[2400];
    private static int recordedLength = 0;

    private static volatile byte[] playback = null;
    private static final ThreadLocal<byte[]> PENDING_EXPORT = new ThreadLocal<>();

    // ---- recording ----

    public static void startRecording() {
        synchronized (LOCK) {
            recorded = new byte[2400];
            recordedLength = 0;
        }
    }

    /** Keeps the loudest value seen for this replay tick (snapshots call this again for the same tick). */
    public static void record(int tick, int value) {
        if (tick < 0 || tick >= MAX_TICKS) {
            return;
        }
        synchronized (LOCK) {
            if (tick >= recorded.length) {
                recorded = Arrays.copyOf(recorded, Math.max(tick + 1, recorded.length * 2));
            }
            int old = recorded[tick] & 0xFF;
            recorded[tick] = (byte) Math.max(old, Math.max(0, Math.min(255, value)));
            recordedLength = Math.max(recordedLength, tick + 1);
        }
    }

    /** Writes the sidecar file into Flashback's record folder. */
    public static void saveTo(Path recordFolder) {
        if (recordFolder == null) {
            return;
        }
        byte[] data;
        synchronized (LOCK) {
            if (recordedLength == 0) {
                return;
            }
            data = Arrays.copyOf(recorded, recordedLength);
        }
        try {
            Files.write(recordFolder.resolve(ENTRY), data);
        } catch (IOException | RuntimeException e) {
            RVF.LOGGER.warn("Could not save the voice loudness track", e);
        }
    }

    // ---- export (record folder -> replay zip) ----

    /** Called at the start of Flashback's export: the record folder is deleted at its end, so read the file now. */
    public static void beginExport(Path recordFolder) {
        PENDING_EXPORT.remove();
        try {
            Path file = recordFolder.resolve(ENTRY);
            if (Files.exists(file)) {
                PENDING_EXPORT.set(Files.readAllBytes(file));
            }
        } catch (IOException | RuntimeException e) {
            RVF.LOGGER.warn("Could not read the voice loudness track", e);
        }
    }

    /** Called at the end of Flashback's export: adds the loudness file to the finished zip. */
    public static void finishExport(Path zip) {
        byte[] data = PENDING_EXPORT.get();
        PENDING_EXPORT.remove();
        if (data == null || zip == null || !Files.exists(zip)) {
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(zip)) {
            Files.write(fs.getPath("/" + ENTRY), data);
        } catch (IOException | RuntimeException e) {
            RVF.LOGGER.warn("Could not add the voice loudness track to the replay", e);
        }
    }

    // ---- playback ----

    public static void loadFrom(FileSystem replayFileSystem) {
        playback = null;
        if (replayFileSystem == null) {
            return;
        }
        try {
            Path file = replayFileSystem.getPath("/" + ENTRY);
            if (Files.exists(file)) {
                playback = Files.readAllBytes(file);
            }
        } catch (IOException | RuntimeException e) {
            RVF.LOGGER.warn("Could not read the voice loudness track from the replay", e);
        }
    }

    public static byte[] getPlayback() {
        return playback;
    }

}
