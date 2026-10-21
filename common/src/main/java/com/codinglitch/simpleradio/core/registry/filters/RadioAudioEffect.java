package com.codinglitch.simpleradio.core.registry.filters;

import com.codinglitch.simpleradio.radio.AudioEffect;

import java.util.Random;

/**
 * Makes voice sound like it came over a radio link.
 * <ul>
 *     <li>Voice band limiting (300-3400 Hz for FM, 300-2800 Hz for AM, 300-2700 Hz for SSB) with gentle compression.</li>
 *     <li>FM stays clean while the signal is strong; near the edge of the range hiss rises quickly
 *     and the audio starts breaking up (squelch flutter).</li>
 *     <li>AM and SSB always have a faint hiss, and weak signals fade in and out.</li>
 *     <li>HF adds atmospheric static crashes, many more during a thunderstorm.</li>
 *     <li>A receiver tuned off frequency distorts FM, muffles AM and shifts the pitch of SSB.</li>
 * </ul>
 * {@link #severity} (0-100) describes how weak the signal is. One instance should be used per audio
 * source, as the filters keep state between frames.
 */
public class RadioAudioEffect extends AudioEffect {
    public static Random RANDOM = new Random();

    /** Amplitude modulation (noisier, narrower) instead of frequency modulation. */
    public boolean amplitudeModulation = false;
    /** Single sideband: narrow, no carrier, the pitch follows the tuning error. */
    public boolean singleSideband = false;
    /** The signal travelled on the HF bands (atmospheric static). */
    public boolean highFrequency = false;
    /** A thunderstorm is going on (many more static crashes on HF). */
    public boolean stormy = false;
    /** Tuning error in Hz (positive: the audio comes out higher). */
    public int offsetHz = 0;
    public int sampleRate = 48000;

    private final Random random = new Random();

    private final Biquad highPass = new Biquad();
    private final Biquad lowPass = new Biquad();
    private final Biquad lowPass2 = new Biquad();
    private final Biquad noiseShaper = new Biquad();
    private int configuredRate = -1;
    private int configuredMode = -1;
    private int configuredCutoff = -1;

    private final FrequencyShifter shifter = new FrequencyShifter();

    private double fadePhase = RANDOM.nextDouble() * Math.PI * 2;
    private int breakupSamples = 0;
    private int crashSamples = 0;

    public RadioAudioEffect() {}

    private int mode() {
        return singleSideband ? 2 : amplitudeModulation ? 1 : 0;
    }

    private void configure() {
        int mode = mode();
        int cutoff = switch (mode) {
            case 2 -> 2700;
            case 1 -> (int) Math.max(1200, 2800 - Math.abs(offsetHz) * 0.4);
            default -> 3400;
        };
        if (configuredRate == sampleRate && configuredMode == mode && configuredCutoff == cutoff) return;
        configuredRate = sampleRate;
        configuredMode = mode;
        configuredCutoff = cutoff;

        highPass.highPass(sampleRate, 300);
        lowPass.lowPass(sampleRate, cutoff);
        lowPass2.lowPass(sampleRate, cutoff);
        noiseShaper.lowPass(sampleRate, Math.min(3000, cutoff - 300));
    }

