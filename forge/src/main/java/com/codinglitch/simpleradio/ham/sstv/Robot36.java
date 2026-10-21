package com.codinglitch.simpleradio.ham.sstv;

import com.codinglitch.simpleradio.ham.radio.CwGenerator;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Slow scan television in the Robot 36 mode (VIS code 8): 240 lines of 320 pixels in 36 seconds, luminance
 * on every line and the two colour differences on alternating lines. A 128x128 map is letterboxed into the
 * picture. The audio is the real thing - an SSTV app listening to the speaker can show the picture.
 */
public final class Robot36 {
    public static final int LINES = 240;
    public static final int WIDTH = 320;
    public static final double LINE_MS = 150;
    private static final int VIS_CODE = 8;
    private static final int MAP = 128;

    private Robot36() {}

    /**
     * The encoded picture.
     * @param frames 20 ms audio frames
     * @param lineStartMs when every line starts (from the beginning of the audio)
     */
    public record Encoded(List<short[]> frames, double[] lineStartMs) {}

    private static final class Tone {
        short[] samples = new short[1_800_000];
        int size = 0;
        final double peak;
        double phase = 0;
        double time = 0;      // ms written so far (exact)
        double written = 0;   // samples written so far

        Tone(double amplitude) {
            this.peak = Math.max(0, Math.min(1, amplitude)) * Short.MAX_VALUE;
        }

        void tone(double hz, double ms) {
            time += ms;
            double target = time * CwGenerator.SAMPLE_RATE / 1000.0;
            double step = 2 * Math.PI * hz / CwGenerator.SAMPLE_RATE;
            while (written < target) {
                if (size == samples.length) samples = java.util.Arrays.copyOf(samples, samples.length * 2);
                samples[size++] = (short) Math.round(Math.sin(phase) * peak);
                phase += step;
                written++;
            }
            phase %= 2 * Math.PI;
        }
    }

    private static double frequency(double value) {
        return 1500 + Math.max(0, Math.min(255, value)) * 800 / 255.0;
    }

    /**
     * RGB (0-255) of the map pixel under a picture pixel; the map sits in the middle 240x240 square.
     */
    private static int[] rgb(byte[] colors, int x, int line) {
        int mapX = (x - 40) * MAP / 240;
        int mapY = line * MAP / 240;
        if (mapX < 0 || mapX >= MAP || mapY >= MAP) return new int[]{0, 0, 0};

        int abgr = MapColor.getColorFromPackedId(colors[mapY * MAP + mapX] & 255);
        return new int[]{abgr & 255, (abgr >> 8) & 255, (abgr >> 16) & 255};
    }

    private static double luminance(int[] c) {
        return 16 + (65.738 * c[0] + 129.057 * c[1] + 25.064 * c[2]) / 256;
    }

    private static double redDifference(int[] c) {
        return 128 + (112.439 * c[0] - 94.154 * c[1] - 18.285 * c[2]) / 256;
    }

    private static double blueDifference(int[] c) {
        return 128 + (-37.945 * c[0] - 74.494 * c[1] + 112.439 * c[2]) / 256;
    }

    public static Encoded encode(byte[] colors, double amplitude) {
        Tone out = new Tone(amplitude);

        // VIS header: leader, break, leader, start bit, 7 data bits (LSB first), even parity, stop bit
        out.tone(1900, 300);
        out.tone(1200, 10);
        out.tone(1900, 300);
        out.tone(1200, 30);
        int parity = 0;
        for (int bit = 0; bit < 7; bit++) {
            boolean one = ((VIS_CODE >> bit) & 1) == 1;
            if (one) parity++;
            out.tone(one ? 1100 : 1300, 30);
        }
        out.tone(parity % 2 == 1 ? 1100 : 1300, 30);
        out.tone(1200, 30);

        double[] lineStart = new double[LINES];
        for (int line = 0; line < LINES; line++) {
            lineStart[line] = out.time;
            out.tone(1200, 9);   // sync
            out.tone(1500, 3);   // porch
            for (int x = 0; x < WIDTH; x++) out.tone(frequency(luminance(rgb(colors, x, line))), 88.0 / WIDTH);

            boolean even = line % 2 == 0;
            out.tone(even ? 1500 : 2300, 4.5); // separator tells which colour difference follows
            out.tone(1900, 1.5);
            int pair = even ? line + 1 : line - 1;
            for (int x = 0; x < WIDTH / 2; x++) {
                int[] a = rgb(colors, x * 2, line), b = rgb(colors, x * 2, Math.min(LINES - 1, pair));
                double value = even ? (redDifference(a) + redDifference(b)) / 2 : (blueDifference(a) + blueDifference(b)) / 2;
                out.tone(frequency(value), 44.0 / (WIDTH / 2.0));
            }
        }

        int frameCount = (out.size + CwGenerator.FRAME_SIZE - 1) / CwGenerator.FRAME_SIZE;
        List<short[]> frames = new ArrayList<>(frameCount);
        for (int f = 0; f < frameCount; f++) {
            short[] frame = new short[CwGenerator.FRAME_SIZE];
            for (int i = 0; i < CwGenerator.FRAME_SIZE; i++) {
                int index = f * CwGenerator.FRAME_SIZE + i;
                frame[i] = index < out.size ? out.samples[index] : 0;
            }
            frames.add(frame);
        }
        return new Encoded(frames, lineStart);
    }

    /**
     * Map rows (0-127) a picture line carries.
     */
    public static int mapRow(int line) {
        return Math.min(MAP - 1, line * MAP / LINES);
    }
}
