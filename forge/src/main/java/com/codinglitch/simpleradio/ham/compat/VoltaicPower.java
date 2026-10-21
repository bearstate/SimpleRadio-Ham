package com.codinglitch.simpleradio.ham.compat;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.ham.block.PowerSupplyBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import voltaic.api.electricity.ICapabilityElectrodynamic;
import voltaic.prefab.utilities.object.TransferPack;
import voltaic.registers.VoltaicCapabilities;

/**
 * Electrodynamics (Voltaic API) electricity input for radio equipment.
 * Only class-loaded when the "voltaic" mod is present.
 */
public final class VoltaicPower {
    private VoltaicPower() {}

    public static boolean isCapability(Capability<?> capability) {
        return capability == VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK;
    }

    public static boolean hasCapability(BlockEntity blockEntity) {
        if (blockEntity.getCapability(VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK).isPresent()) return true;
        for (Direction direction : Direction.values()) {
            if (blockEntity.getCapability(VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK, direction).isPresent()) return true;
        }
        return false;
    }

    public static LazyOptional<?> create(BlockEntity blockEntity, Powered powered) {
        return LazyOptional.of(() -> new Electrodynamic(blockEntity, powered));
    }

    public static double mainsVoltage() {
        return SimpleRadioLibrary.SERVER_CONFIG.power.voltage;
    }

