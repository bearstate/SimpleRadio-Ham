package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A small fox hunt transmitter. Use it with an empty hand to choose the fox number (1-5), or with a
 * handheld radio to set it to the handheld's frequency.
 */
public class FoxTransmitterBlock extends BaseEntityBlock {
    public static final BooleanProperty TRANSMITTING = BooleanProperty.create("transmitting");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 6, 12);

    public FoxTransmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TRANSMITTING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRANSMITTING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FoxTransmitterBlockEntity fox)) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            if (stack.getItem() instanceof TransceiverItem && stack.hasTag() && stack.getTag().contains("frequency")) {
                String frequency = HamFrequency.normalize(stack.getTag().getString("frequency"));
                if (frequency != null) fox.setFrequency(frequency);
            } else if (stack.isEmpty()) {
                fox.setFox(fox.fox % FoxTransmitterBlockEntity.IDENTITIES.length + 1);
            }
            player.displayClientMessage(Component.translatable("message.simpleradio.fox", fox.fox, fox.identity(), HamFrequency.display(fox.frequency))
                    .withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoxTransmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HamContent.FOX_TRANSMITTER_BE.get(), FoxTransmitterBlockEntity::serverTick);
    }
}
