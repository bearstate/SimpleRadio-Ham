package com.codinglitch.simpleradio.ham.aprs;

import com.codinglitch.simpleradio.SimpleRadioLibrary;

import java.util.Locale;

/**
 * An APRS position report: who, where (in the world and as latitude / longitude), how fast and where to.
 */
public record AprsPacket(String callsign, String dimension, double x, double y, double z, double speedKmh, double course,
                         String comment, long gameTime) {

    public double latitude() {
        return SimpleRadioLibrary.SERVER_CONFIG.aprs.originLatitude - z / 111_320.0;
    }

    public double longitude() {
        double latitude = SimpleRadioLibrary.SERVER_CONFIG.aprs.originLatitude;
        return SimpleRadioLibrary.SERVER_CONFIG.aprs.originLongitude + x / (111_320.0 * Math.cos(Math.toRadians(latitude)));
    }

    /**
     * The information field of the AX.25 frame: "!DDMM.mmN/DDDMM.mmE[" position with a walking person symbol.
     */
    public String information() {
        double latitude = latitude(), longitude = longitude();
        String lat = coordinate(Math.abs(latitude), 2) + (latitude >= 0 ? "N" : "S");
        String lon = coordinate(Math.abs(longitude), 3) + (longitude >= 0 ? "E" : "W");
        String extension = speedKmh > 1 ? String.format(Locale.ROOT, "%03d/%03d", Math.round(course) % 360, Math.round(speedKmh / 1.852)) : "";
        String clean = comment.replaceAll("[^\\x20-\\x7E]", "");
        return "!" + lat + "/" + lon + "[" + extension + clean;
    }

    private static String coordinate(double degrees, int width) {
        int whole = (int) degrees;
        double minutes = (degrees - whole) * 60;
        return String.format(Locale.ROOT, "%0" + width + "d%05.2f", whole, minutes);
    }
}
