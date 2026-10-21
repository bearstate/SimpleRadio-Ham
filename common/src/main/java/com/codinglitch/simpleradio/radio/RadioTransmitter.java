package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import com.codinglitch.simpleradio.routers.Receiver;
import com.codinglitch.simpleradio.routers.Transmitter;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A type of {@link RadioRouter} that accepts {@link RadioMessage}s and transmits them along its connected {@link Frequency}.
 * <br>
 * <b>Does route further.</b>
 */
public class RadioTransmitter extends RadioRouter implements Transmitter {
    public int antennaPower = 0;
    public Frequency frequency;

    /** When this transmitter last sent audio (System.currentTimeMillis). */
    public volatile long lastTransmitTime = 0;

    /** CTCSS tone (Hz) sent with every transmission, 0 for none. */
    public float ctcssTone = 0;

    /** Scales the output, e.g. a catalyst's efficiency or a handheld with a weak battery (range scales with its square root). */
    public float powerMultiplier = 1;

    public FrequencingType frequencingType;

    protected RadioTransmitter(Frequency frequency, UUID id) {
        super(id);
        this.frequency(frequency);
    }
    protected RadioTransmitter(Frequency frequency) {
        this(frequency, UUID.randomUUID());
    }

    public RadioTransmitter(Frequency frequency, Entity owner) {
        this(frequency, owner, UUID.randomUUID());
    }
    public RadioTransmitter(Frequency frequency, Entity owner, UUID uuid) {
        this(frequency, uuid);
        this.owner = owner;
    }
    public RadioTransmitter(Frequency frequency, WorldlyPosition location) {
        this(frequency, location, UUID.randomUUID());
    }
    public RadioTransmitter(Frequency frequency, WorldlyPosition location, UUID uuid) {
        this(frequency, uuid);
        this.position = location;
    }

    @Override
    public int getAntennaPower() {
        return antennaPower;
    }
    @Override
    public float getPower(Frequency.Modulation modulation) {
        int baseTransmissionPower = frequencingType.getTransmissionPower(modulation);

        return (baseTransmissionPower + (antennaPower * frequencingType.antennaAptitude)) * powerMultiplier;
    }

    /**
     * Transmission power towards one receiver; directional transmitters (yagi antennas) override this.
     */
    public float getPowerTowards(@Nullable RadioReceiver receiver) {
        return getPower(this.frequency.getModulation());
    }
    @Override
    public FrequencingType getFrequencingType() {
        return frequencingType;
    }

    @Override
    public RadioTransmitter frequency(Frequency frequency) {
        CommonSimpleRadio.info(frequency);

        if (this.frequency != null) {
            this.frequency.removeTransmitter(this);
        }

        this.frequency = frequency;
        this.routers = (List) this.frequency.getReceivers();
        return this;
    }

    public RadioTransmitter frequencingType(FrequencingType type) {
        this.frequencingType = type;
        return this;
    }

    @Nullable
    @Override
    public Frequency getFrequency() {
        return frequency;
    }

    @Override
    public boolean shouldRouteTo(RadioMessage message, RadioRouter destination) {
        if (destination instanceof RadioReceiver receiver) {
            if (message.willShort(receiver)) return false;

            FrequencingType type = message.frequencingType == -1 ? this.frequencingType : message.getFrequencingType();
            double transmissionPower = message.frequencingType == -1 ? this.getPowerTowards(receiver) : message.transmissionPower;

            RadioLink.Result link = RadioLink.compute(this, receiver, this.frequency);
            double cost = link.effectiveDistance() * RadioLink.factor(message.adjacentLossDb) * type.transmissionDiminishment;

            return (transmissionPower + receiver.getPower(this.getLocation())) >= cost;
        }

        return super.shouldRouteTo(message, destination);
    }

    @Override
    public RadioMessage prepareSource(RadioMessage message, RadioRouter destination) {
        if (message.frequencingType == -1) {
            float transmissionPower = getPowerTowards(destination instanceof RadioReceiver receiver ? receiver : null);

            message.frequencingType = this.frequencingType.id;
            message.transmissionCap = transmissionPower;
            message.addPower(transmissionPower);

            //CommonSimpleRadio.info("transmitting at {}", source.transmissionPower);
        }
        return super.prepareSource(message, destination);
    }

    @Override
    public void take(Message message) {
        if (!this.active) return;
        if (acceptCriteria != null && !acceptCriteria.test(message)) return;

        RadioMessage radioMessage = (RadioMessage) message;
        if (radioMessage.frequencingType == -1) radioMessage.ctcss = ctcssTone;

        // carrier detection for scanners; the end of a transmission is no carrier
        if (!radioMessage.endOfTransmission) this.lastTransmitTime = System.currentTimeMillis();

        Predicate<RadioRouter> notSelf = router -> message.getOwner() == null || !message.getOwner().equals(router.reference);
        this.route(message, notSelf);
        this.routeAdjacent(radioMessage, notSelf);
    }

    /**
     * Ends the current transmission so receivers close their squelch (FM lets a short burst of noise through).
     * @param owner the router the audio came from (e.g. a handheld's reference)
     * @param speaker the player that talked, or null
     */
    public void endTransmission(UUID owner, @Nullable UUID speaker) {
        if (!this.active || System.currentTimeMillis() - lastTransmitTime > 1500) return; // nothing was sent
        WorldlyPosition origin = getLocation();
        if (origin != null) this.take(RadioMessage.endOfTransmission(owner, speaker, origin));
    }

    /**
     * Receivers tuned a few kHz off still hear the signal: FM distorted and weaker, SSB with a shifted pitch.
     */
    protected void routeAdjacent(RadioMessage message, Predicate<RadioRouter> criteria) {
        if (frequency == null) return;
        Frequency.Modulation modulation = frequency.getModulation();
        int spread = OffFrequency.spreadKHz(modulation);
        long center = HamFrequency.parseKHz(frequency.getFrequency());
        if (spread <= 0 || center < 0) return;

        int step = HamFrequency.resolutionKHz();
        for (int offset = step; offset <= spread; offset += step) {
            double lossDb = OffFrequency.lossDb(modulation, offset);
            if (lossDb >= OffFrequency.CUTOFF_DB) break;

            for (int sign = -1; sign <= 1; sign += 2) {
                String name = HamFrequency.format(center + (long) sign * offset);
                Frequency neighbour = name == null ? null : RadioManager.getInstance().frequencies().get(name, modulation);
                if (neighbour == null) continue;

                for (Receiver receiver : List.copyOf(neighbour.getReceivers())) {
                    if (!(receiver instanceof RadioReceiver radioReceiver) || !criteria.test(radioReceiver)) continue;

                    RadioMessage copy = message.copy();
                    copy.adjacentLossDb = (float) lossDb;
                    // a receiver tuned below the transmitter hears the audio higher pitched on SSB
                    copy.offsetHz = -sign * offset * 1000;

                    if (!shouldRouteTo(copy, radioReceiver)) continue;
                    if (routeCriteria != null && !routeCriteria.test(copy, radioReceiver)) continue;
                    radioReceiver.accept(this.prepareSource(copy, radioReceiver));
                }
            }
        }
    }
}
