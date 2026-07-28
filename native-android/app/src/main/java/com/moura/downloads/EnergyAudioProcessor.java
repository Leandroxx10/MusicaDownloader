package com.moura.downloads;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Transparent PCM processor that measures the real audio without modifying it.
 */
@UnstableApi
public final class EnergyAudioProcessor extends BaseAudioProcessor {
    private static final int FFT_SIZE = 1024;
    private static final double MIN_FREQUENCY = 55.0;
    private static final double MAX_FREQUENCY = 16_000.0;

    private final float[] pcm = new float[FFT_SIZE];
    private final double[] real = new double[FFT_SIZE];
    private final double[] imaginary = new double[FFT_SIZE];
    private final float[] smoothedBands =
            new float[AudioEnergyBus.BAND_COUNT];

    private int pcmCount;
    private int channelCount = 2;
    private int sampleRate = 44_100;
    private int encoding = C.ENCODING_PCM_16BIT;

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws AudioProcessor.UnhandledAudioFormatException {
        if (!isSupportedEncoding(inputAudioFormat.encoding)) {
            throw new AudioProcessor.UnhandledAudioFormatException(
                    inputAudioFormat);
        }
        channelCount = Math.max(1, inputAudioFormat.channelCount);
        sampleRate = Math.max(8_000, inputAudioFormat.sampleRate);
        encoding = inputAudioFormat.encoding;
        return inputAudioFormat;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int remaining = inputBuffer.remaining();
        if (remaining == 0) return;

        if (AudioEnergyBus.isEnabled()) {
            ByteBuffer analysis = inputBuffer.duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN);
            analysePcm(analysis);
        } else {
            pcmCount = 0;
        }

