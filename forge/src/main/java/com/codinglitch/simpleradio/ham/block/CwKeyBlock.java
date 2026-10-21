package com.codinglitch.simpleradio.ham.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A straight key ("maniple") for sending morse code. Place it within two blocks of a station and
 * hold the use key while looking at it: the station transmits a CW tone for as long as it is held.
 * The keying itself is detected on the client for accurate timing (see HamKeys).
 */
public class CwKeyBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    public static final int STATION_SEARCH_RADIUS = 2;

    private static final VoxelShape SHAPE_NS = Shapes.or(Block.box(3, 0, 2, 13, 2, 14), Block.box(6, 2, 2, 10, 6, 13));
    private static final VoxelShape SHAPE_EW = Shapes.or(Block.box(2, 0, 3, 14, 2, 13), Block.box(3, 2, 6, 14, 6, 10));

    public CwKeyBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // the knob points towards the player
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // keying is handled by the client key state; consume the click so nothing else happens
        return InteractionResult.CONSUME;
    }

    @Nullable
    public static StationBlockEntity findStation(Level level, BlockPos keyPos) {
        StationBlockEntity closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(keyPos.offset(-STATION_SEARCH_RADIUS, -STATION_SEARCH_RADIUS, -STATION_SEARCH_RADIUS),
                keyPos.offset(STATION_SEARCH_RADIUS, STATION_SEARCH_RADIUS, STATION_SEARCH_RADIUS))) {
            if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
                double distance = pos.distSqr(keyPos);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = station;
                }
            }
        }
        return closest;
    }

    public static void setPressed(ServerLevel level, BlockPos pos, boolean pressed) {
        setPressed(level, pos, pressed, null);
    }

    /**
     * @param operator the player keying, checked for a licence (null when unknown)
     */
    public static void setPressed(ServerLevel level, BlockPos pos, boolean pressed, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player operator) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CwKeyBlock)) return;

        if (state.getValue(PRESSED) != pressed) {
            level.setBlock(pos, state.setValue(PRESSED, pressed), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.15f, pressed ? 2.0f : 1.7f);
        }

        StationBlockEntity station = findStation(level, pos);
        if (station != null) station.setCwKey(pressed, operator);
    }
}