    /**
     * The voltage a piece of equipment runs on: 13.8 V DC for amateur radio gear, mains for the rest.
     */
    public static double nominalVoltage(Powered powered) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.power;
        return powered.needsDc() && config.requirePsu ? config.dcVoltage : config.voltage;
    }

    private record Electrodynamic(BlockEntity blockEntity, Powered powered) implements ICapabilityElectrodynamic {
        private PowerBuffer buffer() {
            return powered.getPowerBuffer();
        }

        @Override
        public double getJoulesStored() {
            PowerBuffer buffer = buffer();
            return buffer == null ? 0 : buffer.getStored();
        }

        @Override
        public double getMaxJoulesStored() {
            PowerBuffer buffer = buffer();
            return buffer == null ? 0 : buffer.getCapacity();
        }

        @Override
        public void setJoulesStored(double joules) {
            PowerBuffer buffer = buffer();
            if (buffer != null) buffer.setStored(joules);
        }

        @Override
        public double getVoltage() {
            return nominalVoltage(powered);
        }

        @Override
        public boolean isEnergyReceiver() {
            return true;
        }

        @Override
        public boolean isEnergyProducer() {
            return false;
        }

        @Override
        public TransferPack extractPower(TransferPack transfer, boolean debug) {
            return TransferPack.EMPTY;
        }

        @Override
        public TransferPack receivePower(TransferPack transfer, boolean debug) {
            PowerBuffer buffer = buffer();
            if (buffer == null) return TransferPack.EMPTY;

            double nominal = nominalVoltage(powered);
            double voltage = transfer.getVoltage();
            // mains equipment (original radios, the charger) also runs on a 13.8 V DC line
            double dc = SimpleRadioLibrary.SERVER_CONFIG.power.dcVoltage;
            if (!powered.needsDc() && voltage >= dc * 0.9 && voltage <= dc * 1.1) nominal = dc;
            // Under-voltage cannot run the equipment
            if (voltage < nominal * 0.9) return TransferPack.EMPTY;

            // 13.8 V equipment on mains: its protection refuses the power (explosions only above the mains rating)
            if (voltage > nominal * 1.1 && powered.needsDc() && SimpleRadioLibrary.SERVER_CONFIG.power.requirePsu && voltage <= mainsVoltage() * 1.1) {
                refuse();
                return TransferPack.EMPTY;
            }

            double accepted = buffer.receive(transfer.getJoules(), true);
            if (accepted <= 0) return TransferPack.EMPTY;

            if (!debug) {
                // like Electrodynamics machines: the network sends the power and the equipment blows up
                if (voltage > nominal * 1.1) {
                    overVoltage(transfer);
                    return TransferPack.EMPTY;
                }
                buffer.receive(accepted, false);
                onChange();
            }
            return TransferPack.joulesVoltage(accepted, voltage);
        }

        private void refuse() {
            Level level = blockEntity.getLevel();
            PowerBuffer buffer = buffer();
            if (level == null || buffer == null) return;

            boolean wasWarned = buffer.hasWrongVoltage(level.getGameTime());
            buffer.markWrongVoltage(level.getGameTime());
            if (!wasWarned && level instanceof ServerLevel serverLevel) {
                BlockPos pos = blockEntity.getBlockPos();
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.05);
            }
        }

        @Override
        public void overVoltage(TransferPack transfer) {
            if (!SimpleRadioLibrary.SERVER_CONFIG.power.explodeOnOverVoltage) return;

            Level level = blockEntity.getLevel();
            if (level == null || level.isClientSide) return;

            BlockPos pos = blockEntity.getBlockPos();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    (float) Math.log10(10 + transfer.getVoltage() / nominalVoltage(powered)), Level.ExplosionInteraction.BLOCK);
        }

        @Override
        public void onChange() {
            // the owner marks itself dirty periodically
        }

        @Override
        public TransferPack getConnectedLoad(LoadProfile loadProfile, Direction direction) {
            PowerBuffer buffer = buffer();
            if (buffer == null) return TransferPack.EMPTY;
            return TransferPack.joulesVoltage(buffer.getFreeSpace(), nominalVoltage(powered));
        }
    }

    // ---- Power supply ---- \\

    /**
     * Capabilities of the 13.8 V power supply: mains in on every side but the front, DC out of the front.
     */
    public static LazyOptional<?> powerSupply(PowerSupplyBlockEntity supply, Direction side) {
        return LazyOptional.of(() -> side == supply.outputSide() ? new SupplyOutput() : new SupplyInput(supply));
    }

    /**
     * Pushes DC into whatever is connected to the supply's front (a radio or an Electrodynamics wire).
     * @return the joules accepted
     */
    public static double pushDc(PowerSupplyBlockEntity supply, double joules, boolean debug) {
        Level level = supply.getLevel();
        if (level == null || joules <= 0) return 0;

        Direction out = supply.outputSide();
        BlockEntity target = level.getBlockEntity(supply.getBlockPos().relative(out));
        if (target == null) return 0;

        var capability = target.getCapability(VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK, out.getOpposite());
        if (!capability.isPresent()) return -1; // not an Electrodynamics device: try Forge Energy

        double voltage = SimpleRadioLibrary.SERVER_CONFIG.power.dcVoltage;
        TransferPack accepted = capability.orElseThrow(IllegalStateException::new).receivePower(TransferPack.joulesVoltage(joules, voltage), debug);
        return accepted.getJoules();
    }

    private record SupplyInput(PowerSupplyBlockEntity supply) implements ICapabilityElectrodynamic {
        @Override
        public double getJoulesStored() {
            return 0;
        }

        @Override
        public double getMaxJoulesStored() {
            return 0;
        }

        @Override
        public void setJoulesStored(double joules) {}

        @Override
        public double getVoltage() {
            return mainsVoltage();
        }

        @Override
        public boolean isEnergyReceiver() {
            return true;
        }

        @Override
        public boolean isEnergyProducer() {
            return false;
        }

        @Override
        public TransferPack extractPower(TransferPack transfer, boolean debug) {
            return TransferPack.EMPTY;
        }

        @Override
        public TransferPack receivePower(TransferPack transfer, boolean debug) {
            double mains = mainsVoltage();
            double voltage = transfer.getVoltage();
            if (voltage < mains * 0.9) return TransferPack.EMPTY;
            if (voltage > mains * 1.1) {
                // accept the offer, then blow up when the power actually arrives (Electrodynamics behaviour)
                if (debug) return TransferPack.joulesVoltage(transfer.getJoules(), voltage);
                overVoltage(transfer);
                return TransferPack.EMPTY;
            }

            double efficiency = Math.max(0.05, Math.min(1, SimpleRadioLibrary.SERVER_CONFIG.power.psuEfficiency));
            double delivered = supply.deliver(transfer.getJoules() * efficiency, debug);
            if (delivered <= 0) return TransferPack.EMPTY;
            return TransferPack.joulesVoltage(delivered / efficiency, voltage);
        }

        @Override
        public void overVoltage(TransferPack transfer) {
            if (!SimpleRadioLibrary.SERVER_CONFIG.power.explodeOnOverVoltage) return;

            Level level = supply.getLevel();
            if (level == null || level.isClientSide) return;

            BlockPos pos = supply.getBlockPos();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    (float) Math.log10(10 + transfer.getVoltage() / mainsVoltage()), Level.ExplosionInteraction.BLOCK);
        }

        @Override
        public void onChange() {}

        @Override
        public TransferPack getConnectedLoad(LoadProfile loadProfile, Direction direction) {
            double possible = supply.deliver(Double.MAX_VALUE / 4, true);
            double efficiency = Math.max(0.05, Math.min(1, SimpleRadioLibrary.SERVER_CONFIG.power.psuEfficiency));
            return TransferPack.joulesVoltage(Math.max(0, possible / efficiency), mainsVoltage());
        }
    }

    private static final class SupplyOutput implements ICapabilityElectrodynamic {
        @Override
        public double getJoulesStored() {
            return 0;
        }

        @Override
        public double getMaxJoulesStored() {
            return 0;
        }

        @Override
        public void setJoulesStored(double joules) {}

        @Override
        public double getVoltage() {
            return SimpleRadioLibrary.SERVER_CONFIG.power.dcVoltage;
        }

        @Override
        public boolean isEnergyReceiver() {
            return false;
        }

        @Override
        public boolean isEnergyProducer() {
            return true;
        }

        @Override
        public TransferPack extractPower(TransferPack transfer, boolean debug) {
            return TransferPack.EMPTY;
        }

        @Override
        public TransferPack receivePower(TransferPack transfer, boolean debug) {
            return TransferPack.EMPTY;
        }

        @Override
        public void onChange() {}

        @Override
        public TransferPack getConnectedLoad(LoadProfile loadProfile, Direction direction) {
            return TransferPack.EMPTY;
        }
    }
}
