package com.codinglitch.simpleradio.core.central;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.nbt.CompoundTag;

/**
 * A small internal energy store (in joules) for radio equipment.
 * Filled by Electrodynamics wires or Forge Energy, drained every tick by the owner.
 * <p>
 * The owner is responsible for marking itself dirty (energy changes every tick,
 * so doing it on every change would spam neighbour updates).
 */
public class PowerBuffer {
    private final double capacity;
    private double stored;
    private boolean powered;
    private long wrongVoltageUntil = -1000;

    public PowerBuffer(double capacity) {
        this.capacity = capacity;
    }

    public static boolean isRequired() {
        return SimpleRadioLibrary.SERVER_CONFIG.power.requirePower;
    }

    public double getStored() {
        return stored;
    }

    public double getCapacity() {
        return capacity;
    }

    public double getFreeSpace() {
        return Math.max(0, capacity - stored);
    }

    public void setStored(double stored) {
        this.stored = Math.max(0, Math.min(capacity, stored));
    }

    /**
     * @return the joules actually accepted
     */
    public double receive(double joules, boolean simulate) {
        double accepted = Math.max(0, Math.min(joules, getFreeSpace()));
        if (!simulate && accepted > 0) stored += accepted;
        return accepted;
    }

    /**
     * Uses energy for one tick of operation.
     * @return whether the equipment had enough energy to run this tick
     */
    public boolean consume(double joules) {
        if (!isRequired()) {
            powered = true;
            return true;
        }

        if (stored >= joules) {
            stored -= joules;
            powered = true;
        } else {
            stored = 0;
            powered = false;
        }
        return powered;
    }

    /**
     * @return whether the last {@link #consume(double)} call succeeded
     */
    public boolean isPowered() {
        return powered || !isRequired();
    }

    /**
     * Electricity arrived at a voltage the equipment refuses (e.g. mains instead of 13.8 V from a power supply).
     */
    public void markWrongVoltage(long gameTime) {
        wrongVoltageUntil = gameTime + 40;
    }

    public boolean hasWrongVoltage(long gameTime) {
        return gameTime < wrongVoltageUntil;
    }

    public void save(CompoundTag tag) {
        tag.putDouble("energy", stored);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("energy")) stored = Math.max(0, Math.min(capacity, tag.getDouble("energy")));
    }
}
