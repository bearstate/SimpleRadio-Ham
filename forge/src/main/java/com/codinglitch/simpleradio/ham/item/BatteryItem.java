package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A swappable handheld battery pack. Holds as much charge as a handheld's built-in battery,
 * is charged in the charging station and swapped into a handheld by crafting them together.
 * New batteries come out of the crafting table empty.
 */
public class BatteryItem extends Item {
    public BatteryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TransceiverItem.BATTERY)) return 0;
        return Mth.clamp(tag.getInt(TransceiverItem.BATTERY), 0, TransceiverItem.maxBattery());
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.getOrCreateTag().putInt(TransceiverItem.BATTERY, Mth.clamp(charge, 0, TransceiverItem.maxBattery()));
    }

    public static int percent(ItemStack stack) {
        return Math.round(100f * getCharge(stack) / TransceiverItem.maxBattery());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getCharge(stack) / TransceiverItem.maxBattery());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((float) getCharge(stack) / TransceiverItem.maxBattery() / 3f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        int percent = percent(stack);
        components.add(Component.translatable("tooltip.simpleradio.battery.charge", percent)
                .withStyle(percent > 20 ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        components.add(Component.translatable("tooltip.simpleradio.battery.hint").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, components, flag);
    }
}
