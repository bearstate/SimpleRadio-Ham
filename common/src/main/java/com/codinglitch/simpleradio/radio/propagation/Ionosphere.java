package com.codinglitch.simpleradio.radio.propagation;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * HF sky-wave propagation.
 * <ul>
 *     <li>The F layer reflects HF back to earth. The highest usable frequency (MUF) rises by day and with
 *     solar activity (solar flux, following the sun's 27 day rotation) and drops at night.</li>
 *     <li>Above the critical frequency the wave passes through at steep angles: near stations are not
 *     heard (skip zone) while far ones are.</li>
 *     <li>By day the D layer absorbs low frequencies (80/160 m are short range by day, open at night).</li>
 *     <li>Geomagnetic storms (high K index) weaken everything; sporadic E opens 10 m and 6 m on some days.</li>
 *     <li>The ground wave covers short distances and fades faster on higher frequencies.</li>
 * </ul>
 */
public final class Ionosphere {
    private Ionosphere() {}

    public record Conditions(long day, double solarFlux, int kIndex, boolean sporadicE) {}

    private static Conditions cached;
    private static long cachedSeed;

    // ---- Time ---- \\

    private static ServerLevel overworld(ServerLevel level) {
        MinecraftServer server = level.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld != null ? overworld : level;
    }

    /**
     * Sun elevation factor at a time of day: 0 at night, 1 at noon.
     */
    static double daylight(long dayTime) {
        double t = Math.floorMod(dayTime, 24000L);
        return t < 12000 ? Math.sin(Math.PI * t / 12000.0) : 0;
    }

    public static double daylight(ServerLevel level) {
        return daylight(overworld(level).getDayTime());
    }

    /**
     * The F layer builds up and decays with about an hour of delay.
     */
    static double fLayer(long dayTime) {
        return Math.max(daylight(dayTime - 1000), 0.6 * daylight(dayTime - 2500));
    }

    // ---- Solar conditions ---- \\

    public static synchronized Conditions conditions(ServerLevel level) {
        ServerLevel overworld = overworld(level);
        long day = Math.floorDiv(overworld.getDayTime(), 24000L);
        long seed = overworld.getSeed();
        if (cached != null && cached.day() == day && cachedSeed == seed) return cached;

        cached = conditions(seed, day);
        cachedSeed = seed;
        return cached;
    }

    public static Conditions conditions(long seed, long day) {
        var settings = SimpleRadioLibrary.SERVER_CONFIG.ionosphere;
        Random random = new Random(seed * 31 + day * 0x9E3779B97F4A7C15L);
        double phase = new Random(seed).nextDouble() * Math.PI * 2;

        // the sun rotates every 27 days; active regions come around again
        double rotation = 0.5 + 0.35 * Math.sin(2 * Math.PI * day / 27.0 + phase) + 0.15 * (random.nextDouble() * 2 - 1);
        double flux = settings.minSolarFlux + (settings.maxSolarFlux - settings.minSolarFlux) * Math.max(0, Math.min(1, rotation));

        // geomagnetic storms last a day or two
        boolean storm = stormOn(seed, day) || (stormOn(seed, day - 1) && random.nextBoolean());
        int kIndex = storm ? 5 + random.nextInt(3) : random.nextInt(4);

        boolean sporadicE = random.nextDouble() < settings.sporadicEChance;
        return new Conditions(day, flux, kIndex, sporadicE);
    }

    private static boolean stormOn(long seed, long day) {
        return new Random(seed * 17 + day * 0x632BE59BD9B4E019L).nextDouble() < SimpleRadioLibrary.SERVER_CONFIG.ionosphere.stormChance;
    }

    // ---- Layers ---- \\

    /**
     * Critical frequency of the F2 layer (MHz): the highest frequency reflected straight up.
     */
    public static double criticalFrequency(Conditions conditions, long dayTime) {
        double flux = conditions.solarFlux();
        double day = 5.0 + 0.035 * (flux - 70);
        double night = 2.5 + 0.012 * (flux - 70);
        double foF2 = night + (day - night) * fLayer(dayTime);
        return foF2 * (1 - 0.07 * Math.max(0, conditions.kIndex() - 3));
    }

    /**
     * Highest usable frequency (MHz) for the longest single hop.
     */
    public static double maximumUsableFrequency(ServerLevel level) {
        return criticalFrequency(conditions(level), overworld(level).getDayTime()) * 3.3;
    }

    public static double absorptionDb(double frequencyMHz, long dayTime, Conditions conditions) {
        double absorption = SimpleRadioLibrary.SERVER_CONFIG.ionosphere.dayAbsorption * daylight(dayTime) / (frequencyMHz * frequencyMHz);
        if (conditions.kIndex() >= 5) absorption += (conditions.kIndex() - 4) * 4;
        return absorption;
    }

    /**
     * Skip distance (blocks): the sky wave comes down no closer than this.
     */
    public static double skipDistance(double frequencyMHz, double criticalFrequency) {
        if (frequencyMHz <= criticalFrequency) return 0;
        double ratio = frequencyMHz / criticalFrequency;
        return SimpleRadioLibrary.SERVER_CONFIG.ionosphere.skipScale * Math.sqrt(ratio * ratio - 1);
    }

    // ---- Links ---- \\

    /**
     * Extra ground wave loss (dB) on HF; it fades faster on higher frequencies.
     */
    public static double groundWaveLossDb(double frequencyMHz, double distance) {
        return SimpleRadioLibrary.SERVER_CONFIG.ionosphere.groundWaveLossDb * frequencyMHz * distance / 1000.0;
    }

    /**
     * Loss (dB) of a sky-wave link relative to {@code skyReference}, or NaN when no sky wave arrives
     * (band closed, inside the skip zone or not an ionospheric frequency).
     * @param variation a per-link value in [0, 1) so every link fades at its own pace
     */
    public static double skyWaveLossDb(ServerLevel level, double distance, double frequencyMHz, double variation) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.ionosphere.enabled) return Double.NaN;
        return skyWaveLossDb(conditions(level), overworld(level).getDayTime(), level.getServer().getTickCount(), distance, frequencyMHz, variation);
    }

    /**
     * @see #skyWaveLossDb(ServerLevel, double, double, double)
     * @param tick server tick, drives the slow fading
     */
    public static double skyWaveLossDb(Conditions conditions, long dayTime, int tick, double distance, double frequencyMHz, double variation) {
        var settings = SimpleRadioLibrary.SERVER_CONFIG.ionosphere;
        double fading = settings.fadingDb * (0.5 + 0.5 * Math.sin(tick / 20.0 * 2 * Math.PI / (6 + 6 * variation) + variation * 20));

        if (frequencyMHz < 30) {
            double foF2 = criticalFrequency(conditions, dayTime);
            double muf = foF2 * 3.3;
            if (frequencyMHz > muf) return Double.NaN;
            if (distance < skipDistance(frequencyMHz, foF2)) return Double.NaN;

            int hops = (int) Math.max(1, Math.ceil(distance / settings.hopLength));
            double ratio = frequencyMHz / muf;
            double nearMuf = ratio > 0.85 ? (ratio - 0.85) / 0.15 * 10 : 0;
            double greyLine = isTwilight(dayTime) ? -3 : 0;

            return settings.baseSkyLossDb + absorptionDb(frequencyMHz, dayTime, conditions) + (hops - 1) * settings.hopLossDb
                    + nearMuf + greyLine + fading;
        }

        // sporadic E clouds on some summer-like days reflect 10 m and 6 m (up to about 70 MHz)
        if (frequencyMHz <= 70 && conditions.sporadicE() && daylight(dayTime) > 0.2) {
            if (distance < settings.skipScale * 1.6) return Double.NaN;
            int hops = (int) Math.max(1, Math.ceil(distance / (settings.hopLength * 0.6)));
            double ratio = frequencyMHz / 70.0;
            return settings.baseSkyLossDb + 4 + (hops - 1) * settings.hopLossDb * 1.5 + ratio * 8 + fading;
        }

        return Double.NaN;
    }

    /**
     * Around sunrise and sunset (the grey line) HF paths are unusually good.
     */
    static boolean isTwilight(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        long toTerminator = Math.min(Math.min(t, Math.abs(t - 12000)), 24000 - t);
        return toTerminator < 800;
    }

    public static double skyReference() {
        return SimpleRadioLibrary.SERVER_CONFIG.ionosphere.skyReference;
    }

    // ---- Band conditions (for displays) ---- \\

    public enum BandState { CLOSED, POOR, FAIR, GOOD }

    public static final double[] DISPLAY_BANDS = {3.6, 7.1, 14.2, 21.2, 28.5};
    public static final String[] DISPLAY_BAND_NAMES = {"80m", "40m", "20m", "15m", "10m"};

    public static BandState bandState(ServerLevel level, double frequencyMHz) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.ionosphere.enabled) return BandState.GOOD;

        Conditions conditions = conditions(level);
        long dayTime = overworld(level).getDayTime();
        double muf = criticalFrequency(conditions, dayTime) * 3.3;
        if (frequencyMHz > muf) return BandState.CLOSED;

        double loss = SimpleRadioLibrary.SERVER_CONFIG.ionosphere.baseSkyLossDb + absorptionDb(frequencyMHz, dayTime, conditions);
        double ratio = frequencyMHz / muf;
        if (ratio > 0.85) loss += (ratio - 0.85) / 0.15 * 10;

        if (loss < 8) return BandState.GOOD;
        if (loss < 14) return BandState.FAIR;
        return BandState.POOR;
    }
}
