package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.power.WireNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;

/**
 * The coal generator's works: a stack of coal, the fire it keeps, and the power it holds and hands on. It burns a
 * piece only while its store is not full, so a generator with nothing to feed idles cold; a piece of coal gives as long
 * as it burns in a furnace, at forty FE a tick.
 */
public class CoalGeneratorBlockEntity extends BlockEntity {

    /** Power made a tick while it burns, and the most it hands on in a tick. */
    public static final int MAKES = 40, HANDS_ON = 200;
    /** What it holds, in FE. */
    public static final int HOLDS = 20_000;
    /** The most coal it takes in. */
    public static final int FUEL_MOST = 64;

    private ItemStack fuel = ItemStack.EMPTY;
    private int burning, burnTotal;
    private double stored;
    private List<WireNetwork.Sink> sinks = List.of();
    private long sinksAt = -100;

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.COAL_GENERATOR_BE.get(), pos, state);
    }

    /** Coal, charcoal and blocks of coal: what such a generator burns. */
    public static boolean isFuel(ItemStack stack) {
        return stack.is(ItemTags.COALS) || stack.is(Items.COAL_BLOCK);
    }

    /** As much of a stack as there is room for, taken in; how many were taken. */
    public int addFuel(ItemStack stack) {
        if (!isFuel(stack)) return 0;
        if (!fuel.isEmpty() && !ItemStack.isSameItemSameTags(fuel, stack)) return 0;
        int room = FUEL_MOST - fuel.getCount();
        int taken = Math.min(room, stack.getCount());
        if (taken <= 0) return 0;
        if (fuel.isEmpty()) fuel = stack.copyWithCount(taken);
        else fuel.grow(taken);
        setChanged();
        return taken;
    }

    public void dropFuel() {
        if (level != null && !fuel.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, fuel);
            fuel = ItemStack.EMPTY;
        }
    }

    /** What it shows when looked at: fuel, fire, store, and how many things it feeds. */
    public Component status() {
        return Component.translatable("message.simpleradio.generator_status", fuel.getCount(),
                Component.translatable(burning > 0 ? "message.simpleradio.generator_burning" : "message.simpleradio.generator_cold"),
                (int) stored, HOLDS, sinks.size());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoalGeneratorBlockEntity g) {
        long now = level.getGameTime();
        if (now - g.sinksAt >= 40) {
            g.sinks = WireNetwork.sinks(level, pos);
            g.sinksAt = now;
        }
        // The fire: a new piece when the last is out and the store has room.
        if (g.burning <= 0 && g.stored < HOLDS - MAKES * 20 && !g.fuel.isEmpty()) {
            int burn = ForgeHooks.getBurnTime(g.fuel, null);
            if (burn > 0) {
                g.burnTotal = g.burning = burn;
                g.fuel.shrink(1);
                if (g.fuel.isEmpty()) g.fuel = ItemStack.EMPTY;
                g.setChanged();
            }
        }
        if (g.burning > 0) {
            g.burning--;
            g.stored = Math.min(HOLDS, g.stored + MAKES);
        }
        // Handed on: shared between what takes it along the runs.
        if (g.stored > 0 && !g.sinks.isEmpty()) {
            int share = (int) Math.min(g.stored, HANDS_ON) / g.sinks.size();
            for (WireNetwork.Sink sink : g.sinks) {
                if (sink.entity().isRemoved()) continue;
                IEnergyStorage storage = sink.storage();
                if (storage == null) continue;
                g.stored -= storage.receiveEnergy(share, false);
            }
        }
        boolean lit = g.burning > 0;
        if (state.getValue(CoalGeneratorBlock.LIT) != lit) level.setBlock(pos, state.setValue(CoalGeneratorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        if (now % 100 == 0) g.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!fuel.isEmpty()) tag.put("Fuel", fuel.save(new CompoundTag()));
        tag.putInt("Burning", burning);
        tag.putInt("BurnTotal", burnTotal);
        tag.putDouble("Stored", stored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        fuel = tag.contains("Fuel") ? ItemStack.of(tag.getCompound("Fuel")) : ItemStack.EMPTY;
        burning = tag.getInt("Burning");
        burnTotal = tag.getInt("BurnTotal");
        stored = tag.getDouble("Stored");
    }
}
