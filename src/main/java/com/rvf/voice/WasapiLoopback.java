package com.rvf.voice;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Records what the default speakers play (Windows WASAPI loopback), converted to 16-bit mono PCM.
 * Windows only. The Windows COM interfaces are called through JNA (which Minecraft already ships) by reading their
 * function tables directly, so no native code has to be bundled.
 * Windows delivers no audio at all while nothing is playing, so read() fills the gaps with silence in real time.
 */
public final class WasapiLoopback {

    /** The entry shown in the input list for this capture. */
    public static final String DEVICE_NAME = "Default speakers (loopback)";

    public interface Ole32Library extends Library {
        int CoInitializeEx(Pointer reserved, int coInit);

        void CoUninitialize();

        int CoCreateInstance(Pointer clsid, Pointer outer, int clsContext, Pointer iid, PointerByReference result);

        void CoTaskMemFree(Pointer memory);
    }

    private static final int CLSCTX_ALL = 23;
    private static final int AUDCLNT_SHAREMODE_SHARED = 0;
    private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int AUDCLNT_BUFFERFLAGS_SILENT = 0x2;
    private static final long IDLE_NANOS = 100_000_000L;

    private Ole32Library ole;
    private boolean comInitialized = false;
    private Pointer enumerator;
    private Pointer device;
    private Pointer audioClient;
    private Pointer captureClient;

    private int sampleRate;
    private int channels;
    private boolean floatFormat;
    private int outputRate;
    private double ratio = 1.0;
    private double phase = 0.0;
    private float sum = 0.0f;
    private int count = 0;

    private byte[] pending = new byte[1 << 16];
    private int pendingLength = 0;
    private long lastDataNanos = System.nanoTime();
    private long lastFrameNanos = System.nanoTime();

    private WasapiLoopback() {}

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** Starts capturing the default speakers. Throws if that is not possible. */
    public static WasapiLoopback open() {
        if (!isSupported()) {
            throw new IllegalStateException("Loopback capture only works on Windows");
        }
        WasapiLoopback loopback = new WasapiLoopback();
        try {
            loopback.start();
        } catch (RuntimeException | Error e) {
            loopback.close();
            throw e;
        }
        return loopback;
    }

    /** Sample rate of the produced mono audio. */
    public int sampleRate() {
        return this.outputRate;
    }

