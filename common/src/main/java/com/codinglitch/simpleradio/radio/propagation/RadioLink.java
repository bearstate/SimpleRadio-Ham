package com.codinglitch.simpleradio.radio.propagation;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioRouter;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * The radio path between a transmitter and a receiver.
 * <p>
 * The mod's ranges are calibrated for a clear line of sight over flat open land. Everything that
 * weakens a real signal is expressed in dB and turned into an effective distance: 6 dB of loss makes
 * the receiver seem twice as far away (free space), so it needs twice the power to be heard.
 * <ul>
 *     <li>terrain in the way and the radio horizon ({@link Terrain})</li>
 *     <li>antennas indoors, underground or under water ({@link Environment})</li>
 *     <li>poor antennas for the band, e.g. a handheld's short antenna on HF</li>
 *     <li>on HF the ground wave or the sky wave, whichever is stronger ({@link Ionosphere})</li>
 * </ul>
 */
public final class RadioLink {
    public enum Mode { LINE_OF_SIGHT, GROUND_WAVE, SKY_WAVE, SPORADIC_E, UNMODELLED }

    public record Result(double distance, double effectiveDistance, double lossDb, Mode mode) {
        public static Result plain(double distance) {
            return new Result(distance, distance, 0, Mode.UNMODELLED);
        }
    }

    // antenna losses of device types on HF (dB), e.g. handhelds whose antenna is far too short
    private static final Map<FrequencingType, DoubleSupplier> HF_ANTENNA_LOSS = new ConcurrentHashMap<>();

    private static final int CACHE_SIZE = 32;
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    private RadioLink() {}

    public static void registerHfAntennaLoss(FrequencingType type, DoubleSupplier lossDb) {
        HF_ANTENNA_LOSS.put(type, lossDb);
    }

    public static double factor(double lossDb) {
        return Math.pow(10, lossDb / 20.0);
    }

    public static Result compute(RadioRouter from, RadioRouter to, @Nullable Frequency frequency) {
        WorldlyPosition a = from.getLocation(), b = to.getLocation();
        if (a == null || b == null) return Result.plain(0);

        double distance = a.distance(b);
        if (frequency == null || a.level != b.level || !(a.level instanceof ServerLevel level)) return Result.plain(distance);

        Cache cache = CACHE.get();
        int tick = level.getServer().getTickCount();
        Result cached = cache.get(from, to, frequency, tick);
        if (cached != null) return cached;

        Result result = compute(level, from, to, frequency, distance);
        cache.put(from, to, frequency, tick, result);
        return result;
    }

    private static Result compute(ServerLevel level, RadioRouter from, RadioRouter to, Frequency frequency, double distance) {
        long kHz = HamFrequency.parseKHz(frequency.getFrequency());
        if (kHz <= 0) return Result.plain(distance);

        double frequencyMHz = kHz / 1000.0;
        HamFrequency.Band band = HamFrequency.bandOf(kHz);
        Vector3f antennaA = from.antennaPosition(), antennaB = to.antennaPosition();

        double endpoints = 0;
        if (!from.externalAntenna) endpoints += Environment.lossDb(level, antennaA, band);
        if (!to.externalAntenna) endpoints += Environment.lossDb(level, antennaB, band);
        endpoints += antennaLossDb(from, band) + antennaLossDb(to, band);
        endpoints += from.directionalLossDb(to.getLocation()) + to.directionalLossDb(from.getLocation());

        // single sideband puts all the power into the voice, it gets through where FM would be lost in the noise
        if (frequency.getModulation() == Frequency.Modulation.SINGLE_SIDEBAND) endpoints -= SimpleRadioLibrary.SERVER_CONFIG.propagation.ssbGainDb;

        double terrain = Terrain.lossDb(level, antennaA, antennaB, frequencyMHz);

        if (Boolean.getBoolean("simpleradio.debugLinks")) {
            com.codinglitch.simpleradio.CommonSimpleRadio.warn("LINKDEBUG {} -> {} f={} d={} terrain={} endpoints={} envA={} envB={} antA={} antB={}", from.getClass().getSimpleName(), to.getClass().getSimpleName(), frequencyMHz, distance, terrain, endpoints, Environment.lossDb(level, antennaA, band), Environment.lossDb(level, antennaB, band), antennaA, antennaB);
        }

        Result best;
        if (band == HamFrequency.Band.HF) {
            double loss = terrain + endpoints + Ionosphere.groundWaveLossDb(frequencyMHz, distance);
            best = new Result(distance, distance * factor(loss), loss, Mode.GROUND_WAVE);
        } else {
            double loss = terrain + endpoints;
            best = new Result(distance, distance * factor(loss), loss, Mode.LINE_OF_SIGHT);
        }

        // the sky wave does not care about hills between the stations, only about the ends of the path
        double sky = Ionosphere.skyWaveLossDb(level, distance, frequencyMHz, variation(antennaA, antennaB));
        if (!Double.isNaN(sky)) {
            double loss = sky + endpoints;
            double effective = Ionosphere.skyReference() * factor(loss);
            if (effective < best.effectiveDistance()) {
                best = new Result(distance, effective, loss, frequencyMHz < 30 ? Mode.SKY_WAVE : Mode.SPORADIC_E);
            }
        }
        return best;
    }

