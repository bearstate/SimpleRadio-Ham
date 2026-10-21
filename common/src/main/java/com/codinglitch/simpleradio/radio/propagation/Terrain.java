package com.codinglitch.simpleradio.radio.propagation;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Terrain between two antennas: hills and mountains in the way (knife-edge diffraction, Deygout method)
 * and the radio horizon of a round earth.
 * <p>
 * Heights come from, in order: loaded chunks, a coarse record of chunks that were loaded earlier
 * (player builds included) and, for never explored land, the world generator's height estimate,
 * which is computed on a background thread so routing never waits for it.
 */
public final class Terrain {
    private static final int MAX_EXPLORED_CHUNKS = 300_000;
    private static final int MAX_GENERATED_CHUNKS = 400_000;
    private static final int MAX_PROFILES = 4096;
    private static final int COMPLETE_PROFILE_TICKS = 1200;
    private static final int INCOMPLETE_PROFILE_TICKS = 10;
    private static final double SAMPLE_SPACING = 16;
    private static final double MIN_LENGTH = 24;

    private static final Map<ResourceKey<Level>, LevelHeights> HEIGHTS = new ConcurrentHashMap<>();
    private static final Map<ProfileKey, Profile> PROFILES = new ConcurrentHashMap<>();

    private static final ExecutorService GENERATOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SimpleRadio-Terrain");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private Terrain() {}

    private static final class LevelHeights {
        // chunk -> 16 cells of 4x4 blocks (highest surface of each cell), guarded by itself
        final Long2ObjectOpenHashMap<short[]> explored = new Long2ObjectOpenHashMap<>();
        // chunk -> world generator surface estimate at the chunk center
        final Map<Long, Short> generated = new ConcurrentHashMap<>();
        final Set<Long> pending = ConcurrentHashMap.newKeySet();
    }

    private record ProfileKey(ResourceKey<Level> level, long a, long b) {}

    /**
     * Heights along the path between two antennas.
     */
    static final class Profile {
        final double length;
        final double[] terrain; // index 0 and n are the ground below the antennas
        final double yA, yB;    // antenna heights (moved to the surface when buried)
        final double meanTerrain;
        final boolean complete;
        final int created;

        private double memoFrequency = -1;
        private double memoLoss;

        Profile(double length, double[] terrain, double yA, double yB, boolean complete, int created) {
            this.length = length;
            this.terrain = terrain;
            this.yA = yA;
            this.yB = yB;
            this.complete = complete;
            this.created = created;

            double sum = 0;
            for (double height : terrain) sum += height;
            this.meanTerrain = terrain.length == 0 ? Math.min(yA, yB) : sum / terrain.length;
        }

        int samples() {
            return terrain.length - 1;
        }

        double distance(int index) {
            return length * index / samples();
        }
    }

    // ---- Height sources ---- \\

    private static LevelHeights heights(ServerLevel level) {
        return HEIGHTS.computeIfAbsent(level.dimension(), key -> new LevelHeights());
    }

