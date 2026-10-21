package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.ham.radio.Propagation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A directional yagi antenna. Place it on top of an antenna mast standing on a station.
 * Right click to turn it clockwise by 22.5 degrees, sneak + right click to turn it counter-clockwise.
 * Stations can also turn it remotely with their rotator buttons.
 */
public class YagiAntennaBlock extends BaseEntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public YagiAntennaBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    public static double azimuthOf(BlockState state) {
        return state.getValue(ROTATION) * 22.5;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // point the antenna where the player is looking (compass azimuth = yaw + 180)
        double azimuth = context.getRotation() + 180.0;
        int rotation = (int) Math.round(((azimuth % 360) + 360) % 360 / 22.5) & 15;
        return this.defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public static void rotate(Level level, BlockPos pos, BlockState state, int steps) {
        int rotation = Math.floorMod(state.getValue(ROTATION) + steps, 16);
        level.setBlock(pos, state.setValue(ROTATION, rotation), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    public static Component describe(int rotation) {
        double azimuth = rotation * 22.5;
        String point = Propagation.compassPoint(azimuth).toLowerCase();
        return Component.translatable("message.simpleradio.yagi_azimuth",
                String.format(java.util.Locale.ROOT, "%05.1f", azimuth),
                Component.translatable("compass.simpleradio." + point));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;

        if (!level.isClientSide) {
            rotate(level, pos, state, player.isShiftKeyDown() ? -1 : 1);
            BlockState rotated = level.getBlockState(pos);
            player.displayClientMessage(describe(rotated.getValue(ROTATION)).copy().withStyle(ChatFormatting.AQUA), true);
            notifyStationBelow(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void notifyStationBelow(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.below().mutable();
        for (int i = 0; i < 40; i++) {
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity instanceof StationBlockEntity station) {
                station.markAntennaDirty();
                return;
            }
            if (level.getBlockState(cursor).isAir()) return;
            cursor.move(Direction.DOWN);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new YagiAntennaBlockEntity(pos, state);
    }
}