    private static double antennaLossDb(RadioRouter router, HamFrequency.Band band) {
        if (band != HamFrequency.Band.HF) return 0;

        FrequencingType type = null;
        if (router instanceof RadioTransmitter transmitter) type = transmitter.frequencingType;
        else if (router instanceof RadioReceiver receiver) type = receiver.frequencingType;
        if (type == null) return 0;

        DoubleSupplier loss = HF_ANTENNA_LOSS.get(type);
        return loss == null ? 0 : loss.getAsDouble();
    }

    // stable per path, so both ends of a link fade together but different links fade differently
    private static double variation(Vector3f a, Vector3f b) {
        long hash = Math.round(a.x / 64) * 73856093L ^ Math.round(a.z / 64) * 19349663L ^ Math.round(b.x / 64) * 83492791L ^ Math.round(b.z / 64) * 2654435761L;
        return (Math.floorMod(hash, 1000L)) / 1000.0;
    }

    /**
     * Signal strength in dB above the noise-limited edge of the range.
     */
    public static double marginDb(double budget, double cost) {
        return 20 * Math.log10(Math.max(1e-6, budget) / Math.max(1, cost));
    }

    /**
     * S-meter reading (S0 .. S9, above S9 in dB) for a signal margin.
     * @return S units; values above 9 are S9 plus (value - 9) * dB-per-unit
     */
    public static double sUnits(double marginDb) {
        if (Double.isNaN(marginDb)) return 0;
        double perUnit = Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.propagation.sMeterDbPerUnit);
        return Math.max(0, 1 + marginDb / perUnit);
    }

    // a small per-tick memory: shouldRouteTo and travel ask for the same path right after each other
    private static final class Cache {
        private final Object[] from = new Object[CACHE_SIZE];
        private final Object[] to = new Object[CACHE_SIZE];
        private final Object[] frequency = new Object[CACHE_SIZE];
        private final int[] tick = new int[CACHE_SIZE];
        private final Result[] result = new Result[CACHE_SIZE];
        private int next = 0;

        Result get(Object from, Object to, Object frequency, int tick) {
            for (int i = 0; i < CACHE_SIZE; i++) {
                if (this.from[i] == from && this.to[i] == to && this.frequency[i] == frequency && this.tick[i] == tick) return result[i];
            }
            return null;
        }

        void put(Object from, Object to, Object frequency, int tick, Result result) {
            int slot = next;
            next = (next + 1) % CACHE_SIZE;
            this.from[slot] = from;
            this.to[slot] = to;
            this.frequency[slot] = frequency;
            this.tick[slot] = tick;
            this.result[slot] = result;
        }
    }
}
