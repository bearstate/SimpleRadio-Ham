package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.licence.LicenceExam;
import com.codinglitch.simpleradio.ham.licence.Licences;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The amateur radio licence exam booklet: use it to take the exam. Passing it grants a licence and a call sign.
 */
public class LicenceExamItem extends Item {
    public LicenceExamItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) LicenceExam.start(serverPlayer);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag flag) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.band_plan;
        components.add(Component.translatable("tooltip.simpleradio.exam.hint", config.examQuestions, config.examPassMark).withStyle(ChatFormatting.GRAY));
        if (Licences.clientLicensed) {
            components.add(Component.translatable("tooltip.simpleradio.exam.licensed", Licences.clientCallsign).withStyle(ChatFormatting.GREEN));
        }
        super.appendHoverText(stack, level, components, flag);
    }
}