        ByteBuffer output = replaceOutputBuffer(remaining);
        output.put(inputBuffer);
        output.flip();
    }

    @Override
    protected void onFlush() {
        pcmCount = 0;
    }

    @Override
    protected void onReset() {
        pcmCount = 0;
        for (int index = 0; index < smoothedBands.length; index++) {
            smoothedBands[index] = 0f;
        }
        AudioEnergyBus.clear();
    }

    private void analysePcm(ByteBuffer buffer) {
        int bytesPerSample = bytesPerSample(encoding);
        int bytesPerFrame = bytesPerSample * channelCount;
        while (buffer.remaining() >= bytesPerFrame) {
            float mono = 0f;
            for (int channel = 0; channel < channelCount; channel++) {
                mono += readSample(buffer, encoding);
            }
            pcm[pcmCount++] = mono / channelCount;
            if (pcmCount == FFT_SIZE) {
                publishSpectrum();
                pcmCount = 0;
            }
        }
    }

    private void publishSpectrum() {
        double sumSquares = 0.0;
        for (int index = 0; index < FFT_SIZE; index++) {
            double sample = pcm[index];
            sumSquares += sample * sample;
            double window = 0.5 - 0.5 * Math.cos(
                    (2.0 * Math.PI * index) / (FFT_SIZE - 1));
            real[index] = sample * window;
            imaginary[index] = 0.0;
        }

        fft(real, imaginary);
        float energy = clamp((float) (
                Math.sqrt(sumSquares / FFT_SIZE) * 3.8));
        float[] output = new float[AudioEnergyBus.BAND_COUNT];
        double maxFrequency = Math.min(
                MAX_FREQUENCY, sampleRate / 2.0 - 1.0);
        double frequencyRatio = maxFrequency / MIN_FREQUENCY;

        for (int band = 0; band < output.length; band++) {
            double lowerFrequency = MIN_FREQUENCY * Math.pow(
                    frequencyRatio, band / (double) output.length);
            double upperFrequency = MIN_FREQUENCY * Math.pow(
                    frequencyRatio, (band + 1.0) / output.length);
            int firstBin = Math.max(1, (int) Math.floor(
                    lowerFrequency * FFT_SIZE / sampleRate));
            int lastBin = Math.min(
                    FFT_SIZE / 2 - 1,
                    Math.max(firstBin, (int) Math.ceil(
                            upperFrequency * FFT_SIZE / sampleRate)));

            double magnitudeSum = 0.0;
            int binCount = 0;
            for (int bin = firstBin; bin <= lastBin; bin++) {
                double magnitude = Math.hypot(real[bin], imaginary[bin])
                        / (FFT_SIZE * 0.5);
                magnitudeSum += magnitude;
                binCount++;
            }
            double magnitude = binCount == 0 ? 0.0 : magnitudeSum / binCount;
            float target = clamp((float) (
                    Math.log1p(magnitude * 24.0) / Math.log(25.0)));
            float previous = smoothedBands[band];
            float response = target > previous ? 0.68f : 0.22f;
            smoothedBands[band] = previous + (target - previous) * response;
            output[band] = smoothedBands[band];
        }
        AudioEnergyBus.publish(energy, output);
    }

    private static void fft(double[] real, double[] imaginary) {
        int size = real.length;
        for (int source = 1, target = 0; source < size; source++) {
            int bit = size >> 1;
            while ((target & bit) != 0) {
                target ^= bit;
                bit >>= 1;
            }
            target ^= bit;
            if (source < target) {
                double swap = real[source];
                real[source] = real[target];
                real[target] = swap;
                swap = imaginary[source];
                imaginary[source] = imaginary[target];
                imaginary[target] = swap;
            }
        }

        for (int length = 2; length <= size; length <<= 1) {
            double angle = -2.0 * Math.PI / length;
            double stepReal = Math.cos(angle);
            double stepImaginary = Math.sin(angle);
            for (int offset = 0; offset < size; offset += length) {
                double rotationReal = 1.0;
                double rotationImaginary = 0.0;
                for (int index = 0; index < length / 2; index++) {
                    int even = offset + index;
                    int odd = even + length / 2;
                    double oddReal = real[odd] * rotationReal
                            - imaginary[odd] * rotationImaginary;
                    double oddImaginary = real[odd] * rotationImaginary
                            + imaginary[odd] * rotationReal;
                    real[odd] = real[even] - oddReal;
                    imaginary[odd] = imaginary[even] - oddImaginary;
                    real[even] += oddReal;
                    imaginary[even] += oddImaginary;
                    double nextReal = rotationReal * stepReal
                            - rotationImaginary * stepImaginary;
                    rotationImaginary = rotationReal * stepImaginary
                            + rotationImaginary * stepReal;
                    rotationReal = nextReal;
                }
            }
        }
    }

    private static boolean isSupportedEncoding(int value) {
        return value == C.ENCODING_PCM_8BIT
                || value == C.ENCODING_PCM_16BIT
                || value == C.ENCODING_PCM_24BIT
                || value == C.ENCODING_PCM_32BIT
                || value == C.ENCODING_PCM_FLOAT;
    }

    private static int bytesPerSample(int value) {
        if (value == C.ENCODING_PCM_8BIT) return 1;
        if (value == C.ENCODING_PCM_24BIT) return 3;
        if (value == C.ENCODING_PCM_32BIT
                || value == C.ENCODING_PCM_FLOAT) return 4;
        return 2;
    }

    private static float readSample(ByteBuffer buffer, int value) {
        if (value == C.ENCODING_PCM_8BIT) {
            return ((buffer.get() & 0xff) - 128) / 128f;
        }
        if (value == C.ENCODING_PCM_16BIT) {
            return buffer.getShort() / 32768f;
        }
        if (value == C.ENCODING_PCM_FLOAT) {
            float sample = buffer.getFloat();
            return Float.isFinite(sample)
                    ? Math.max(-1f, Math.min(1f, sample)) : 0f;
        }
        if (value == C.ENCODING_PCM_24BIT) {
            int sample = (buffer.get() & 0xff)
                    | ((buffer.get() & 0xff) << 8)
                    | (buffer.get() << 16);
            return sample / 8_388_608f;
        }
        return (float) (buffer.getInt() / 2_147_483_648.0);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
