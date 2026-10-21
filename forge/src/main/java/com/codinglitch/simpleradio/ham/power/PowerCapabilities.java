package com.codinglitch.simpleradio.ham.power;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.ham.compat.VoltaicPower;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Attaches electricity capabilities to every {@link Powered} block entity:
 * Forge Energy always, and the Electrodynamics (Voltaic) capability when that library is present.
 */
public class PowerCapabilities {
    private static final ResourceLocation KEY = CommonSimpleRadio.id("power");
    private static Boolean voltaicLoaded;

    public static boolean isVoltaicLoaded() {
        if (voltaicLoaded == null) voltaicLoaded = ModList.get().isLoaded("voltaic");
        return voltaicLoaded;
    }

    /**
     * Whether a block entity takes part in Electrodynamics networks (a machine, generator or wire).
     */
    public static boolean hasElectrodynamic(BlockEntity blockEntity) {
        return isVoltaicLoaded() && VoltaicPower.hasCapability(blockEntity);
    }

    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof Powered powered)) return;

        Provider provider = new Provider(event.getObject(), powered);
        event.addCapability(KEY, provider);
        event.addListener(provider::invalidate);
    }

    private static class Provider implements ICapabilityProvider {
        private final LazyOptional<IEnergyStorage> energy;
        private final LazyOptional<?> electrodynamic;

        Provider(BlockEntity blockEntity, Powered powered) {
            // The buffer is fetched lazily: capabilities are gathered before subclass fields exist
            this.energy = LazyOptional.of(() -> new EnergyAdapter(powered));
            this.electrodynamic = isVoltaicLoaded() ? VoltaicPower.create(blockEntity, powered) : LazyOptional.empty();
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            if (capability == ForgeCapabilities.ENERGY) return energy.cast();
            if (isVoltaicLoaded() && VoltaicPower.isCapability(capability)) return electrodynamic.cast();
            return LazyOptional.empty();
        }

        void invalidate() {
            energy.invalidate();
            electrodynamic.invalidate();
        }
    }

    /**
     * Forge Energy view of a {@link PowerBuffer} (1 FE = 1 J). Receive only.
     */
    private record EnergyAdapter(Powered powered) implements IEnergyStorage {
        private PowerBuffer buffer() {
            return powered.getPowerBuffer();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            PowerBuffer buffer = buffer();
            if (buffer == null) return 0;
            return (int) Math.floor(buffer.receive(maxReceive, simulate));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            PowerBuffer buffer = buffer();
            return buffer == null ? 0 : (int) buffer.getStored();
        }

        @Override
        public int getMaxEnergyStored() {
            PowerBuffer buffer = buffer();
            return buffer == null ? 0 : (int) buffer.getCapacity();
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
