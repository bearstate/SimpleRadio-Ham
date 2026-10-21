package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.ham.power.WireNetwork;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;

/**
 * Building blocks with a wire laid in them: a stripped spruce log, spruce planks, stone bricks, each the block it looks
 * like but for a thin cable along it: a lookout's power brought up a leg and across a floor without a cable in sight
 * but that line. They carry power from a generator to the radios at the run's far end (see {@link WireNetwork}).
 */
public final class WiredBlocks {

    private WiredBlocks() {}

    /** A wired block that stands as a pillar: a log, laid along an axis. */
    public static class Pillar extends RotatedPillarBlock implements WireNetwork.Wired {
        public Pillar(Properties properties) {
            super(properties);
        }
    }

    /** A wired block of the plain kind: planks, bricks. */
    public static class Plain extends Block implements WireNetwork.Wired {
        public Plain(Properties properties) {
            super(properties);
        }
    }
}
