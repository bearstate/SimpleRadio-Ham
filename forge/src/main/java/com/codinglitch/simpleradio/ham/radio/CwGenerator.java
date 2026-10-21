package com.codinglitch.simpleradio.ham.radio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates the audio of a keyed CW (morse) carrier.
 * <p>
 * Key transitions are timestamped when they arrive and rendered sample-accurately into
 * 20 ms frames of 48 kHz PCM, the format used by Simple Voice Chat. A short raised envelope
 * avoids key clicks. While a keying session is active the gaps between elements are filled
 * with a faint hiss so the receiving audio stream does not stop between dits and dahs.
 */
public class CwGenerator {
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SIZE = 960;

    private static final double RAMP_SECONDS = 0.005;
    private static final long SESSION_TAIL_NANOS = 1_200_000_000L;
    private static final long MAX_KEY_DOWN_NANOS = 30_000_000_000L;

    private record Transition(long time, boolean down) {}

    private final ArrayDeque<Transition> transitions = new ArrayDeque<>();
    private final Random random = new Random();

    private boolean keyDown;          // requested key state
    private boolean renderedDown;     // key state at the current render position
    private long keyDownSince;
    private long lastKeyUp;

    private long sessionStart = -1;   // System.nanoTime() of the first sample
    private long generatedSamples;

    private double phase;
    private double envelope;

    public synchronized void setKey(boolean down) {
        if (down == keyDown) return;
        long now = System.nanoTime();

        keyDown = down;
        if (down) keyDownSince = now;
        else lastKeyUp = now;

        if (sessionStart < 0) {
            if (!down) return;
            sessionStart = now;
            generatedSamples = 0;
            renderedDown = false;
            envelope = 0;
        }
        transitions.add(new Transition(now, down));
    }

    public synchronized boolean isKeyDown() {
        return keyDown;
    }

    /**
     * @return whether a keying session is running (the station should be transmitting)
     */
    public synchronized boolean isActive() {
        return sessionStart >= 0;
    }

    public synchronized void reset() {
        transitions.clear();
        keyDown = false;
        renderedDown = false;
        sessionStart = -1;
        envelope = 0;
    }

    /**
     * Renders every frame that is due up to now.
     * @param toneHz tone frequency
     * @param amplitude 0..1
     */
    public synchronized List<short[]> generate(int toneHz, double amplitude) {
        List<short[]> frames = new ArrayList<>();
        if (sessionStart < 0) return frames;

        long now = System.nanoTime();

        // a stuck key (lost packet, disconnect) is released automatically
        if (keyDown && now - keyDownSince > MAX_KEY_DOWN_NANOS) setKey(false);

        long dueSamples = (now - sessionStart) * SAMPLE_RATE / 1_000_000_000L;
        double rampStep = 1.0 / (RAMP_SECONDS * SAMPLE_RATE);
        double phaseStep = 2 * Math.PI * toneHz / SAMPLE_RATE;
        double peak = Math.max(0, Math.min(1, amplitude)) * Short.MAX_VALUE;

        while (generatedSamples + FRAME_SIZE <= dueSamples) {
            short[] frame = new short[FRAME_SIZE];
            for (int i = 0; i < FRAME_SIZE; i++) {
                long sampleTime = sessionStart + (generatedSamples + i) * 1_000_000_000L / SAMPLE_RATE;
                while (!transitions.isEmpty() && transitions.peek().time() <= sampleTime) {
                    renderedDown = transitions.poll().down();
                }

                if (renderedDown) envelope = Math.min(1, envelope + rampStep);
                else envelope = Math.max(0, envelope - rampStep);

                double shaped = envelope * envelope * (3 - 2 * envelope); // smoothstep
                double tone = Math.sin(phase) * shaped * peak;
                double hiss = (random.nextDouble() * 2 - 1) * 12;

                frame[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(tone + hiss)));

                phase += phaseStep;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
            }
            generatedSamples += FRAME_SIZE;
            frames.add(frame);
        }

        // end the session a moment after the last key release
        if (!keyDown && transitions.isEmpty() && envelope <= 0 && now - lastKeyUp > SESSION_TAIL_NANOS) {
            sessionStart = -1;
        }

        return frames;
    }
}
