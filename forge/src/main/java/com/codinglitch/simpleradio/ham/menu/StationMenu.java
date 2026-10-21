package com.codinglitch.simpleradio.ham.menu;

import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.StationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-less menu of the station, used to sync live status (energy, TX, antenna) to the screen.
 */
public class StationMenu extends AbstractContainerMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_FLAGS = 2;
    public static final int DATA_YAGI = 3;
    public static final int DATA_MAST = 4;
    public static final int DATA_RANGE = 5;
    public static final int DATA_ANTENNA = 6;
    public static final int DATA_COAX = 7;
    public static final int DATA_COAX_LOSS = 8;
    public static final int DATA_SIGNAL = 9;
    public static final int DATA_SWR = 10;
    public static final int DATA_TEMPERATURE = 11;
    public static final int DATA_SOLAR_FLUX = 12;
    public static final int DATA_K_INDEX = 13;
    public static final int DATA_BANDS = 14;
    public static final int DATA_NOISE = 15;
    public static final int DATA_COUNT = 16;

    public static final int ANTENNA_MAST = 0;
    public static final int ANTENNA_YAGI_MAST = 1;
    public static final int ANTENNA_YAGI_COAX = 2;
    public static final int ANTENNA_VERTICAL = 3;

    public static final int FLAG_POWERED = 1;
    public static final int FLAG_TRANSMITTING = 2;
    public static final int FLAG_KEYED = 4;
    public static final int FLAG_CW = 8;
    public static final int FLAG_RECEIVING = 16;
    public static final int FLAG_BURNED = 32;
    public static final int FLAG_TUNER = 64;
    public static final int FLAG_OVERHEATED = 128;
    public static final int FLAG_VOX = 256;
    public static final int FLAG_DENIED = 512;
    public static final int FLAG_WRONG_VOLTAGE = 1024;

    public final BlockPos pos;
    private final ContainerData data;

    public StationMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainerData(DATA_COUNT));
    }

    public StationMenu(int containerId, Inventory inventory, BlockPos pos, ContainerData data) {
        super(HamContent.STATION_MENU.get(), containerId);
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
        return player.level().getBlockState(pos).getBlock() instanceof StationBlock
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
