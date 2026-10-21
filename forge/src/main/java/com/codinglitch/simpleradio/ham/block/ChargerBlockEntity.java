package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.item.BatteryItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Holds one handheld radio and charges its battery from the grid.
 */
public class ChargerBlockEntity extends BlockEntity implements Powered {
    public final PowerBuffer power = new PowerBuffer(2_000);
    private ItemStack stack = ItemStack.EMPTY;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.CHARGER_BE.get(), pos, state);
    }

    @Override
    public PowerBuffer getPowerBuffer() {
        return power;
    }

    public ItemStack getStack() {
        return stack;
    }

    public static boolean accepts(ItemStack stack) {
        return stack.getItem() instanceof TransceiverItem || stack.getItem() instanceof BatteryItem;
    }

    /** Charge of a docked handheld or battery pack, in battery ticks. */
    public static int getCharge(ItemStack stack) {
        return stack.getItem() instanceof BatteryItem ? BatteryItem.getCharge(stack) : TransceiverItem.getBattery(stack);
    }

    public static void setCharge(ItemStack stack, int charge) {
        if (stack.getItem() instanceof BatteryItem) BatteryItem.setCharge(stack, charge);
        else TransceiverItem.setBattery(stack, charge);
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
        sync();
    }

    public ItemStack takeStack() {
        ItemStack taken = this.stack;
        this.stack = ItemStack.EMPTY;
        sync();
        return taken;
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargerBlockEntity charger) {
        int status = ChargerBlock.STATUS_IDLE;

        if (accepts(charger.stack)) {
            int battery = getCharge(charger.stack);
            int max = TransceiverItem.maxBattery();

            if (battery >= max) {
                status = ChargerBlock.STATUS_FULL;
            } else if (charger.power.consume(SimpleRadioLibrary.SERVER_CONFIG.power.chargerUsage)) {
                setCharge(charger.stack, battery + SimpleRadioLibrary.SERVER_CONFIG.charger.chargeRate);
                status = ChargerBlock.STATUS_CHARGING;
                if (level.getGameTime() % 20 == 0) charger.sync();
            }
        }

        if (state.getValue(ChargerBlock.STATUS) != status) {
            level.setBlock(pos, state.setValue(ChargerBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
        if (level.getGameTime() % 20 == 0) charger.setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stack = tag.contains("item") ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY;
        power.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!stack.isEmpty()) tag.put("item", stack.save(new CompoundTag()));
        power.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (!stack.isEmpty()) tag.put("item", stack.save(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        stack = tag.contains("item") ? ItemStack.of(tag.getCompound("item")) : ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        handleUpdateTag(tag == null ? new CompoundTag() : tag);
    }
}
