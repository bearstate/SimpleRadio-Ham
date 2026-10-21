package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.registry.FrequencingRegistry;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.entities.Wire;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import com.codinglitch.simpleradio.routers.Router;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import net.minecraft.sounds.SoundEvent;
import org.joml.Math;

import java.util.UUID;

/**
 * A message representing a packet for radio transmission containing the
 * audio data in a {@link Source} as well as other data collected while travelling.
 */
public class RadioMessage implements Message {

    public UUID owner;
    public UUID originalOwner;
    public WorldlyPosition origin;
    public short frequencingType = -1;

    public Source source;

    public float pitch = 1;
    public float volume;
    public float offset;
    public long seed;

    public float activity;

    public ShortArrayList record = new ShortArrayList();

    public Frequency frequencyMedium;
    public Wire wireMedium;

    public float transmissionCap = 50;
    public float transmissionPower = 50;

    /** Signal strength at the last receiver, in dB above the edge of reception (NaN until received). */
    public float signalDb = Float.NaN;
    /** Extra noise (0-100) picked up on the way, e.g. static from a thunderstorm. */
    public float extraNoise = 0;
    /** A thunderstorm crackles on the frequency. */
    public boolean stormy = false;
    /** How the signal travelled the last radio hop. */
    public RadioLink.Mode propagationMode = RadioLink.Mode.UNMODELLED;

    /** Marks the end of a transmission (the carrier dropped); carries no audio. */
    public boolean endOfTransmission = false;
    /** Sub-audible CTCSS tone (Hz) sent along with the audio, 0 when there is none. */
    public float ctcss = 0;
    /** How far (Hz) the receiver is tuned away from the transmitter, including its clarifier. */
    public int offsetHz = 0;
    /** Extra loss (dB) on the next radio hop, e.g. a receiver tuned to the neighbouring channel. */
    public float adjacentLossDb = 0;
    /** Came in over a repeater link (it is not linked on again). */
    public boolean linked = false;
    /** Data carried by the transmission (e.g. a decoded APRS packet); shared by all copies. */
    public Object payload;

    protected RadioMessage() {}

    public RadioMessage(UUID owner, WorldlyPosition location, float volume, RadioSource source) {
        this.owner = owner;
        this.origin = location;
        this.volume = volume;

        this.source = source;
    }

    public RadioMessage(UUID owner, WorldlyPosition location, byte[] data, float volume) {
        this(owner, location, volume, new RadioSource(data));
    }

    public RadioMessage(UUID owner, WorldlyPosition location, short[] data, float volume) {
        this(owner, location, volume, new RadioSource(data));
    }

    public RadioMessage(UUID owner, WorldlyPosition location, String sound, float volume) {
        this(owner, location, volume, new RadioSource(sound));
    }

    public RadioMessage(UUID owner, WorldlyPosition location, SoundEvent soundEvent, float volume) {
        this(owner, location, soundEvent.getLocation().toString(), volume);
    }

