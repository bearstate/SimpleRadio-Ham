package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Who may transmit where.
 * <ul>
 *     <li>Amateur bands (IARU region 1): licensed radio amateurs with amateur equipment.</li>
 *     <li>PMR446 (446.000-446.200 MHz, 16 channels): licence free, but only with PMR walkie talkies.</li>
 *     <li>Receiving is allowed everywhere.</li>
 * </ul>
 */
public final class BandPlan {
    public enum Equipment { PMR, AMATEUR }

    /** Amateur bands in kHz. */
    public static final long[][] AMATEUR_BANDS = {
            {1_810, 2_000}, {3_500, 3_800}, {7_000, 7_200}, {10_100, 10_150}, {14_000, 14_350},
            {18_068, 18_168}, {21_000, 21_450}, {24_890, 24_990}, {28_000, 29_700},
            {50_000, 52_000}, {144_000, 146_000}, {430_000, 440_000}
    };
    public static final String[] AMATEUR_BAND_NAMES = {"160m", "80m", "40m", "30m", "20m", "17m", "15m", "12m", "10m", "6m", "2m", "70cm"};

    /** PMR446 channel centres in Hz (12.5 kHz spacing from 446.00625 MHz). */
    public static final int PMR_CHANNELS = 16;

    /** Set by the loader: whether a player holds an amateur radio licence. */
    public static Predicate<Player> licensed = player -> true;

    private BandPlan() {}

    public static boolean isAmateur(long kHz) {
        for (long[] band : AMATEUR_BANDS) {
            if (kHz >= band[0] && kHz <= band[1]) return true;
        }
        return false;
    }

    @Nullable
    public static String amateurBandName(long kHz) {
        for (int i = 0; i < AMATEUR_BANDS.length; i++) {
            if (kHz >= AMATEUR_BANDS[i][0] && kHz <= AMATEUR_BANDS[i][1]) return AMATEUR_BAND_NAMES[i];
        }
        return null;
    }

    public static boolean isPmr(long kHz) {
        return kHz >= 446_000 && kHz <= 446_200;
    }

    /**
     * Frequency (kHz, rounded to the configured resolution) of a PMR446 channel (1-16).
     */
    public static long pmrChannelKHz(int channel) {
        int clamped = Math.max(1, Math.min(PMR_CHANNELS, channel));
        double hz = 446_006_250 + (clamped - 1) * 12_500.0;
        int resolution = HamFrequency.resolutionKHz();
        return Math.round(hz / 1000.0 / resolution) * resolution;
    }

    public static String pmrChannelFrequency(int channel) {
        String formatted = HamFrequency.format(pmrChannelKHz(channel));
        return formatted != null ? formatted : HamFrequency.format(446_006);
    }

    /**
     * @return the PMR channel (1-16) a frequency is on, or 0 when it is none
     */
    public static int pmrChannelOf(String frequency) {
        long kHz = HamFrequency.parseKHz(frequency);
        for (int channel = 1; channel <= PMR_CHANNELS; channel++) {
            if (pmrChannelKHz(channel) == kHz) return channel;
        }
        return 0;
    }

    /**
     * @return null when the transmission is allowed, otherwise the translation key of the reason
     */
    @Nullable
    public static String denial(@Nullable Player player, String frequency, Equipment equipment) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.band_plan;
        long kHz = HamFrequency.parseKHz(frequency);

        if (config.enforce) {
            if (equipment == Equipment.PMR && !isPmr(kHz)) return "message.simpleradio.denied.pmr_only";
            if (equipment == Equipment.AMATEUR && !isAmateur(kHz)) return "message.simpleradio.denied.out_of_band";
        }
        if (equipment == Equipment.AMATEUR && config.requireLicense && player != null && !licensed.test(player)) {
            return "message.simpleradio.denied.license";
        }
        return null;
    }

    public static String describe(long kHz) {
        String band = amateurBandName(kHz);
        if (band != null) return band;
        if (isPmr(kHz)) return "PMR446";
        return String.format(Locale.ROOT, "%s", "---");
    }
}
