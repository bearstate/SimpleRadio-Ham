package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.SimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.radio.DirectionalReceiver;
import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.codinglitch.simpleradio.routers.Receiver;
import com.codinglitch.simpleradio.routers.Speaker;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A fox hunt (ARDF) receiver with a small handheld directional antenna: the S-meter peaks when the
 * antenna points at the transmitter. Use it to step the attenuator (0 / 10 / 20 / 30 dB) when the fox is
 * near and the meter is pinned; sneak + use to tune it.
 */
public class DirectionFinderItem extends TransceiverItem {
    public static final String ATTENUATION = "attenuation";
    public static final int[] STEPS = {0, 10, 20, 30};

    public DirectionFinderItem(Properties settings) {
        super(settings);
    }

    public static int attenuation(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(ATTENUATION);
    }

    @Override
    public FrequencingType receiverType() {
        return SimpleRadioFrequencing.DIRECTION_FINDER;
    }

    @Override
    public boolean canTransmit() {
        return false;
    }

    @Override
    public String getDefaultFrequency() {
        return "144.500";
    }

    @Override
    public void activate(Level level, ItemStack stack, String frequencyName, Frequency.Modulation modulation, Entity entity, UUID owner) {
        // a receiver and a speaker: it cannot transmit
        Speaker speaker = startSpeaking(entity, owner);
        speaker.setOwner(entity);
        speaker.setRange(SimpleRadioLibrary.SERVER_CONFIG.direction_finder.speakingRange);
        speaker.setCategory(CommonRadioPlugin.TRANSCEIVERS_CATEGORY);
        speaker.setLink(this.getClass());

        Frequency frequency = SimpleRadioApi.getInstance(level.isClientSide).frequencies().getOrCreate(frequencyName, modulation);
        Receiver receiver = frequency.getReceiver(owner);
        if (receiver == null) receiver = new DirectionalReceiver(frequency, entity, owner);
        receiver.setOwner(entity);
        receiver.frequencingType(SimpleRadioFrequencing.DIRECTION_FINDER);
        receiver.setLink(this.getClass());
        receiver.tryAddRouter(speaker);
        if (receiver instanceof DirectionalReceiver directional) directional.attenuationDb = attenuation(stack);
    }

    @Override
    public void entityTick(ItemStack stack, Entity entity) {
        super.entityTick(stack, entity);
        if (entity.level().isClientSide) return;
        Receiver receiver = findReceiver(entity.level(), stack.getOrCreateTag());
        if (receiver instanceof DirectionalReceiver directional) directional.attenuationDb = attenuation(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) return super.use(level, player, hand); // settings

        ItemStack stack = player.getItemInHand(hand);
        int current = attenuation(stack);
        int index = 0;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == current) index = i;
        int next = STEPS[(index + 1) % STEPS.length];
        stack.getOrCreateTag().putInt(ATTENUATION, next);

        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.3f, 1.6f);
            player.displayClientMessage(Component.translatable("message.simpleradio.attenuator", next), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag tooltip) {
        components.add(Component.translatable("tooltip.simpleradio.direction_finder.attenuator", attenuation(stack)).withStyle(ChatFormatting.GOLD));
        components.add(Component.translatable("tooltip.simpleradio.direction_finder.hint").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, components, tooltip);
    }
}