    @Override
    public short[] apply(short[] data) {
        configure();

        double s = Math.max(0, Math.min(1, severity / 100.0));
        boolean noisyFloor = amplitudeModulation || singleSideband;
        double offset = Math.abs(offsetHz);

        // an FM receiver tuned too far off distorts: the signal leaves the filter's straight part
        double fmDetune = !noisyFloor ? Math.max(0, Math.min(1, (offset - 1500) / 5000.0)) : 0;

        // hiss grows with the square of the signal weakness; AM and SSB always have a faint noise floor
        double hiss = (noisyFloor ? 0.004 : 0.0) + 0.45 * s * s + 0.12 * fmDetune;

        // FM holds full volume until it is about to drop, AM and SSB get quieter as they weaken
        double gain = noisyFloor ? 1 - 0.45 * s : 1 - 0.6 * Math.max(0, (s - 0.7) / 0.3);
        if (amplitudeModulation) gain *= Math.max(0.3, 1 - offset / 8000.0);

        // FM squelch flutter near the edge of the range (about 2 dropouts/s at s=0.8, 7/s at s=0.95)
        double breakupChance = !noisyFloor && s > 0.65 ? Math.pow((s - 0.65) / 0.35, 2) * 0.0002 : 0;
        breakupChance += fmDetune * 0.00015;
        // fading (QSB) of weak AM and SSB signals
        double fadeDepth = noisyFloor && s > 0.35 ? 0.6 * (s - 0.35) / 0.65 : 0;
        double fadeStep = 2 * Math.PI / (sampleRate * 1.7);
        // HF static crashes (roughly 0.5-2.5 per second, several times that in a thunderstorm)
        double crashChance = highFrequency ? (0.00001 + 0.00004 * s) * (stormy ? 5 : 1) : 0;

        double drive = 1.4 + 6 * fmDetune;

        for (int i = 0; i < data.length; i++) {
            double sample = data[i] / 32768.0;

            // SSB demodulated with a tuning error comes out pitch shifted
            if (singleSideband && offsetHz != 0) sample = shifter.process(sample, offsetHz, sampleRate);

            double signalGain = gain;
            if (fadeDepth > 0) {
                fadePhase += fadeStep;
                signalGain *= 1 - fadeDepth * (0.5 + 0.5 * Math.sin(fadePhase));
            }

            if (breakupSamples <= 0 && breakupChance > 0 && random.nextDouble() < breakupChance) {
                breakupSamples = sampleRate / 1000 * (15 + random.nextInt(50));
            }
            if (breakupSamples > 0) {
                breakupSamples--;
                signalGain *= 0.05;
            }

            double noise = (random.nextDouble() * 2 - 1) * hiss;
            if (breakupSamples > 0) noise *= 1.6; // the squelch opens on noise

            if (crashSamples <= 0 && crashChance > 0 && random.nextDouble() < crashChance) {
                crashSamples = sampleRate / 1000 * (3 + random.nextInt(12));
            }
            if (crashSamples > 0) {
                crashSamples--;
                noise += (random.nextDouble() * 2 - 1) * 0.25;
            }

            double mixed = sample * signalGain + noiseShaper.process(noise);

            // voice band, then compression like a radio's audio stage (hard clipping when badly detuned)
            mixed = lowPass2.process(lowPass.process(highPass.process(mixed)));
            mixed = Math.tanh(mixed * drive) / Math.tanh(drive);

            data[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed * 32767)));
        }

        return super.apply(data);
    }

    /**
     * Shifts every frequency of the audio by the same amount (single sideband modulation of the audio with
     * a Hilbert transformer), which is what a mistuned SSB receiver does to a voice.
     */
    static final class FrequencyShifter {
        private static final int TAPS = 63;
        private static final int DELAY = TAPS / 2;
        private static final double[] HILBERT = new double[TAPS];

        static {
            for (int i = 0; i < TAPS; i++) {
                int n = i - DELAY;
                double window = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (TAPS - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (TAPS - 1));
                HILBERT[i] = n % 2 == 0 ? 0 : 2.0 / (Math.PI * n) * window;
            }
        }

        private final double[] history = new double[TAPS];
        private int position = 0;
        private double phase = 0;

        double process(double sample, int shiftHz, int sampleRate) {
            history[position] = sample;

            double quadrature = 0;
            for (int i = 0; i < TAPS; i++) {
                quadrature += HILBERT[i] * history[(position - i + TAPS) % TAPS];
            }
            double inPhase = history[(position - DELAY + TAPS) % TAPS];
            position = (position + 1) % TAPS;

            phase += 2 * Math.PI * shiftHz / sampleRate;
            if (phase > Math.PI) phase -= 2 * Math.PI;
            if (phase < -Math.PI) phase += 2 * Math.PI;

            return inPhase * Math.cos(phase) - quadrature * Math.sin(phase);
        }
    }

    /**
     * RBJ cookbook biquad (Butterworth Q) with its own state.
     */
    private static final class Biquad {
        private double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        void lowPass(int rate, double frequency) {
            double w = 2 * Math.PI * frequency / rate;
            double alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
            double cos = Math.cos(w);
            double a0 = 1 + alpha;
            b0 = (1 - cos) / 2 / a0;
            b1 = (1 - cos) / a0;
            b2 = (1 - cos) / 2 / a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        void highPass(int rate, double frequency) {
            double w = 2 * Math.PI * frequency / rate;
            double alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
            double cos = Math.cos(w);
            double a0 = 1 + alpha;
            b0 = (1 + cos) / 2 / a0;
            b1 = -(1 + cos) / a0;
            b2 = (1 + cos) / 2 / a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