    private void start() {
        this.ole = Native.load("ole32", Ole32Library.class);

        int hr = this.ole.CoInitializeEx(null, 0); // multithreaded; S_FALSE (1) means it was already initialized
        this.comInitialized = hr == 0 || hr == 1;

        Memory clsidEnumerator = guid(0xBCDE0395, 0xE52F, 0x467C, 0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E);
        Memory iidEnumerator = guid(0xA95664D2, 0x9614, 0x4F35, 0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6);
        Memory iidAudioClient = guid(0x1CB9AD4C, 0xDBFA, 0x4C32, 0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2);
        Memory iidCaptureClient = guid(0xC8ADBD64, 0xE71E, 0x48A0, 0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17);

        PointerByReference ref = new PointerByReference();
        check(this.ole.CoCreateInstance(clsidEnumerator, null, CLSCTX_ALL, iidEnumerator, ref), "CoCreateInstance");
        this.enumerator = ref.getValue();

        // IMMDeviceEnumerator::GetDefaultAudioEndpoint(eRender = 0, eConsole = 0, &device)
        ref = new PointerByReference();
        check(call(this.enumerator, 4, 0, 0, ref), "GetDefaultAudioEndpoint");
        this.device = ref.getValue();

        // IMMDevice::Activate(IID_IAudioClient, CLSCTX_ALL, null, &audioClient)
        ref = new PointerByReference();
        check(call(this.device, 3, iidAudioClient, CLSCTX_ALL, null, ref), "Activate");
        this.audioClient = ref.getValue();

        // IAudioClient::GetMixFormat(&format)
        PointerByReference formatRef = new PointerByReference();
        check(call(this.audioClient, 8, formatRef), "GetMixFormat");
        Pointer format = formatRef.getValue();
        try {
            int tag = format.getShort(0) & 0xFFFF;
            this.channels = format.getShort(2) & 0xFFFF;
            this.sampleRate = format.getInt(4);
            int bits = format.getShort(14) & 0xFFFF;
            if (tag == 0xFFFE) {
                this.floatFormat = format.getInt(24) == 3; // WAVE_FORMAT_EXTENSIBLE: sub-format 3 = IEEE float
            } else {
                this.floatFormat = tag == 3;
            }
            boolean pcm16 = !this.floatFormat && bits == 16;
            boolean float32 = this.floatFormat && bits == 32;
            if (this.channels < 1 || this.sampleRate < 8000 || !(pcm16 || float32)) {
                throw new IllegalStateException("Unsupported speaker format: " + this.channels + " channels, "
                    + this.sampleRate + " Hz, " + bits + " bit, tag " + tag);
            }

            // Speakers are often set to 96 or 192 kHz. Speech does not need that: reduce to at most 48 kHz
            this.outputRate = Math.min(this.sampleRate, 48000);
            this.ratio = (double) this.sampleRate / this.outputRate;

            // IAudioClient::Initialize(shared, loopback, 1 second buffer, 0, format, null)
            check(call(this.audioClient, 3, AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_LOOPBACK,
                Long.valueOf(10_000_000L), Long.valueOf(0L), format, null), "Initialize");
        } finally {
            this.ole.CoTaskMemFree(format);
        }

        // IAudioClient::GetService(IID_IAudioCaptureClient, &captureClient)
        ref = new PointerByReference();
        check(call(this.audioClient, 14, iidCaptureClient, ref), "GetService");
        this.captureClient = ref.getValue();

        // IAudioClient::Start()
        check(call(this.audioClient, 10), "Start");
    }

    /**
     * Fills the frame with the next samples (16-bit mono, little endian). Returns false if capturing should stop.
     * If nothing is playing, silence is delivered at real-time speed.
     */
    public boolean read(byte[] frame, BooleanSupplier keepRunning) {
        long frameNanos = (long) (frame.length / 2) * 1_000_000_000L / this.outputRate;

        while (this.pendingLength < frame.length) {
            drain();
            if (this.pendingLength >= frame.length) {
                break;
            }
            if (!keepRunning.getAsBoolean()) {
                return false;
            }
            long now = System.nanoTime();
            // Silence is only delivered (in real time) when nothing has played for a while. Short waits between
            // packets must never be filled with silence, that would cut holes into the sound.
            if (now - this.lastDataNanos > IDLE_NANOS && now - this.lastFrameNanos >= frameNanos) {
                int missing = frame.length - this.pendingLength;
                ensure(missing);
                Arrays.fill(this.pending, this.pendingLength, this.pendingLength + missing, (byte) 0);
                this.pendingLength += missing;
                break;
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        this.lastFrameNanos = System.nanoTime();
        System.arraycopy(this.pending, 0, frame, 0, frame.length);
        System.arraycopy(this.pending, frame.length, this.pending, 0, this.pendingLength - frame.length);
        this.pendingLength -= frame.length;
        return true;
    }

    /** Takes every packet Windows has ready and appends it, as mono 16-bit, to the pending bytes. */
    private void drain() {
        IntByReference packetFrames = new IntByReference();
        while (true) {
            // IAudioCaptureClient::GetNextPacketSize(&frames)
            if (call(this.captureClient, 5, packetFrames) != 0 || packetFrames.getValue() == 0) {
                return;
            }
            PointerByReference data = new PointerByReference();
            IntByReference frames = new IntByReference();
            IntByReference flags = new IntByReference();
            // IAudioCaptureClient::GetBuffer(&data, &frames, &flags, null, null)
            if (call(this.captureClient, 3, data, frames, flags, null, null) != 0) {
                return;
            }
            int count = frames.getValue();
            this.lastDataNanos = System.nanoTime();
            if (count > 0) {
                convert(data.getValue(), count, (flags.getValue() & AUDCLNT_BUFFERFLAGS_SILENT) != 0);
            }
            // IAudioCaptureClient::ReleaseBuffer(frames)
            call(this.captureClient, 4, count);
        }
    }

    private void convert(Pointer data, int frames, boolean silent) {
        ensure(frames * 2 + 2);
        if (silent || data == null) {
            for (int i = 0; i < frames; i++) {
                addInput(0.0f);
            }
            return;
        }
        if (this.floatFormat) {
            float[] samples = data.getFloatArray(0, frames * this.channels);
            for (int i = 0; i < frames; i++) {
                float sum = 0.0f;
                for (int c = 0; c < this.channels; c++) {
                    sum += samples[i * this.channels + c];
                }
                addInput(sum / this.channels);
            }
        } else {
            short[] samples = data.getShortArray(0, frames * this.channels);
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < this.channels; c++) {
                    sum += samples[i * this.channels + c];
                }
                addInput((sum / (float) this.channels) / 32768.0f);
            }
        }
    }

