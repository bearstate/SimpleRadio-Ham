package com.codinglitch.simpleradio.ham.menu;

import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.SstvTerminalBlock;
import com.codinglitch.simpleradio.ham.block.SstvTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Menu of the SSTV terminal: a filled map to send, blank maps to print on and the printed picture.
 */
public class SstvMenu extends AbstractContainerMenu {
    public static final int DATA_FLAGS = 0;
    public static final int DATA_SEND_PROGRESS = 1;
    public static final int DATA_RECEIVE_PROGRESS = 2;
    public static final int DATA_COUNT = 3;

    public static final int FLAG_STATION = 1;
    public static final int FLAG_SENDING = 2;
    public static final int FLAG_RECEIVING = 4;
    public static final int FLAG_NEEDS_MAP = 8;

    public static final int SLOT_X = 8;
    public static final int SEND_Y = 26;
    public static final int BLANK_Y = 62;
    public static final int OUTPUT_Y = 98;
    public static final int INVENTORY_X = 34;
    public static final int INVENTORY_Y = 130;

    public final BlockPos pos;
    private final Container container;
    private final ContainerData data;

    public SstvMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos(), new SimpleContainer(3), new SimpleContainerData(DATA_COUNT));
    }

    public SstvMenu(int containerId, Inventory inventory, BlockPos pos, Container container, ContainerData data) {
        super(HamContent.SSTV_MENU.get(), containerId);
        this.pos = pos;
        this.container = container;
        this.data = data;

        addSlot(new Slot(container, SstvTerminalBlockEntity.SLOT_SEND, SLOT_X, SEND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.FILLED_MAP);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(container, SstvTerminalBlockEntity.SLOT_BLANK, SLOT_X, BLANK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.MAP);
            }
        });
        addSlot(new Slot(container, SstvTerminalBlockEntity.SLOT_OUTPUT, SLOT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }

        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    public boolean hasFlag(int flag) {
        return (data.get(DATA_FLAGS) & flag) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int terminalSlots = 3;
        int end = slots.size();

        if (index < terminalSlots) {
            if (!moveItemStackTo(stack, terminalSlots, end, true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.MAP)) {
            if (!moveItemStackTo(stack, SstvTerminalBlockEntity.SLOT_BLANK, SstvTerminalBlockEntity.SLOT_BLANK + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.FILLED_MAP)) {
            if (!moveItemStackTo(stack, SstvTerminalBlockEntity.SLOT_SEND, SstvTerminalBlockEntity.SLOT_SEND + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).getBlock() instanceof SstvTerminalBlock
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
