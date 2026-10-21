package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;

/**
 * A transmitter whose power depends on the station's output power, band and (directional) antenna.
 * The power is computed separately for every receiver so a yagi only reaches far in its beam.
 */
public class StationTransmitter extends RadioTransmitter {
    private final Function<WorldlyPosition, Double> powerTowards;

    public StationTransmitter(Frequency frequency, WorldlyPosition location, UUID id, Function<WorldlyPosition, Double> powerTowards) {
        super(frequency, location, id);
        this.powerTowards = powerTowards;
    }

    public float getPowerTowards(@Nullable WorldlyPosition target) {
        return (float) (double) powerTowards.apply(target) * powerMultiplier;
    }

    @Override
    public float getPowerTowards(@Nullable RadioReceiver receiver) {
        return getPowerTowards(receiver == null ? null : receiver.getLocation());
    }

    @Override
    public float getPower(Frequency.Modulation modulation) {
        return getPowerTowards((WorldlyPosition) null);
    }
}
