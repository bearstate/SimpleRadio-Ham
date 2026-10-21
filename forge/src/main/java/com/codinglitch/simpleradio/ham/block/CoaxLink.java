package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A coaxial cable run from an antenna to a station or repeater. Remembers how many cable items
 * were used so they can be given back when the cable is removed.
 */
public class CoaxLink {
    @Nullable private BlockPos antenna;
    private int items;
    private double length;

    /**
     * Something a coax cable can be connected to (station, repeater).
     */
    public interface Endpoint {
        CoaxLink getCoax();

        boolean acceptsAntenna(net.minecraft.world.level.block.state.BlockState antenna);

        void onCoaxChanged();
    }

    public boolean isConnected() {
        return antenna != null;
    }

    @Nullable
    public BlockPos getAntenna() {
        return antenna;
    }

    public double getLength() {
        return length;
    }

    public void connect(BlockPos antenna, int items, double length, Level level, BlockPos owner) {
        disconnect(level, owner);
        this.antenna = antenna.immutable();
        this.items = items;
        this.length = length;
    }

    /**
     * Removes the cable and drops its items at the owner.
     */
    public void disconnect(Level level, BlockPos owner) {
        if (antenna != null && items > 0 && !level.isClientSide) {
            Containers.dropItemStack(level, owner.getX() + 0.5, owner.getY() + 1, owner.getZ() + 0.5,
                    new ItemStack(HamContent.COAX_CABLE.get(), items));
        }
        antenna = null;
        items = 0;
        length = 0;
    }

    /**
     * Fraction of the power that survives the cable (both directions).
     */
    public double lossFactor(long kHz) {
        if (antenna == null) return 1;

        double lossPer100 = switch (HamFrequency.bandOf(kHz)) {
            case HF -> SimpleRadioLibrary.SERVER_CONFIG.coax.hfLossPer100;
            case VHF -> SimpleRadioLibrary.SERVER_CONFIG.coax.vhfLossPer100;
            case UHF -> SimpleRadioLibrary.SERVER_CONFIG.coax.uhfLossPer100;
        };
        return Math.pow(10, -(lossPer100 * length / 100.0) / 10.0);
    }

    public double lossDb(long kHz) {
        return -10 * Math.log10(lossFactor(kHz));
    }

    public void save(CompoundTag tag) {
        if (antenna == null) return;
        CompoundTag coax = new CompoundTag();
        coax.put("antenna", NbtUtils.writeBlockPos(antenna));
        coax.putInt("items", items);
        coax.putDouble("length", length);
        tag.put("coax", coax);
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("coax")) {
            antenna = null;
            items = 0;
            length = 0;
            return;
        }
        CompoundTag coax = tag.getCompound("coax");
        antenna = NbtUtils.readBlockPos(coax.getCompound("antenna"));
        items = coax.getInt("items");
        length = coax.getDouble("length");
    }
}
