package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.routers.Transmitter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Carrier detection for scanners: is a transmitter within reach on the air on a frequency right now?
 */
public final class Carrier {
    private static final long RECENT_MILLIS = 500;

    private Carrier() {}

    public static boolean present(@Nullable Frequency frequency, RadioReceiver probe, Level level) {
        if (frequency == null) return false;

        long cutoff = System.currentTimeMillis() - RECENT_MILLIS;
        for (Transmitter transmitter : List.copyOf(frequency.getTransmitters())) {
            if (!(transmitter instanceof RadioTransmitter radioTransmitter)) continue;
            if (!radioTransmitter.isActive() || radioTransmitter.lastTransmitTime < cutoff) continue;
            if (radioTransmitter.frequencingType == null) continue;

            WorldlyPosition from = radioTransmitter.getLocation();
            if (from == null || from.level != level) continue;
            if (probe.reference.equals(radioTransmitter.reference)) continue; // not our own transmitter

            RadioMessage test = new RadioMessage(probe.reference, from, new short[0], 1f);
            if (radioTransmitter.shouldRouteTo(test, probe)) return true;
        }
        return false;
    }
}
