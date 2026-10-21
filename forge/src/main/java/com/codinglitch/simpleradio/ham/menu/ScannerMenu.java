package com.codinglitch.simpleradio.ham.menu;

import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.ScannerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-less menu of the scanner, used to sync the sweeping channel and status to the screen.
 * Menu data is sent as 16 bit values, so the channel is split into MHz and kHz parts.
 */
public class ScannerMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_FLAGS = 2;
    public static final int DATA_CURSOR_MHZ = 3;
    public static final int DATA_CURSOR_KHZ = 4;
    public static final int DATA_SIGNAL = 5;
    public static final int DATA_COUNT = 6;

    public static final int FLAG_POWERED = 1;
    public static final int FLAG_SCANNING = 2;
    public static final int FLAG_HOLDING = 4;
    public static final int FLAG_AUDIO = 8;
    public static final int FLAG_BURNED = 16;

    public final BlockPos pos;
    private final ContainerData data;

    public ScannerMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    public ScannerMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(HamContent.SCANNER_MENU.get(), containerId);
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

    public long cursorKHz() {
        return data.get(DATA_CURSOR_MHZ) * 1000L + data.get(DATA_CURSOR_KHZ);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof ScannerBlock
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
