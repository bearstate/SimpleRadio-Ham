package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.radio.HamFrequency;

/**
 * Standing wave ratio: how well an antenna matches the transmitter on a frequency.
 * <ul>
 *     <li>A yagi and the dual band vertical are built for VHF/UHF and reflect most HF power.</li>
 *     <li>A mast of antenna blocks works as a vertical wire: best when it is a quarter wave long.</li>
 *     <li>Without an antenna nearly all power comes back.</li>
 * </ul>
 * Reflected power is lost, the radio turns its power down to protect itself (fold-back) and heats up.
 * An antenna tuner next to the radio matches it again (the antenna itself stays inefficient off its band).
 */
public final class Swr {
    public enum Antenna { NONE, MAST, YAGI, VERTICAL }

    public static final double OPEN = 20;

    private Swr() {}

    public static double of(Antenna antenna, long kHz, int mastHeight) {
        HamFrequency.Band band = HamFrequency.bandOf(kHz);
        return switch (antenna) {
            case NONE -> OPEN;
            case YAGI -> band == HamFrequency.Band.HF ? 12 : 1.3;
            case VERTICAL -> band == HamFrequency.Band.HF ? 15 : band == HamFrequency.Band.VHF ? 1.2 : 1.3;
            case MAST -> {
                if (band != HamFrequency.Band.HF) yield 1.5;
                double quarterWave = 74_948.0 / Math.max(1, kHz); // metres = blocks
                double ratio = Math.max(0.05, mastHeight / quarterWave);
                yield Math.min(OPEN, 1.2 + 6 * Math.abs(Math.log(ratio)));
            }
        };
    }

    /**
     * @return the share of power reflected back to the radio
     */
    public static double reflected(double swr) {
        double gamma = (swr - 1) / (swr + 1);
        return gamma * gamma;
    }

    /**
     * Mismatch loss in dB (power that never reaches the antenna).
     */
    public static double mismatchLossDb(double swr) {
        return -10 * Math.log10(Math.max(1e-3, 1 - reflected(swr)));
    }

    /**
     * Share of the set power the radio still puts out when it protects itself from a high SWR.
     */
    public static double foldback(double swr) {
        double limit = SimpleRadioLibrary.SERVER_CONFIG.swr.foldbackSwr;
        return swr <= limit ? 1 : (limit / swr) * (limit / swr);
    }

    /**
     * SWR the radio sees; a tuner matches anything up to its range.
     */
    public static double atRadio(double antennaSwr, boolean tuner) {
        if (tuner && antennaSwr <= SimpleRadioLibrary.SERVER_CONFIG.swr.tunerMaxSwr) return 1.1;
        return antennaSwr;
    }
}
