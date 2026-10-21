package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;

/**
 * Reception of a transmitter a few kHz away from where the receiver is tuned.
 * <ul>
 *     <li>FM: within about 2 kHz it hardly matters; further off the audio distorts and the signal fades
 *     into the neighbouring channel.</li>
 *     <li>AM: the carrier slides into the filter slope, the audio gets weaker and muffled.</li>
 *     <li>SSB: the audio is shifted in pitch by the tuning error ("Donald Duck").</li>
 * </ul>
 */
public final class OffFrequency {
    /** Beyond this much extra loss (dB) a neighbouring channel is not heard at all. */
    public static final double CUTOFF_DB = 60;

    private OffFrequency() {}

    public static int spreadKHz(Frequency.Modulation modulation) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.frequency.offFrequencyReception) return 0;
        return switch (modulation) {
            case FREQUENCY -> 7;
            case AMPLITUDE -> 4;
            case SINGLE_SIDEBAND -> 3;
        };
    }

    /**
     * Extra loss (dB) of a receiver tuned {@code offsetKHz} away.
     */
    public static double lossDb(Frequency.Modulation modulation, int offsetKHz) {
        int offset = Math.abs(offsetKHz);
        return switch (modulation) {
            case FREQUENCY -> offset <= 2 ? 0 : 3.0 * (offset - 2) * (offset - 2);
            case AMPLITUDE -> offset <= 1 ? 0 : 4.0 * (offset - 1) * (offset - 1);
            case SINGLE_SIDEBAND -> offset <= 2 ? 0 : 20.0 * (offset - 2);
        };
    }
}
