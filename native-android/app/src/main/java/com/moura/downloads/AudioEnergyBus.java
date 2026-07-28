package com.moura.downloads;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight, in-process bridge between the playback audio thread and the UI.
 *
 * <p>The service and the player activity run in the same application process.
 * Publishing immutable snapshots here avoids microphone permissions and keeps
 * the audio thread independent from the screen lifecycle.</p>
 */
public final class AudioEnergyBus {
    public static final int BAND_COUNT = 28;

    public static final class Frame {
        public final long sequence;
        public final long capturedAtNanos;
        public final float energy;
        public final float[] bands;

        private Frame(
                long sequence,
                long capturedAtNanos,
                float energy,
                float[] bands) {
            this.sequence = sequence;
            this.capturedAtNanos = capturedAtNanos;
            this.energy = energy;
            this.bands = bands;
        }
    }

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static volatile boolean enabled;
    private static volatile Frame latest = new Frame(
            0L, 0L, 0f, new float[BAND_COUNT]);

    private AudioEnergyBus() { }

    static void publish(float energy, float[] bands) {
        latest = new Frame(
                SEQUENCE.incrementAndGet(),
                System.nanoTime(),
                clamp(energy),
                Arrays.copyOf(bands, BAND_COUNT));
    }

    public static Frame latest() {
        return latest;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) clear();
    }

    static boolean isEnabled() {
        return enabled;
    }

    public static void clear() {
        latest = new Frame(
                SEQUENCE.incrementAndGet(),
                System.nanoTime(),
                0f,
                new float[BAND_COUNT]);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
