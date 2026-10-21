package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.compat.VoltaicPower;
import com.codinglitch.simpleradio.ham.power.PowerCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * A 13.8 V DC power supply: mains (Electrodynamics wires or Forge Energy) goes in on any side, and the
 * converted power comes out of the front into a radio or into a wire that feeds several radios.
 */
public class PowerSupplyBlockEntity extends BlockEntity {
    private final Map<Direction, LazyOptional<?>> electrodynamic = new EnumMap<>(Direction.class);
    private final LazyOptional<IEnergyStorage> energyInput = LazyOptional.of(() -> new EnergyInput(this));
    private long lastDelivery = -100;

    public PowerSupplyBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.POWER_SUPPLY_BE.get(), pos, state);
    }

    public Direction outputSide() {
        return getBlockState().getValue(PowerSupplyBlock.FACING);
    }

    /**
     * Converted DC delivered out of the front.
     * @return the joules the connected equipment accepted
     */
    public double deliver(double joules, boolean simulate) {
        Level level = getLevel();
        if (level == null || level.isClientSide || joules <= 0) return 0;

        double accepted = PowerCapabilities.isVoltaicLoaded() ? VoltaicPower.pushDc(this, joules, simulate) : -1;
        if (accepted < 0) accepted = pushEnergy(level, joules, simulate);

        if (accepted > 0 && !simulate) lastDelivery = level.getGameTime();
        return Math.max(0, accepted);
    }

    private double pushEnergy(Level level, double joules, boolean simulate) {
        Direction out = outputSide();
        BlockEntity target = level.getBlockEntity(worldPosition.relative(out));
        if (target == null) return 0;

        int amount = (int) Math.min(Integer.MAX_VALUE, Math.floor(joules));
        return target.getCapability(ForgeCapabilities.ENERGY, out.getOpposite())
                .map(storage -> storage.receiveEnergy(amount, simulate)).orElse(0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerSupplyBlockEntity supply) {
        if (level.getGameTime() % 10 != 0) return;
        boolean on = level.getGameTime() - supply.lastDelivery < 20;
        if (state.getValue(PowerSupplyBlock.ON) != on) level.setBlock(pos, state.setValue(PowerSupplyBlock.ON, on), Block.UPDATE_CLIENTS);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (side != null && PowerCapabilities.isVoltaicLoaded() && VoltaicPower.isCapability(capability)) {
            return electrodynamic.computeIfAbsent(side, direction -> VoltaicPower.powerSupply(this, direction)).cast();
        }
        if (capability == ForgeCapabilities.ENERGY && side != outputSide()) return energyInput.cast();
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        electrodynamic.values().forEach(LazyOptional::invalidate);
        electrodynamic.clear();
        energyInput.invalidate();
    }

    @Override
    public void setBlockState(BlockState state) {
        Direction before = getBlockState().getValue(PowerSupplyBlock.FACING);
        super.setBlockState(state);
        if (before != state.getValue(PowerSupplyBlock.FACING)) {
            // sides changed their role
            electrodynamic.values().forEach(LazyOptional::invalidate);
            electrodynamic.clear();
        }
    }

    /**
     * Forge Energy in (1 FE = 1 J), pushed straight on to the front.
     */
    private record EnergyInput(PowerSupplyBlockEntity supply) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            double efficiency = Math.max(0.05, Math.min(1, SimpleRadioLibrary.SERVER_CONFIG.power.psuEfficiency));
            double delivered = supply.deliver(maxReceive * efficiency, simulate);
            return (int) Math.min(maxReceive, Math.ceil(delivered / efficiency));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 1000;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
