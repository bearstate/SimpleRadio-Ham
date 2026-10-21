package com.codinglitch.simpleradio.ham.radio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays pre-rendered 20 ms frames out in real time. Receivers only buffer a fraction of a second,
 * so longer audio (a morse ID, an SSTV picture) has to be sent as it would be spoken.
 */
public class PacedAudio {
    private static final long FRAME_NANOS = 20_000_000L;
    /** How much faster than real time audio is played out; only game tests change it. */
    public static double speed = 1;

    private final ArrayDeque<short[]> pending = new ArrayDeque<>();
    private long start = -1;
    private long sent = 0;

    public synchronized void enqueue(List<short[]> frames) {
        if (pending.isEmpty()) {
            start = System.nanoTime();
            sent = 0;
        }
        pending.addAll(frames);
    }

    public synchronized boolean isPlaying() {
        return !pending.isEmpty();
    }

    public synchronized int remainingFrames() {
        return pending.size();
    }

    public synchronized void clear() {
        pending.clear();
    }

    /**
     * @return the frames that are due by now (a couple in advance, so there is no gap between ticks)
     */
    public synchronized List<short[]> poll() {
        List<short[]> due = new ArrayList<>();
        if (pending.isEmpty()) return due;

        long elapsedFrames = (long) ((System.nanoTime() - start) * speed / FRAME_NANOS) + 3;
        while (!pending.isEmpty() && sent < elapsedFrames) {
            due.add(pending.poll());
            sent++;
        }
        return due;
    }
}