    /**
     * The end of a transmission: sent when a transmitter stops so receivers can close their squelch
     * (with the familiar short burst of noise on FM).
     * @param owner the router that transmitted (e.g. a handheld's reference)
     * @param speaker the player that spoke, or null
     */
    public static RadioMessage endOfTransmission(UUID owner, @org.jetbrains.annotations.Nullable UUID speaker, WorldlyPosition origin) {
        RadioMessage message = new RadioMessage(owner, origin, 0, new RadioSource(new short[0]));
        message.originalOwner = speaker;
        message.endOfTransmission = true;
        return message;
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public WorldlyPosition getOrigin() {
        return origin;
    }

    @Override
    public float getActivity() {
        return activity;
    }

    @Override
    public float getOffset() {
        return offset;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public long getSeed() {
        return seed;
    }

    @Override
    public float getPitch() {
        return pitch;
    }
    @Override
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    @Override
    public UUID getOwner() {
        return owner;
    }
    @Override
    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    @Override
    public float getPower() {
        return transmissionPower;
    }
    @Override
    public void setPower(float transmissionPower) {
        this.transmissionPower = transmissionPower;
    }

    @Override
    public ShortArrayList getTravelRecord() {
        return record;
    }

    @Override
    public Frequency getFrequencyMedium() {
        return frequencyMedium;
    }
    @Override
    public void setFrequencyMedium(Frequency frequencyMedium) {
        this.frequencyMedium = frequencyMedium;
    }

    @Override
    public Wire getWireMedium() {
        return wireMedium;
    }
    @Override
    public void setWireMedium(Wiring wireMedium) {
        this.wireMedium = (Wire) wireMedium;
    }

    @Override
    public UUID getRealOwner() {
        return originalOwner == null ? owner : originalOwner;
    }

    @Override
    public void delegate(UUID owner) {
        this.originalOwner = this.owner;
        this.owner = owner;
    }

    @Override
    public void addPower(float power) {
        this.transmissionPower = Math.min(this.transmissionPower + power, this.transmissionCap);
    }

    @Override
    public FrequencingType getFrequencingType() {
        FrequencingType type = FrequencingRegistry.getById(this.frequencingType);
        if (type == null) {
            CommonSimpleRadio.error("Missing frequencing type for id {}!", this.frequencingType);
        }
        return type;
    }

    @Override
    public RadioMessage copy() {
        RadioMessage copy = new RadioMessage();

        copy.owner = this.owner;
        copy.originalOwner = this.originalOwner;
        copy.origin = this.origin;
        copy.frequencingType = this.frequencingType;

        copy.volume = this.volume;
        copy.activity = this.activity;
        copy.pitch = this.pitch;
        copy.offset = this.offset;
        copy.seed = this.seed;

        copy.frequencyMedium = this.frequencyMedium;
        copy.wireMedium = this.wireMedium;

        copy.transmissionPower = this.transmissionPower;
        copy.transmissionCap = this.transmissionCap;
        copy.signalDb = this.signalDb;
        copy.extraNoise = this.extraNoise;
        copy.stormy = this.stormy;
        copy.propagationMode = this.propagationMode;
        copy.endOfTransmission = this.endOfTransmission;
        copy.ctcss = this.ctcss;
        copy.offsetHz = this.offsetHz;
        copy.adjacentLossDb = this.adjacentLossDb;
        copy.linked = this.linked;
        copy.payload = this.payload;

        // copy the record of the travel so far in case of splitting
        copy.record = this.record.clone();

        // don't copy the source since we don't know if it'll be any different from the origin at this point
        copy.source = this.source;

        return copy;
    }

    @Override
    public boolean willShort(Router router) {
        short identifier = router.getIdentifier();
        for (short recordIdentifier : record) {
            if (identifier == recordIdentifier) return true;
        }
        return false;
    }

    @Override
    public void visit(Router router) {
        record.add(router.getIdentifier());
    }

    @Override
    public void travel(Router from, Router to, Medium medium) {
        WorldlyPosition fromPos = from.getLocation();
        WorldlyPosition toPos = to.getLocation();

        double distance = fromPos.distance(toPos);
        double transmissionDiminishment = 0;
        FrequencingType.DiminishmentMethod diminishmentMethod = FrequencingType.DiminishmentMethod.ADDITIVE;

        if (medium instanceof Wire wire) {
            transmissionDiminishment = SimpleRadioFrequencing.WIRE.transmissionDiminishment;
            diminishmentMethod = SimpleRadioFrequencing.WIRE.diminishmentMethod;

            this.wireMedium = wire;
        } else if (medium instanceof Frequency frequency) {
            FrequencingType type = this.getFrequencingType();
            transmissionDiminishment = type.transmissionDiminishment;
            diminishmentMethod = type.diminishmentMethod;

            if (to instanceof RadioReceiver receiver) {
                // terrain, buildings, caves and the ionosphere make the receiver seem further away
                if (from instanceof RadioRouter fromRouter) {
                    RadioLink.Result link = RadioLink.compute(fromRouter, receiver, frequency);
                    distance = link.effectiveDistance() * RadioLink.factor(this.adjacentLossDb);
                    this.propagationMode = link.mode();
                }

                double receptionPower = receiver.getPower(fromPos);
                this.signalDb = (float) RadioLink.marginDb(this.transmissionPower + receptionPower, distance * transmissionDiminishment);
                double atmospheric = atmosphericNoise(frequency, toPos);
                this.stormy = atmospheric > 0;
                this.extraNoise = (float) (atmospheric + receiver.localNoise);

                if (distance > receiver.frequencingType.receptionFloor) {
                    distance = Math.max(receiver.frequencingType.receptionFloor, distance - receptionPower);
                }
            }

            if (fromPos.level.dimensionType() != toPos.level.dimensionType()) {
                if (SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional) {
                    double interference = SimpleRadioLibrary.SERVER_CONFIG.frequency.dimensionalInterference;
                    transmissionDiminishment += frequency.getModulation() == Frequency.Modulation.FREQUENCY ? interference : interference/2;
                } else {
                    this.transmissionPower = 0;
                    transmissionDiminishment = 0;
                }
            }

            this.frequencyMedium = frequency;
        }

        // nevermind... dont beware.... negative transmission...
        switch (diminishmentMethod) {
            case ADDITIVE -> this.transmissionPower = (float) Math.max(0f, this.transmissionPower - (distance * transmissionDiminishment));
            case MULTIPLICATIVE -> this.transmissionPower = (float) Math.max(0f, this.transmissionPower - (this.transmissionCap * transmissionDiminishment));
        }

        this.visit(to);
    }

    @Override
    public double computeSeverity() {
        double base = 0;
        double severity = 0;
        if (this.frequencyMedium != null) {
            double diminishThreshold = this.getFrequencingType().getDiminishThreshold(frequencyMedium.getModulation());
            // the noisy zone is at most the last 30% of a transmission's reach, so weak transmitters are clean up close
            if (this.transmissionCap > 0) diminishThreshold = Math.min(diminishThreshold, this.transmissionCap * 0.3);
            diminishThreshold = Math.max(1, diminishThreshold);

            base = frequencyMedium.getModulation() == Frequency.Modulation.FREQUENCY ?
                    SimpleRadioLibrary.SERVER_CONFIG.frequency.baseFMInterference :
                    SimpleRadioLibrary.SERVER_CONFIG.frequency.baseAMInterference;

            // atmospheric noise on the HF bands
            if (HamFrequency.bandOf(frequencyMedium.getFrequency()) == HamFrequency.Band.HF) {
                base += SimpleRadioLibrary.SERVER_CONFIG.station.hfNoise;
            }
            base += this.extraNoise;
            severity = 1 - Math.clamp(0f, 1f,  this.transmissionPower / diminishThreshold);
        }

        return Math.clamp(
                0, 100,
                base + severity * (100 - base)
        );
    }

    // thunderstorms crackle on HF
    private static double atmosphericNoise(Frequency frequency, WorldlyPosition at) {
        if (at == null || at.level == null || !at.level.isThundering()) return 0;
        if (HamFrequency.bandOf(frequency.getFrequency()) != HamFrequency.Band.HF) return 0;
        return SimpleRadioLibrary.SERVER_CONFIG.ionosphere.thunderNoise;
    }

    public boolean isValid() {
        return this.origin != null;
    }
}