    /** Adds one input sample. Every 'ratio' input samples become one output sample (simple averaging). */
    private void addInput(float value) {
        this.sum += value;
        this.count++;
        this.phase += 1.0;
        if (this.phase >= this.ratio) {
            putSample(this.sum / this.count);
            this.sum = 0.0f;
            this.count = 0;
            this.phase -= this.ratio;
        }
    }

    private void putSample(float value) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        short s = (short) Math.round(clamped * 32767.0f);
        this.pending[this.pendingLength++] = (byte) (s & 0xFF);
        this.pending[this.pendingLength++] = (byte) ((s >> 8) & 0xFF);
    }

    private void ensure(int extraBytes) {
        if (this.pendingLength + extraBytes > this.pending.length) {
            this.pending = Arrays.copyOf(this.pending, Math.max(this.pendingLength + extraBytes, this.pending.length * 2));
        }
    }

    public void close() {
        try {
            if (this.audioClient != null) {
                call(this.audioClient, 11); // IAudioClient::Stop
            }
        } catch (RuntimeException ignored) {
            // closing anyway
        }
        release(this.captureClient);
        release(this.audioClient);
        release(this.device);
        release(this.enumerator);
        this.captureClient = null;
        this.audioClient = null;
        this.device = null;
        this.enumerator = null;
        if (this.comInitialized && this.ole != null) {
            try {
                this.ole.CoUninitialize();
            } catch (RuntimeException ignored) {
                // closing anyway
            }
            this.comInitialized = false;
        }
    }

    // ---- COM helpers ----

    private static void check(int hr, String step) {
        if (hr < 0) {
            throw new IllegalStateException(step + " failed (HRESULT 0x" + Integer.toHexString(hr) + ")");
        }
    }

    private static void release(Pointer object) {
        if (object != null) {
            try {
                call(object, 2); // IUnknown::Release
            } catch (RuntimeException ignored) {
                // closing anyway
            }
        }
    }

    /** Calls the COM method at the given function-table index. Index 0-2 are the IUnknown methods. */
    private static int call(Pointer object, int index, Object... args) {
        Pointer table = object.getPointer(0);
        Pointer address = table.getPointer((long) index * Native.POINTER_SIZE);
        Function function = Function.getFunction(address, Function.ALT_CONVENTION);
        Object[] all = new Object[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        return function.invokeInt(all);
    }

    private static Memory guid(int data1, int data2, int data3, int... data4) {
        Memory memory = new Memory(16);
        memory.setInt(0, data1);
        memory.setShort(4, (short) data2);
        memory.setShort(6, (short) data3);
        for (int i = 0; i < 8; i++) {
            memory.setByte(8 + i, (byte) data4[i]);
        }
        return memory;
    }

}
