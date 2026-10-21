package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.block.CoaxLink;
import com.codinglitch.simpleradio.ham.block.DualBandAntennaBlock;
import com.codinglitch.simpleradio.ham.block.YagiAntennaBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Coaxial cable. Right click an antenna, then a station or repeater, to run a cable between them.
 * Sneak + right click a station or repeater to remove its cable (the cable items are dropped).
 */
public class CoaxCableItem extends Item {
    private static final String ANTENNA = "antenna";
    private static final String DIMENSION = "antenna_dimension";

    public CoaxCableItem(Properties properties) {
        super(properties);
    }

    private static boolean isAntenna(BlockState state) {
        return state.getBlock() instanceof DualBandAntennaBlock || state.getBlock() instanceof YagiAntennaBlock;
    }

    private static void tell(Player player, String key, ChatFormatting color, Object... args) {
        if (player != null) player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }

    // Forge hook: runs before the clicked block's own use(), so clicking a station does not open its screen
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (isAntenna(state)) {
            if (!level.isClientSide) {
                CompoundTag tag = stack.getOrCreateTag();
                tag.put(ANTENNA, NbtUtils.writeBlockPos(pos));
                tag.putString(DIMENSION, level.dimension().location().toString());
                tell(player, "message.simpleradio.coax.antenna_selected", ChatFormatting.AQUA, pos.toShortString());
                level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!(blockEntity instanceof CoaxLink.Endpoint endpoint)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        CoaxLink coax = endpoint.getCoax();

        if (player != null && player.isShiftKeyDown()) {
            if (coax.isConnected()) {
                coax.disconnect(level, pos);
                endpoint.onCoaxChanged();
                tell(player, "message.simpleradio.coax.removed", ChatFormatting.YELLOW);
            }
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ANTENNA) || !level.dimension().location().toString().equals(tag.getString(DIMENSION))) {
            tell(player, "message.simpleradio.coax.select_antenna", ChatFormatting.YELLOW);
            return InteractionResult.SUCCESS;
        }

        BlockPos antenna = NbtUtils.readBlockPos(tag.getCompound(ANTENNA));
        BlockState antennaState = level.getBlockState(antenna);
        if (!isAntenna(antennaState)) {
            tag.remove(ANTENNA);
            tell(player, "message.simpleradio.coax.antenna_missing", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!endpoint.acceptsAntenna(antennaState)) {
            tell(player, "message.simpleradio.coax.not_supported", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        double length = Math.sqrt(antenna.distSqr(pos));
        if (length > SimpleRadioLibrary.SERVER_CONFIG.coax.maxLength) {
            tell(player, "message.simpleradio.coax.too_long", ChatFormatting.RED,
                    String.format(Locale.ROOT, "%.1f", length), SimpleRadioLibrary.SERVER_CONFIG.coax.maxLength);
            return InteractionResult.SUCCESS;
        }

        int items = Math.max(1, (int) Math.ceil(length / Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.coax.blocksPerItem)));
        boolean creative = player != null && player.getAbilities().instabuild;
        if (!creative && stack.getCount() < items) {
            tell(player, "message.simpleradio.coax.not_enough", ChatFormatting.RED, items);
            return InteractionResult.SUCCESS;
        }

        coax.connect(antenna, creative ? 0 : items, length, level, pos);
        endpoint.onCoaxChanged();
        tag.remove(ANTENNA);
        tag.remove(DIMENSION);
        if (tag.isEmpty()) stack.setTag(null);
        if (!creative) stack.shrink(items);

        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7f, 1.2f);
        tell(player, "message.simpleradio.coax.connected", ChatFormatting.GREEN, String.format(Locale.ROOT, "%.1f", length), items);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(ANTENNA);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ANTENNA)) {
            components.add(Component.translatable("tooltip.simpleradio.coax.selected",
                    NbtUtils.readBlockPos(tag.getCompound(ANTENNA)).toShortString()).withStyle(ChatFormatting.AQUA));
        }
        components.add(Component.translatable("tooltip.simpleradio.coax.hint", SimpleRadioLibrary.SERVER_CONFIG.coax.blocksPerItem).withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, components, flag);
    }
}
