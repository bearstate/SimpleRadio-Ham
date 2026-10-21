package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tall antennas in a thunderstorm attract lightning. A lightning rod next to the antenna takes the strike;
 * a lightning rod touching the radio grounds it so the surge does no harm. Otherwise the radio burns out.
 */
public final class Lightning {
    public enum Outcome { NONE, DIVERTED, GROUNDED, BURNED }

    private Lightning() {}

    /**
     * Rolls for a strike on an antenna. Call once a second while the equipment is loaded.
     * @param antenna the top of the antenna
     * @param height the antenna's height above what it stands on
     * @param device the radio connected to the antenna
     */
    public static Outcome tick(ServerLevel level, BlockPos antenna, double height, BlockPos device, RandomSource random) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.lightning;
        if (!config.enabled || !level.isThundering()) return Outcome.NONE;
        if (height < config.minAntennaHeight || !level.isRainingAt(antenna.above())) return Outcome.NONE;

        double perSecond = config.strikesPerHour / 3600.0 * Math.pow(height / 20.0, 2);
        if (random.nextDouble() >= perSecond) return Outcome.NONE;

        return strike(level, antenna, device);
    }

    public static Outcome strike(ServerLevel level, BlockPos antenna, BlockPos device) {
        BlockPos rod = rodNear(level, antenna, SimpleRadioLibrary.SERVER_CONFIG.lightning.protectionRadius);
        if (rod != null) {
            bolt(level, rod);
            BlockState state = level.getBlockState(rod);
            if (state.getBlock() instanceof LightningRodBlock rodBlock) rodBlock.onLightningStrike(state, level, rod);
            return Outcome.DIVERTED;
        }

        bolt(level, antenna);
        if (isGrounded(level, device)) {
            sparks(level, device, 6);
            return Outcome.GROUNDED;
        }

        sparks(level, device, 20);
        level.playSound(null, device, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6f, 1.6f);
        return Outcome.BURNED;
    }

    @Nullable
    private static BlockPos rodNear(ServerLevel level, BlockPos antenna, int radius) {
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(antenna.offset(-radius, -2, -radius), antenna.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).is(Blocks.LIGHTNING_ROD)) {
                if (best == null || pos.getY() > best.getY()) best = pos.immutable();
            }
        }
        return best;
    }

    /**
     * A lightning rod touching the radio acts as its ground connection.
     */
    public static boolean isGrounded(ServerLevel level, BlockPos device) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(device.relative(direction)).is(Blocks.LIGHTNING_ROD)) return true;
        }
        return false;
    }

    private static void bolt(ServerLevel level, BlockPos at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        bolt.setVisualOnly(true); // the radio takes the damage, nothing else is set on fire
        level.addFreshEntity(bolt);
    }

    /**
     * Height of an antenna above whatever it stands on (masts and antennas do not count as ground).
     */
    public static int heightAboveSupport(net.minecraft.world.level.Level level, BlockPos antenna) {
        BlockPos.MutableBlockPos cursor = antenna.mutable();
        int height = 0;
        while (height < 64) {
            cursor.move(Direction.DOWN);
            BlockState state = level.getBlockState(cursor);
            boolean antennaPart = state.isAir() || state.getBlock() instanceof com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock
                    || state.getBlock() instanceof com.codinglitch.simpleradio.ham.block.YagiAntennaBlock
                    || state.getBlock() instanceof com.codinglitch.simpleradio.ham.block.DualBandAntennaBlock;
            if (!antennaPart) break;
            height++;
        }
        return height + 1;
    }

    public static void sparks(ServerLevel level, BlockPos pos, int count) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, count, 0.4, 0.4, 0.4, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, count / 3, 0.2, 0.2, 0.2, 0.02);
    }
}
