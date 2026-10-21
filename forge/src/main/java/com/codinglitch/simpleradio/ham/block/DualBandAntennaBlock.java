package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A two metre tall dual band (VHF/UHF) fiberglass vertical, meant for rooftops.
 * It is omnidirectional and connects to a station or repeater with coaxial cable.
 */
public class DualBandAntennaBlock extends Block {
    /** Height of the radiating element above the block's base. */
    public static final double HEIGHT = 2.0;

    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public DualBandAntennaBlock(Properties properties) {
        super(properties);
    }

    /**
     * Linear gain relative to an isotropic radiator on the given frequency.
     */
    public static double gain(long kHz) {
        double dbi = switch (HamFrequency.bandOf(kHz)) {
            case HF -> SimpleRadioLibrary.SERVER_CONFIG.dual_band_antenna.hfGainDbi;
            case VHF -> SimpleRadioLibrary.SERVER_CONFIG.dual_band_antenna.vhfGainDbi;
            case UHF -> SimpleRadioLibrary.SERVER_CONFIG.dual_band_antenna.uhfGainDbi;
        };
        return Math.pow(10, dbi / 10.0);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
