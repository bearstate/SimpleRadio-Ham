package com.codinglitch.simpleradio.ham.radio;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders short tone sequences (e.g. a repeater's courtesy beep) into 20 ms frames of 48 kHz PCM.
 * The frames are sent all at once; receiving speakers queue and play them in order.
 */
public final class ToneBurst {
    private static final double RAMP_SECONDS = 0.004;

    private ToneBurst() {}

    /**
     * @param tones pairs of {frequency Hz, duration ms}; a frequency of 0 is a pause
     * @param amplitude 0..1
     */
    public static List<short[]> render(int[][] tones, double amplitude) {
        int total = 0;
        for (int[] tone : tones) total += tone[1] * CwGenerator.SAMPLE_RATE / 1000;

        int frameCount = (total + CwGenerator.FRAME_SIZE - 1) / CwGenerator.FRAME_SIZE;
        short[] samples = new short[frameCount * CwGenerator.FRAME_SIZE];

        double peak = Math.max(0, Math.min(1, amplitude)) * Short.MAX_VALUE;
        int ramp = (int) (RAMP_SECONDS * CwGenerator.SAMPLE_RATE);
        int offset = 0;
        double phase = 0;

        for (int[] tone : tones) {
            int length = tone[1] * CwGenerator.SAMPLE_RATE / 1000;
            double step = 2 * Math.PI * tone[0] / CwGenerator.SAMPLE_RATE;
            for (int i = 0; i < length; i++) {
                double envelope = Math.min(1, Math.min(i, length - 1 - i) / (double) Math.max(1, ramp));
                double value = tone[0] > 0 ? Math.sin(phase) * envelope * peak : 0;
                samples[offset + i] = (short) Math.round(value);
                phase += step;
            }
            offset += length;
        }

        // a faint hiss keeps the frames "valid" audio for the speakers
        java.util.Random random = new java.util.Random();
        List<short[]> frames = new ArrayList<>();
        for (int f = 0; f < frameCount; f++) {
            short[] frame = new short[CwGenerator.FRAME_SIZE];
            for (int i = 0; i < frame.length; i++) {
                int value = samples[f * CwGenerator.FRAME_SIZE + i] + (int) Math.round((random.nextDouble() * 2 - 1) * 12);
                frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
            }
            frames.add(frame);
        }
        return frames;
    }
}
