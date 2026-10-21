package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Helpers for treating the mod's frequency strings as real frequencies in MHz,
 * so that shifts (e.g. -7.600 MHz) and band limits can be applied.
 * <p>
 * Internally frequencies are handled in whole kHz. The configured amount of decimal places
 * decides how the string is formatted (3 decimals = 1 kHz resolution).
 */
public final class HamFrequency {
    public enum Band {
        HF("HF", 1_000, 29_999),
        VHF("VHF", 30_000, 299_999),
        UHF("UHF", 300_000, Integer.MAX_VALUE);

        public final String label;
        public final long minKHz;
        public final long maxKHz;

        Band(String label, long minKHz, long maxKHz) {
            this.label = label;
            this.minKHz = minKHz;
            this.maxKHz = maxKHz;
        }

        public long defaultKHz() {
            return switch (this) {
                case HF -> 7_050;
                case VHF -> 145_500;
                case UHF -> 433_500;
            };
        }
    }

    private HamFrequency() {}

    private static int decimals() {
        return SimpleRadioLibrary.SERVER_CONFIG.frequency.decimalPlaces;
    }

    private static int wholes() {
        return SimpleRadioLibrary.SERVER_CONFIG.frequency.wholePlaces;
    }

    /**
     * @return The highest frequency (in kHz) that can be represented with the configured pattern.
     */
    public static long maxKHz() {
        long wholeMax = (long) Math.pow(10, wholes()) - 1;
        return wholeMax * 1000 + 999;
    }

    /**
     * Parses user input or a stored frequency string ("438.650", "438,65", "145.5") into kHz.
     * @return the frequency in kHz, or -1 when it cannot be parsed
     */
    public static long parseKHz(@Nullable String input) {
        if (input == null) return -1;
        String text = input.trim().replace(',', '.');
        if (text.isEmpty()) return -1;

        // tolerate a trailing modulation suffix like "FM"/"AM" or a "MHz" unit
        text = text.replaceAll("(?i)\\s*(mhz|fm|am)$", "").trim();
        if (!text.matches("\\d{1,4}(\\.\\d{0,4})?")) return -1;

        String[] parts = text.split("\\.", -1);
        long whole = Long.parseLong(parts[0]);
        String fraction = parts.length > 1 ? parts[1] : "";
        fraction = (fraction + "000").substring(0, 3);

        return whole * 1000 + Long.parseLong(fraction);
    }

    /**
     * Parses a signed shift in MHz ("-7.6", "+0.600", "0") into kHz.
     * @return the shift in kHz, or null when it cannot be parsed
     */
    @Nullable
    public static Integer parseShiftKHz(@Nullable String input) {
        if (input == null) return null;
        String text = input.trim().replace(',', '.').replaceAll("(?i)\\s*mhz$", "");
        if (text.isEmpty()) return 0;

        int sign = 1;
        if (text.startsWith("-")) {
            sign = -1;
            text = text.substring(1);
        } else if (text.startsWith("+")) {
            text = text.substring(1);
        }

        long magnitude = parseKHz(text);
        if (magnitude < 0 || magnitude > 100_000) return null;
        return (int) (sign * magnitude);
    }

    /**
     * Formats a frequency in kHz to the configured frequency pattern (e.g. "438.650").
     * @return the formatted frequency, or null when it does not fit the pattern
     */
    @Nullable
    public static String format(long kHz) {
        if (kHz < 0 || kHz > maxKHz()) return null;

        int decimals = decimals();
        long whole = kHz / 1000;
        long millis = kHz % 1000;

        String wholePart = String.format(Locale.ROOT, "%0" + wholes() + "d", whole);
        if (decimals <= 0) return wholePart;

        String fraction;
        if (decimals >= 3) {
            fraction = String.format(Locale.ROOT, "%03d", millis) + "0".repeat(decimals - 3);
        } else {
            long divisor = (long) Math.pow(10, 3 - decimals);
            fraction = String.format(Locale.ROOT, "%0" + decimals + "d", millis / divisor);
        }
        return wholePart + "." + fraction;
    }

    /**
     * Normalizes arbitrary user input into the configured frequency pattern.
     * @return the normalized frequency, or null when it is invalid
     */
    @Nullable
    public static String normalize(@Nullable String input) {
        long kHz = parseKHz(input);
        if (kHz < 0) return null;
        return format(kHz);
    }

    /**
     * Applies a shift to a stored frequency.
     * @return the shifted frequency, or null when the result is out of range
     */
    @Nullable
    public static String shift(String frequency, int shiftKHz) {
        long kHz = parseKHz(frequency);
        if (kHz < 0) return null;
        return format(kHz + shiftKHz);
    }

    /**
     * Smallest frequency step (kHz) the configured decimal places can express.
     */
    public static int resolutionKHz() {
        int decimals = Math.max(0, Math.min(3, decimals()));
        return (int) Math.pow(10, 3 - decimals);
    }

    public static Band bandOf(long kHz) {
        if (kHz < Band.VHF.minKHz) return Band.HF;
        if (kHz < Band.UHF.minKHz) return Band.VHF;
        return Band.UHF;
    }

    public static Band bandOf(String frequency) {
        return bandOf(Math.max(0, parseKHz(frequency)));
    }

    /**
     * Human readable frequency, e.g. "438.650 MHz".
     */
    public static String display(String frequency) {
        long kHz = parseKHz(frequency);
        if (kHz < 0) return frequency;
        return String.format(Locale.ROOT, "%d.%03d MHz", kHz / 1000, kHz % 1000);
    }

    /**
     * Human readable shift, e.g. "-7.600 MHz" or "simplex" when zero.
     */
    public static String displayShift(int shiftKHz) {
        if (shiftKHz == 0) return "0.000 MHz";
        int magnitude = Math.abs(shiftKHz);
        return String.format(Locale.ROOT, "%s%d.%03d MHz", shiftKHz < 0 ? "-" : "+", magnitude / 1000, magnitude % 1000);
    }

    /**
     * Editable shift text for input fields, e.g. "-7.600".
     */
    public static String editableShift(int shiftKHz) {
        int magnitude = Math.abs(shiftKHz);
        return String.format(Locale.ROOT, "%s%d.%03d", shiftKHz < 0 ? "-" : (shiftKHz > 0 ? "+" : ""), magnitude / 1000, magnitude % 1000);
    }

    /**
     * Editable frequency text for input fields, e.g. "438.650".
     */
    public static String editable(String frequency) {
        long kHz = parseKHz(frequency);
        if (kHz < 0) return frequency;
        return String.format(Locale.ROOT, "%d.%03d", kHz / 1000, kHz % 1000);
    }
}
