package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.RadioReceiver;

import java.util.UUID;
import java.util.function.Function;

/**
 * A receiver whose sensitivity depends on the direction of the transmitter (yagi antennas)
 * and the height of the antenna. Also used by repeaters (omnidirectional).
 */
public class StationReceiver extends RadioReceiver {
    private final Function<WorldlyPosition, Double> powerFrom;

    public StationReceiver(Frequency frequency, WorldlyPosition location, UUID id, Function<WorldlyPosition, Double> powerFrom) {
        super(frequency, location, id);
        this.powerFrom = powerFrom;
    }

    @Override
    public float getPower() {
        return (float) (double) powerFrom.apply(null);
    }

    @Override
    public float getPower(WorldlyPosition from) {
        return (float) (double) powerFrom.apply(from);
    }
}
