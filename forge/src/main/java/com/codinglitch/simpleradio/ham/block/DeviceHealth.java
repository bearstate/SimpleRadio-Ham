package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.radio.Lightning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Whether a radio burned out (lightning on an unprotected antenna) and its repair.
 */
public class DeviceHealth {
    private boolean burned = false;

    public boolean isBurned() {
        return burned;
    }

    public void setBurned(boolean burned) {
        this.burned = burned;
    }

    /**
     * Once a second: lightning may strike the antenna during a thunderstorm; smoke rises from a burned radio.
     * @return true when the radio just burned out
     */
    public boolean tick(ServerLevel level, BlockPos device, BlockPos antenna, double antennaHeight) {
        if (burned) {
            if (level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.SMOKE, device.getX() + 0.5, device.getY() + 1.0, device.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
            }
            return false;
        }

        if (Lightning.tick(level, antenna, antennaHeight, device, level.random) == Lightning.Outcome.BURNED) {
            burned = true;
            return true;
        }
        return false;
    }

    /**
     * Repairs a burned radio with redstone dust from the player's hand.
     * @return whether the interaction was consumed
     */
    public boolean tryRepair(Player player, InteractionHand hand, BlockPos pos) {
        if (!burned) return false;

        int needed = Math.max(0, SimpleRadioLibrary.SERVER_CONFIG.lightning.repairRedstone);
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.REDSTONE) || stack.getCount() < needed) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.simpleradio.burned", needed).withStyle(ChatFormatting.RED), true);
            }
            return true;
        }

        if (!player.level().isClientSide) {
            if (!player.getAbilities().instabuild) stack.shrink(needed);
            burned = false;
            player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("message.simpleradio.repaired").withStyle(ChatFormatting.GREEN), true);
        }
        return true;
    }

    public void save(CompoundTag tag) {
        if (burned) tag.putBoolean("burned", true);
    }

    public void load(CompoundTag tag) {
        burned = tag.getBoolean("burned");
    }
}
