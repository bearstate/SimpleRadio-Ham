package com.codinglitch.simpleradio.ham.menu;

import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.RepeaterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-less menu of the repeater, used to sync live status to the screen.
 */
public class RepeaterMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_FLAGS = 2;
    public static final int DATA_RANGE = 3;
    public static final int DATA_LINKED = 4;
    public static final int DATA_SIGNAL = 5;
    public static final int DATA_COUNT = 6;

    public static final int FLAG_POWERED = 1;
    public static final int FLAG_ENABLED = 2;
    public static final int FLAG_RELAYING = 4;
    public static final int FLAG_BURNED = 8;
    public static final int FLAG_TIMED_OUT = 16;

    public final BlockPos pos;
    private final ContainerData data;

    public RepeaterMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    public RepeaterMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(HamContent.REPEATER_MENU.get(), containerId);
        this.pos = pos;
        this.data = data;
        this.addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean hasFlag(int flag) {
        return (data.get(DATA_FLAGS) & flag) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof RepeaterBlock
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
