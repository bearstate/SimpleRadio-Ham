package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.ham.HamContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Exists only so the boom and elements can be rendered at any of the 16 angles.
 */
public class YagiAntennaBlockEntity extends BlockEntity {
    public YagiAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.YAGI_ANTENNA_BE.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2.0);
    }
}
