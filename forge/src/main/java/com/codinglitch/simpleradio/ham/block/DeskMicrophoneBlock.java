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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A desk microphone with a push-to-talk bar. Hold the use key on it to talk through a station within a few blocks.
 */
public class DeskMicrophoneBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    public static final int STATION_SEARCH_RADIUS = 4;

    private static final VoxelShape SHAPE = Shapes.or(Block.box(4, 0, 4, 12, 2, 12), Block.box(7, 2, 7, 9, 9, 9), Block.box(6, 9, 5, 10, 13, 11));

    public DeskMicrophoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // push-to-talk follows the client's key state (see HamKeys); consume the click
        return InteractionResult.CONSUME;
    }

    @Nullable
    public static StationBlockEntity findStation(Level level, BlockPos microphone) {
        StationBlockEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(microphone.offset(-STATION_SEARCH_RADIUS, -STATION_SEARCH_RADIUS, -STATION_SEARCH_RADIUS),
                microphone.offset(STATION_SEARCH_RADIUS, STATION_SEARCH_RADIUS, STATION_SEARCH_RADIUS))) {
            if (level.getBlockEntity(pos) instanceof StationBlockEntity station) {
                double distance = pos.distSqr(microphone);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = station;
                }
            }
        }
        return closest;
    }

    public static void setPressed(ServerLevel level, BlockPos pos, boolean pressed) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DeskMicrophoneBlock) || state.getValue(PRESSED) == pressed) return;
        level.setBlock(pos, state.setValue(PRESSED, pressed), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.2f, pressed ? 1.6f : 1.3f);
    }
}
