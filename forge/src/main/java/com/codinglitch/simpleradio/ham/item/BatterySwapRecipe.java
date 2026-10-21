package com.codinglitch.simpleradio.ham.item;

import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Handheld + battery pack in the crafting grid: the handheld takes the battery's charge and the
 * battery that is left behind holds whatever charge the handheld had before (usually empty).
 */
public class BatterySwapRecipe extends CustomRecipe {
    public BatterySwapRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private record Parts(int handheldSlot, int batterySlot) {}

    private static Parts find(CraftingContainer container) {
        int handheld = -1, battery = -1;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof TransceiverItem && handheld < 0) handheld = slot;
            else if (stack.getItem() instanceof BatteryItem && battery < 0) battery = slot;
            else return null; // anything else (or a second radio/battery) does not match
        }
        return handheld >= 0 && battery >= 0 ? new Parts(handheld, battery) : null;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return find(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        Parts parts = find(container);
        if (parts == null) return ItemStack.EMPTY;

        ItemStack handheld = container.getItem(parts.handheldSlot()).copy();
        handheld.setCount(1);
        TransceiverItem.setBattery(handheld, BatteryItem.getCharge(container.getItem(parts.batterySlot())));

        // the radio is re-activated with fresh routers
        if (handheld.hasTag()) {
            handheld.getTag().remove("reference");
            handheld.getTag().remove("using");
        }
        return handheld;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(container);

        Parts parts = find(container);
        if (parts != null) {
            ItemStack oldBattery = new ItemStack(HamContent.BATTERY.get());
            BatteryItem.setCharge(oldBattery, TransceiverItem.getBattery(container.getItem(parts.handheldSlot())));
            remaining.set(parts.batterySlot(), oldBattery);
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HamContent.BATTERY_SWAP.get();
    }
}
