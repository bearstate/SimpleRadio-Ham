package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.ham.HamContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A small coal-fired generator, of the kind a fire lookout or a mountain hut runs its radio from: coal or charcoal fed
 * in by hand, and the power it makes sent out to the equipment touching it, or along a wired run (see
 * {@link com.codinglitch.simpleradio.ham.power.WireNetwork}). It burns only while something wants power.
 */
public class CoalGeneratorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CoalGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var stack = player.getItemInHand(hand);
        if (CoalGeneratorBlockEntity.isFuel(stack)) {
            int taken = generator.addFuel(stack.copy());
            if (!player.getAbilities().instabuild) stack.shrink(taken);
            if (taken > 0) level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
            else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.simpleradio.generator_full"), true);
        } else {
            player.displayClientMessage(generator.status(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CoalGeneratorBlockEntity generator) {
            generator.dropFuel();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        // Exhaust from the stack on top, and now and then the engine's knock.
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.7, pos.getY() + 1.05, pos.getZ() + 0.3, 0, 0.04, 0);
        }
        if (random.nextInt(24) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.6f, 0.7f, false);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoalGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HamContent.COAL_GENERATOR_BE.get(), CoalGeneratorBlockEntity::serverTick);
    }
}
