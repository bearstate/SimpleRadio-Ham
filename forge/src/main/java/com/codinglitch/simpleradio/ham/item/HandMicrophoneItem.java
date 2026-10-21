package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.HamEvents;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A hand microphone on a coiled cable. Plug it into a station (use it on the station), then hold the use
 * key anywhere within the cable's reach to talk through that station.
 */
public class HandMicrophoneItem extends Item {
    private static final String STATION = "station";
    private static final String DIMENSION = "dimension";

    public HandMicrophoneItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static double reach() {
        return SimpleRadioLibrary.SERVER_CONFIG.station.pttRange + SimpleRadioLibrary.SERVER_CONFIG.station.microphoneReach;
    }

    @Nullable
    private static BlockPos linkedStation(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(STATION)) return null;
        if (!tag.getString(DIMENSION).equals(level.dimension().location().toString())) return null;
        return NbtUtils.readBlockPos(tag.getCompound(STATION));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof StationBlockEntity)) return InteractionResult.PASS;

        CompoundTag tag = context.getItemInHand().getOrCreateTag();
        tag.put(STATION, NbtUtils.writeBlockPos(context.getClickedPos()));
        tag.putString(DIMENSION, level.dimension().location().toString());
        if (!level.isClientSide && context.getPlayer() != null) {
            context.getPlayer().displayClientMessage(Component.translatable("message.simpleradio.microphone.linked").withStyle(ChatFormatting.GREEN), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockPos station = linkedStation(stack, level);
        if (station == null || player.distanceToSqr(station.getCenter()) > reach() * reach()) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(station == null ? "message.simpleradio.microphone.not_linked"
                        : "message.simpleradio.microphone.too_far").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!(level.getBlockEntity(station) instanceof StationBlockEntity entity) || !HamEvents.keyStation(serverPlayer, entity)) {
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        if (!level.isClientSide && user instanceof ServerPlayer player) HamEvents.releasePushToTalk(player);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (!entity.level().isClientSide && entity instanceof ServerPlayer player) HamEvents.releasePushToTalk(player);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(STATION)) {
            BlockPos pos = NbtUtils.readBlockPos(tag.getCompound(STATION));
            components.add(Component.translatable("tooltip.simpleradio.microphone.linked", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY));
        } else {
            components.add(Component.translatable("tooltip.simpleradio.microphone.hint").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, level, components, flag);
    }
}
