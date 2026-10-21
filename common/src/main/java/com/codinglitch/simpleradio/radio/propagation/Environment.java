package com.codinglitch.simpleradio.radio.propagation;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loss of an antenna that is indoors, in a cave or under water: every block of rock, earth or
 * building material above it absorbs part of the signal (more on UHF, less on HF). Openings that let
 * daylight in (a cave mouth, windows, a door) let part of the signal out as well.
 */
public final class Environment {
    private static final int CACHE_TICKS = 40;
    private static final int MAX_CACHE = 20_000;
    private static final int MAX_COLUMN = 64;
    private static final int OPEN_GAP = 3; // with a ceiling (Nether): more free space than this above is an open cavern

    private record Key(ResourceKey<Level> level, long pos) {}
    record Cover(double blocks, int skyLight, int tick) {}

    private static final Map<Key, Cover> CACHE = new ConcurrentHashMap<>();

    private Environment() {}

    public static void clear() {
        CACHE.clear();
    }

    /**
     * @return the extra loss (dB) of an antenna at this position
     */
    public static double lossDb(ServerLevel level, Vector3f position, HamFrequency.Band band) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.propagation.environmentEnabled) return 0;

        Cover cover = cover(level, BlockPos.containing(position.x, position.y, position.z));
        if (cover == null || cover.blocks() <= 0) return 0;

        var config = SimpleRadioLibrary.SERVER_CONFIG.propagation;
        double perBlock = switch (band) {
            case HF -> config.hfLossPerBlock;
            case VHF -> config.vhfLossPerBlock;
            case UHF -> config.uhfLossPerBlock;
        };

        double loss = config.entryLossDb + perBlock * cover.blocks();
        loss *= 1 - Math.min(15, Math.max(0, cover.skyLight())) / 15.0;
        return Math.min(config.maxEnvironmentLossDb, loss);
    }

    static Cover cover(ServerLevel level, BlockPos pos) {
        int now = level.getServer().getTickCount();
        Key key = new Key(level.dimension(), pos.asLong());

        Cover cached = CACHE.get(key);
        if (cached != null && now - cached.tick() < CACHE_TICKS) return cached;
        if (!level.getServer().isSameThread()) return cached;

        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return cached;

        double blocks = level.dimensionType().hasCeiling() ? roofAbove(level, chunk, pos) : columnAbove(level, chunk, pos);
        int skyLight = level.getBrightness(LightLayer.SKY, pos);

        Cover cover = new Cover(blocks, skyLight, now);
        if (CACHE.size() > MAX_CACHE) CACHE.clear();
        CACHE.put(key, cover);
        return cover;
    }

    // everything between the antenna and the open sky
    private static double columnAbove(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (pos.getY() >= top) return 0;

        int maxY = Math.min(top, pos.getY() + MAX_COLUMN);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double blocks = 0;
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            blocks += weight(chunk.getBlockState(cursor.set(pos.getX(), y, pos.getZ())));
        }
        return blocks;
    }

    // below a world ceiling only the rock right above counts; a big open cavern is open space
    private static double roofAbove(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + MAX_COLUMN);

        int gap = 0;
        double blocks = 0;
        boolean inRoof = false;
        for (int y = pos.getY() + 1; y <= maxY; y++) {
            double weight = weight(chunk.getBlockState(cursor.set(pos.getX(), y, pos.getZ())));
            if (weight > 0) {
                inRoof = true;
                blocks += weight;
            } else if (inRoof) {
                break;
            } else if (++gap > OPEN_GAP) {
                return 0;
            }
        }
        return blocks;
    }

    static double weight(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return state.getFluidState().is(FluidTags.WATER) ? SimpleRadioLibrary.SERVER_CONFIG.propagation.waterWeight : 1;
        }
        if (!state.blocksMotion()) return 0;
        if (state.getBlock() instanceof LeavesBlock) return 0.2;
        if (state.getBlock() instanceof AbstractGlassBlock || state.getBlock() instanceof IronBarsBlock) return 0.2;
        return 1;
    }
}