    /**
     * Remembers the surface of a chunk that is about to unload, so paths over it keep seeing player builds.
     */
    public static void recordChunk(ServerLevel level, ChunkAccess chunk) {
        short[] cells = new short[16];
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        for (int cell = 0; cell < 16; cell++) {
            int cellX = (cell & 3) * 4, cellZ = (cell >> 2) * 4;
            int highest = Integer.MIN_VALUE;
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    highest = Math.max(highest, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, baseX + cellX + dx, baseZ + cellZ + dz) + 1);
                }
            }
            cells[cell] = (short) highest;
        }

        LevelHeights heights = heights(level);
        synchronized (heights.explored) {
            if (heights.explored.size() > MAX_EXPLORED_CHUNKS) heights.explored.clear();
            heights.explored.put(chunk.getPos().toLong(), cells);
        }
    }

    /**
     * Surface height (the y an antenna standing on the ground would be at) or NaN while unknown.
     */
    static double surface(ServerLevel level, double x, double z, boolean mainThread) {
        int blockX = (int) Math.floor(x), blockZ = (int) Math.floor(z);

        if (mainThread) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
            if (chunk != null) return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 1;
        }

        long chunkKey = ChunkPos.asLong(blockX >> 4, blockZ >> 4);
        LevelHeights heights = heights(level);

        short[] cells;
        synchronized (heights.explored) {
            cells = heights.explored.get(chunkKey);
        }
        if (cells != null) return cells[((blockZ & 15) >> 2) * 4 + ((blockX & 15) >> 2)];

        Short generated = heights.generated.get(chunkKey);
        if (generated != null) return generated;

        requestGenerated(level, heights, chunkKey);
        return Double.NaN;
    }

    private static void requestGenerated(ServerLevel level, LevelHeights heights, long chunkKey) {
        if (!heights.pending.add(chunkKey)) return;

        GENERATOR.execute(() -> {
            int height;
            try {
                int x = ChunkPos.getX(chunkKey) * 16 + 8, z = ChunkPos.getZ(chunkKey) * 16 + 8;
                height = level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
            } catch (Throwable throwable) {
                CommonSimpleRadio.debug("Terrain height estimate failed: {}", throwable);
                height = level.getSeaLevel();
            }

            if (heights.generated.size() > MAX_GENERATED_CHUNKS) heights.generated.clear();
            heights.generated.put(chunkKey, (short) height);
            heights.pending.remove(chunkKey);
        });
    }

    public static void clear() {
        HEIGHTS.clear();
        PROFILES.clear();
    }

    // ---- Profiles ---- \\

    static Profile profile(ServerLevel level, Vector3f a, Vector3f b) {
        int now = level.getServer().getTickCount();
        ProfileKey key = new ProfileKey(level.dimension(), quantize(a), quantize(b));

        Profile cached = PROFILES.get(key);
        if (cached != null && now - cached.created < (cached.complete ? COMPLETE_PROFILE_TICKS : INCOMPLETE_PROFILE_TICKS)) {
            return cached;
        }

        Profile profile = build(level, a, b, now);
        if (PROFILES.size() > MAX_PROFILES) PROFILES.clear();
        PROFILES.put(key, profile);
        return profile;
    }

    private static long quantize(Vector3f position) {
        return BlockPos.asLong((int) Math.floor(position.x) >> 2, (int) Math.floor(position.y) >> 1, (int) Math.floor(position.z) >> 2);
    }

    private static Profile build(ServerLevel level, Vector3f a, Vector3f b, int now) {
        boolean mainThread = level.getServer().isSameThread();
        double dx = b.x - a.x, dz = b.z - a.z;
        double length = Math.sqrt(dx * dx + dz * dz);

        int maxSamples = Math.max(4, SimpleRadioLibrary.SERVER_CONFIG.propagation.terrainSamples);
        int samples = (int) Math.max(4, Math.min(maxSamples, Math.ceil(length / SAMPLE_SPACING)));
        double[] terrain = new double[samples + 1];
        boolean complete = true;

        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            terrain[i] = surface(level, a.x + dx * t, a.z + dz * t, mainThread);
            if (Double.isNaN(terrain[i])) complete = false;
        }

        // endpoints without data sit on the ground right below their antenna
        if (Double.isNaN(terrain[0])) terrain[0] = a.y - 1.5;
        if (Double.isNaN(terrain[samples])) terrain[samples] = b.y - 1.5;
        fillUnknown(terrain);

        // a buried antenna radiates from the surface above it; getting through the rock is the environment's loss
        double yA = Math.max(a.y, terrain[0] + 0.5);
        double yB = Math.max(b.y, terrain[samples] + 0.5);

        return new Profile(length, terrain, yA, yB, complete, now);
    }

    // unknown heights are interpolated between their known neighbours
    private static void fillUnknown(double[] terrain) {
        int previous = 0;
        for (int i = 1; i < terrain.length; i++) {
            if (Double.isNaN(terrain[i])) continue;
            for (int j = previous + 1; j < i; j++) {
                double t = (double) (j - previous) / (i - previous);
                terrain[j] = terrain[previous] + (terrain[i] - terrain[previous]) * t;
            }
            previous = i;
        }
    }

    // ---- Loss ---- \\

    /**
     * Extra path loss (dB) caused by terrain and the earth's curvature between two antennas.
     * Flat open ground costs nothing, the calibrated ranges are for a clear line of sight.
     */
    public static double lossDb(ServerLevel level, Vector3f a, Vector3f b, double frequencyMHz) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.propagation.terrainEnabled) return 0;
        if (level.dimensionType().hasCeiling() || frequencyMHz <= 0) return 0;

        double dx = b.x - a.x, dz = b.z - a.z;
        if (dx * dx + dz * dz < MIN_LENGTH * MIN_LENGTH) return 0;

        Profile profile = profile(level, a, b);
        synchronized (profile) {
            if (profile.memoFrequency == frequencyMHz) return profile.memoLoss;

            double wavelength = 299.792458 / frequencyMHz; // blocks are metres
            double loss = deygout(profile, 0, profile.samples(), profile.yA, profile.yB, wavelength, 0);
            loss = Math.min(loss, SimpleRadioLibrary.SERVER_CONFIG.propagation.maxTerrainLossDb);
            loss += horizonLossDb(profile, frequencyMHz);

            profile.memoFrequency = frequencyMHz;
            profile.memoLoss = loss;
            return loss;
        }
    }

    /**
     * Loss of a given height profile (evenly spaced surface heights from one antenna to the other).
     */
    public static double pathLossDb(double length, double[] terrain, double yA, double yB, double frequencyMHz) {
        Profile profile = new Profile(length, terrain.clone(), yA, yB, true, 0);
        double loss = deygout(profile, 0, profile.samples(), yA, yB, 299.792458 / frequencyMHz, 0);
        return Math.min(loss, SimpleRadioLibrary.SERVER_CONFIG.propagation.maxTerrainLossDb) + horizonLossDb(profile, frequencyMHz);
    }

    /**
     * Deygout method: the worst edge plus the worst edges on both sides of it.
     */
    static double deygout(Profile profile, int from, int to, double yFrom, double yTo, double wavelength, int depth) {
        if (to - from < 2) return 0;

        double start = profile.distance(from), end = profile.distance(to), span = end - start;
        if (span <= 0) return 0;

        double worst = Double.NEGATIVE_INFINITY;
        int worstIndex = -1;
        for (int i = from + 1; i < to; i++) {
            double d1 = profile.distance(i) - start, d2 = end - profile.distance(i);
            if (d1 <= 0 || d2 <= 0) continue;

            double line = yFrom + (yTo - yFrom) * d1 / span;
            double v = (profile.terrain[i] - line) * Math.sqrt(2 * span / (wavelength * d1 * d2));
            if (v > worst) {
                worst = v;
                worstIndex = i;
            }
        }

        if (worstIndex < 0 || worst <= 0) return 0;

        double loss = edgeLossDb(worst);
        if (depth < 1) {
            double top = profile.terrain[worstIndex];
            loss += deygout(profile, from, worstIndex, yFrom, top, wavelength, depth + 1);
            loss += deygout(profile, worstIndex, to, top, yTo, wavelength, depth + 1);
        }
        return loss;
    }

    /**
     * Knife-edge loss (ITU-R P.526) above that of a grazing path, so an unobstructed path costs nothing.
     */
    static double edgeLossDb(double v) {
        if (v <= 0) return 0;
        return knifeEdgeDb(v) - knifeEdgeDb(0);
    }

    static double knifeEdgeDb(double v) {
        if (v <= -0.78) return 0;
        return 6.9 + 20 * Math.log10(Math.sqrt((v - 0.1) * (v - 0.1) + 1) + v - 0.1);
    }

    /**
     * Beyond the radio horizon of both antennas the signal fades with every extra kilometre.
     * Antenna heights are taken above the average terrain of the path, so a station on a mountain sees far.
     */
    static double horizonLossDb(Profile profile, double frequencyMHz) {
        if (frequencyMHz < 30) return 0; // HF follows the curvature as a ground wave, see Ionosphere

        double effectiveRadius = SimpleRadioLibrary.SERVER_CONFIG.propagation.earthRadius * SimpleRadioLibrary.SERVER_CONFIG.propagation.refractionFactor;
        double heightA = Math.max(1, profile.yA - profile.meanTerrain);
        double heightB = Math.max(1, profile.yB - profile.meanTerrain);
        double horizon = Math.sqrt(2 * effectiveRadius * heightA) + Math.sqrt(2 * effectiveRadius * heightB);
        if (profile.length <= horizon) return 0;

        double rate = SimpleRadioLibrary.SERVER_CONFIG.propagation.horizonDbPerKm * Math.cbrt(frequencyMHz / 145.0);
        return rate * (profile.length - horizon) / 1000.0;
    }

    /**
     * Radio horizon (blocks) of two antennas at the given heights above the ground.
     */
    public static double horizonDistance(double heightA, double heightB) {
        double effectiveRadius = SimpleRadioLibrary.SERVER_CONFIG.propagation.earthRadius * SimpleRadioLibrary.SERVER_CONFIG.propagation.refractionFactor;
        return Math.sqrt(2 * effectiveRadius * Math.max(1, heightA)) + Math.sqrt(2 * effectiveRadius * Math.max(1, heightB));
    }
}
