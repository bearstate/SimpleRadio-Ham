package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.core.BlockPos;
import org.joml.Vector3f;

/**
 * Simplified radio propagation for stations and repeaters.
 * <ul>
 *     <li>Range grows with the square root of the radiated power (free space path loss).</li>
 *     <li>A yagi antenna multiplies the power in its main lobe and attenuates it behind.</li>
 *     <li>HF bounces off the ionosphere and travels further, UHF slightly less far than VHF.</li>
 *     <li>Antennas placed higher up see further.</li>
 * </ul>
 */
public final class Propagation {
    private Propagation() {}

    /**
     * Compass azimuth (0 = north, 90 = east) from one position to another, in degrees.
     */
    public static double azimuth(double fromX, double fromZ, double toX, double toZ) {
        double degrees = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        return (degrees + 360) % 360;
    }

    public static double azimuth(BlockPos from, BlockPos to) {
        return azimuth(from.getX() + 0.5, from.getZ() + 0.5, to.getX() + 0.5, to.getZ() + 0.5);
    }

    public static double azimuth(BlockPos from, Vector3f to) {
        return azimuth(from.getX() + 0.5, from.getZ() + 0.5, to.x, to.z);
    }

    /**
     * Smallest absolute difference between two angles, in degrees (0..180).
     */
    public static double angleBetween(double a, double b) {
        double difference = Math.abs(((a - b) % 360 + 360) % 360);
        return difference > 180 ? 360 - difference : difference;
    }

    /**
     * Linear power gain of the yagi relative to an omnidirectional antenna.
     * @param offAxis angle between the boom direction and the target in degrees
     */
    public static double yagiGain(double offAxis) {
        double gain = Math.pow(10, SimpleRadioLibrary.SERVER_CONFIG.yagi.gainDbi / 10.0);
        double back = gain / Math.pow(10, SimpleRadioLibrary.SERVER_CONFIG.yagi.frontToBackDb / 10.0);

        double theta = Math.abs(offAxis);
        if (theta >= 90) return back;

        // cos^k main lobe with the configured half-power beam width
        double halfBeam = Math.toRadians(Math.max(5, Math.min(170, SimpleRadioLibrary.SERVER_CONFIG.yagi.beamWidth)) / 2.0);
        double exponent = Math.log(0.5) / Math.log(Math.cos(halfBeam));
        double lobe = gain * Math.pow(Math.cos(Math.toRadians(theta)), exponent);

        return Math.max(lobe, back);
    }

    public static double yagiForwardGain() {
        return yagiGain(0);
    }

    /**
     * Extra range for antennas above sea level.
     */
    public static double heightBonus(double y) {
        return Math.max(0, y - SimpleRadioLibrary.SERVER_CONFIG.propagation.seaLevel) * SimpleRadioLibrary.SERVER_CONFIG.propagation.heightBonusPerBlock;
    }

    public static double bandMultiplier(String frequency) {
        return switch (HamFrequency.bandOf(frequency)) {
            case HF -> SimpleRadioLibrary.SERVER_CONFIG.station.hfRangeMultiplier;
            case VHF -> SimpleRadioLibrary.SERVER_CONFIG.station.vhfRangeMultiplier;
            case UHF -> SimpleRadioLibrary.SERVER_CONFIG.station.uhfRangeMultiplier;
        };
    }

    /**
     * Transmission range (in blocks, the mod's "transmission power") of a station.
     * @param watts output power
     * @param gain linear antenna gain towards the receiver
     */
    public static double stationRange(double watts, Frequency.Modulation modulation, String frequency, double gain) {
        double range = SimpleRadioLibrary.SERVER_CONFIG.station.rangePerSqrtWatt * Math.sqrt(Math.max(0, watts) * gain);
        if (modulation == Frequency.Modulation.AMPLITUDE) range *= SimpleRadioLibrary.SERVER_CONFIG.station.amRangeMultiplier;
        return range * bandMultiplier(frequency);
    }

    /**
     * Human readable compass point for an azimuth.
     */
    public static String compassPoint(double azimuth) {
        String[] points = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return points[(int) Math.round(((azimuth % 360) + 360) % 360 / 22.5) % 16];
    }
}
