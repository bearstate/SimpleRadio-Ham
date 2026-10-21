package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.Message;
import com.codinglitch.simpleradio.radio.RadioMessage;
import com.codinglitch.simpleradio.radio.RadioTransmitter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * The output side of a repeater. Everything its input receiver hears is regenerated and
 * transmitted again on the output frequency with the repeater's own transmission power.
 */
public class RepeaterTransmitter extends RadioTransmitter {
    // Ids of active repeaters: their courtesy tones are relayed by other repeaters but must not trigger
    // another courtesy tone, otherwise two cross-linked repeaters would beep at each other forever.
    public static final Set<UUID> REPEATER_IDS = ConcurrentHashMap.newKeySet();

    private final DoubleSupplier bonus;
    private final DoubleSupplier multiplier;
    private final Predicate<RadioMessage> relay;
    private final Consumer<RadioMessage> link;

    /** Who was relayed last, so the end of the transmission reaches the same receivers. */
    public volatile UUID lastOwner;
    public volatile UUID lastRealOwner;

    /**
     * @param relay decides whether an input frame is relayed (e.g. not after the time-out timer tripped)
     * @param link forwards input heard on the air to linked repeaters
     */
    public RepeaterTransmitter(Frequency frequency, WorldlyPosition location, UUID id, DoubleSupplier bonus, DoubleSupplier multiplier,
                               Predicate<RadioMessage> relay, Consumer<RadioMessage> link) {
        super(frequency, location, id);
        this.bonus = bonus;
        this.multiplier = multiplier;
        this.relay = relay;
        this.link = link;
    }

    @Override
    public float getPower(Frequency.Modulation modulation) {
        return (float) (super.getPower(modulation) * multiplier.getAsDouble() + bonus.getAsDouble());
    }

    public static boolean isRepeater(UUID owner) {
        return owner != null && REPEATER_IDS.contains(owner);
    }

    @Override
    public void take(Message message) {
        if (!this.active) return;

        RadioMessage input = (RadioMessage) message;
        // the repeater keeps its carrier up a moment and ends the transmission itself (after the courtesy tone)
        if (input.endOfTransmission) return;
        if (!relay.test(input)) return;

        // Regenerate the signal: it leaves the repeater at full power as a fresh transmission.
        // The owner is kept so the original sender does not hear its own voice echoed back.
        RadioMessage regenerated = input.copy();
        regenerated.frequencingType = -1;
        regenerated.transmissionPower = 50;
        regenerated.transmissionCap = 50;
        regenerated.frequencyMedium = null;
        regenerated.adjacentLossDb = 0;
        regenerated.offsetHz = 0;
        regenerated.signalDb = Float.NaN;
        regenerated.extraNoise = 0;
        regenerated.linked = false;

        lastOwner = regenerated.getOwner();
        lastRealOwner = regenerated.getRealOwner();
        super.take(regenerated);

        // what was heard on the air also goes out over the link (never what came in over it)
        if (!input.linked) link.accept(input);
    }

    /**
     * Transmits the repeater's own audio (courtesy tone, ID, end of transmission) without counting it as relayed traffic.
     */
    public void transmitOwn(Message message) {
        if (!this.active) return;
        super.take(message);
    }
}
