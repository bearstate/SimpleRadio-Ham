package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.ham.power.PowerCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Set;

/**
 * Man-made noise (QRM): running electric machines (motors, generators, processing machines) near a
 * receiver raise its noise floor, so weak stations drown in buzz. The opposite of the RF interference
 * a strong transmitter causes in bare wires.
 */
public final class Qrm {
    /** Blocks that always make noise (e.g. electric motors without a "running" state). */
    public static final TagKey<Block> SOURCES = TagKey.create(Registries.BLOCK, CommonSimpleRadio.id("qrm_sources"));
    private static final Set<String> RUNNING_PROPERTIES = Set.of("lit", "active", "running", "working");

    private Qrm() {}

    /**
     * @return extra noise (0-100 interference points) at a receiver's position
     */
    public static float noiseAt(ServerLevel level, BlockPos pos) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.qrm;
        if (!config.enabled) return 0;

        int radius = Math.max(1, config.radius);
        double noise = 0;
        for (int chunkX = (pos.getX() - radius) >> 4; chunkX <= (pos.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (pos.getZ() - radius) >> 4; chunkZ <= (pos.getZ() + radius) >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    double distance = Math.sqrt(blockEntity.getBlockPos().distSqr(pos));
                    if (distance > radius || blockEntity instanceof Powered) continue;
                    if (isNoisy(blockEntity)) noise += config.noisePerMachine * (1 - distance / (radius + 1));
                }
            }
        }
        return (float) Math.min(config.maxNoise, noise);
    }

    static boolean isNoisy(BlockEntity blockEntity) {
        BlockState state = blockEntity.getBlockState();
        if (state.is(SOURCES)) return true;
        if (!isRunning(state)) return false;

        return blockEntity.getCapability(ForgeCapabilities.ENERGY).isPresent() || PowerCapabilities.hasElectrodynamic(blockEntity);
    }

    private static boolean isRunning(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && RUNNING_PROPERTIES.contains(property.getName())) {
                if (state.getValue(booleanProperty)) return true;
            }
        }
        return false;
    }
}
