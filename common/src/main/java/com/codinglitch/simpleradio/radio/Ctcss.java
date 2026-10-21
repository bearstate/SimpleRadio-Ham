package com.codinglitch.simpleradio.radio;

import java.util.Locale;

/**
 * CTCSS (sub-audible tone squelch): a low tone sent under the voice. Repeaters only open for users
 * sending their access tone, and a receiver with tone squelch only opens for transmitters sending it.
 */
public final class Ctcss {
    /** The 50 standard EIA tones in Hz. */
    public static final float[] TONES = {
            67.0f, 69.3f, 71.9f, 74.4f, 77.0f, 79.7f, 82.5f, 85.4f, 88.5f, 91.5f,
            94.8f, 97.4f, 100.0f, 103.5f, 107.2f, 110.9f, 114.8f, 118.8f, 123.0f, 127.3f,
            131.8f, 136.5f, 141.3f, 146.2f, 151.4f, 156.7f, 159.8f, 162.2f, 165.5f, 167.9f,
            171.3f, 173.8f, 177.3f, 179.9f, 183.5f, 186.2f, 189.9f, 192.8f, 196.6f, 199.5f,
            203.5f, 206.5f, 210.7f, 218.1f, 225.7f, 229.1f, 233.6f, 241.8f, 250.3f, 254.1f
    };

    private Ctcss() {}

    public static boolean matches(float sent, float expected) {
        return expected <= 0 || Math.abs(sent - expected) < 0.5f;
    }

    /**
     * @return the index of a tone in {@link #TONES}, or -1 when off / not a standard tone
     */
    public static int indexOf(float tone) {
        for (int i = 0; i < TONES.length; i++) {
            if (Math.abs(TONES[i] - tone) < 0.05f) return i;
        }
        return -1;
    }

    /**
     * Snaps any value to the nearest standard tone; zero or less means off.
     */
    public static float normalize(float tone) {
        if (tone <= 0) return 0;
        float best = TONES[0];
        for (float candidate : TONES) {
            if (Math.abs(candidate - tone) < Math.abs(best - tone)) best = candidate;
        }
        return best;
    }

    /**
     * Next / previous tone in the list, cycling through "off".
     */
    public static float step(float tone, int direction) {
        int index = indexOf(tone);
        int next = index + direction;
        if (next < -1) next = TONES.length - 1;
        if (next >= TONES.length) next = -1;
        return next < 0 ? 0 : TONES[next];
    }

    public static String display(float tone) {
        return tone <= 0 ? "OFF" : String.format(Locale.ROOT, "%.1f Hz", tone);
    }
}
