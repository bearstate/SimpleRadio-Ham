package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.CompatCore;
import com.codinglitch.simpleradio.SimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.networking.packets.ClientboundSpeakSoundPacket;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioAudioEffects;
import com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect;
import com.codinglitch.simpleradio.platform.Services;
import com.codinglitch.simpleradio.routers.Speaker;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A type of {@link RadioRouter} that accepts {@link RadioMessage}s and emits them in-world.
 * <br>
 * Often serves as the end of the audio pipeline.
 * <br>
 * <b>Does not route further.</b>
 */
public class RadioSpeaker extends RadioRouter implements Speaker {
    // migrated to locational audio channels only due to alternatives not having range property
    public LocationalAudioChannel audioChannel;
    public AudioPlayer audioPlayer;

    /**
     * The audio of one talker arriving over one path (owner: the router it came through, real owner: the player).
     * The filters of its effect keep state between frames, so every stream has its own.
     */
    static final class Stream {
        final Queue<short[]> frames = new ConcurrentLinkedQueue<>();
        final AudioEffect effect = SimpleRadioAudioEffects.RADIO.make();
        // AM carriers are never exactly on the same frequency: two of them whistle at their difference
        final double carrierOffsetHz = 200 + new Random().nextDouble() * 1400;
        volatile float signalDb = Float.NaN;
        volatile Frequency.Modulation modulation = null;
        volatile long lastAudio = 0;
    }

    private record StreamKey(UUID owner, UUID realOwner) {}

    private final Map<StreamKey, Stream> streams = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ChannelInterference interference = new ChannelInterference();

    // Silence bridges short gaps (network jitter, pauses) so the stream does not stop and restart
    private static final int GRACE_FRAMES = 15;
    // Older frames are dropped beyond this, so latency can not pile up
    private static final int MAX_QUEUED_FRAMES = 12;
    private static final int TAIL_FRAMES = 8;
    private static final long STREAM_TIMEOUT = 10_000;
    private int silentFrames = 0;

    public String category;
    public float range = 8;

    public int speakingTime = 0;

    protected RadioSpeaker(UUID id) {
        super(id);
    }
    protected RadioSpeaker() {
        this(UUID.randomUUID());
    }

    public RadioSpeaker(Entity owner) {
        this(owner, UUID.randomUUID());
    }
    public RadioSpeaker(Entity owner, UUID uuid) {
        this(uuid);
        this.owner = owner;

        SimpleRadioApi.registerRouterSided(this, owner.level().isClientSide(), null);
    }
    public RadioSpeaker(WorldlyPosition location) {
        this(location, UUID.randomUUID());
    }
    public RadioSpeaker(WorldlyPosition location, UUID uuid) {
        this(uuid);
        this.position = location;

        SimpleRadioApi.registerRouterSided(this, location.isClientSide(), null);
    }

    @Override
    public float getRange() {
        return range;
    }

    @Override
    public void setRange(float range) {
        this.range = range;
    }

    @Override
    public String getCategory() {
        return category;
    }

    @Override
    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public int getSpeakingTime() {
        return speakingTime;
    }

    public short[] retrieveFrame() {
        short[] audio = generatePacket();
        if (audio == null) {
            if (++silentFrames <= GRACE_FRAMES) return new short[960];
            silentFrames = 0;

            long now = System.currentTimeMillis();
            streams.values().removeIf(stream -> stream.frames.isEmpty() && now - stream.lastAudio > STREAM_TIMEOUT);

            if (audioPlayer != null)
                audioPlayer.stopPlaying();

            audioPlayer = null;
            return null;
        }
        silentFrames = 0;
        return audio;
    }

    public short[] generatePacket() {
        List<ChannelInterference.Input> inputs = new ArrayList<>();

        for (Stream stream : streams.values()) {
            short[] audio = stream.frames.poll();
            if (audio != null) inputs.add(new ChannelInterference.Input(audio, stream.signalDb, stream.modulation, stream.carrierOffsetHz));
        }

        if (inputs.isEmpty()) return null;
        return interference.mix(inputs);
    }

    /**
     * Frames waiting to be played (all talkers together).
     */
    public int queuedFrames() {
        int frames = 0;
        for (Stream stream : streams.values()) frames += stream.frames.size();
        return frames;
    }

    @Override
    public void updateLocation(WorldlyPosition location) {
        super.updateLocation(location);

        if (audioChannel != null) {
            audioChannel.updateLocation(CommonRadioPlugin.serverApi.createPosition(location.x, location.y, location.z));
        }
    }

    @Override
    public void tick(int tickCount) {
        super.tick(tickCount);

        if (speakingTime > 0) speakingTime--;
    }

    @Override
    public void take(Message message) {
        if (!this.active) return;
        if (acceptCriteria != null && !acceptCriteria.test(message)) return;
        super.take(message);
        speak(message);
    }

    public void speak(Message message) {
        RadioMessage radioMessage = (RadioMessage) message;
        Source source = message.getSource();

        // Severity calculation
        ServerLevel level = null;
        Vector3f position = null;
        if (this.position != null) {
            level = (ServerLevel) this.position.level;
            position = this.position.position();
        } else {
            level = (ServerLevel) owner.level();
            position = owner.position().toVector3f();
        }
        if (level == null || position == null) return;

        if (!SimpleRadioLibrary.SERVER_CONFIG.frequency.crossDimensional && level != message.getOrigin().level) return;

        StreamKey key = new StreamKey(message.getOwner(), message.getRealOwner());
        if (radioMessage.endOfTransmission) {
            squelchTail(key);
            return;
        }

        this.compileActivity(message);

        float severity = (float) message.computeSeverity();
        if (severity >= 100) return;

        // Parsing sound event
        if (source.getSound() != null) {
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceTo(new Vec3(position)) < 50) {
                    Services.NETWORKING.sendToPlayer(player, new ClientboundSpeakSoundPacket(
                            this.getReference(), source.getSound(),
                            message.getVolume(), message.getPitch(), severity, message.getOffset(), message.getSeed()
                    ));
                }
            }

            return;
        }

        Stream stream = streams.computeIfAbsent(key, k -> new Stream());
        Frequency medium = message.getFrequencyMedium();
        configure(stream.effect, radioMessage, medium, severity);

        // Decoding
        short[] decoded = source.getDecoded(message.getRealOwner()).clone();
        short[] filtered = stream.effect.apply(decoded);

        if (!CommonRadioPlugin.isAudioValid(filtered)) return;
        stream.frames.offer(filtered);
        while (stream.frames.size() > MAX_QUEUED_FRAMES) stream.frames.poll();

        stream.signalDb = radioMessage.signalDb;
        stream.modulation = medium == null ? null : medium.getModulation();
        stream.lastAudio = System.currentTimeMillis();

        // Loader-specific compat
        Services.COMPAT.onData(this, radioMessage, decoded);

        // Common compat
        CompatCore.onData(this, radioMessage, decoded);

        if (this.audioPlayer == null)
            getAudioPlayer().startPlaying();
    }

    private static void configure(AudioEffect effect, RadioMessage message, Frequency medium, float severity) {
        effect.severity = severity;
        effect.volume = message.getVolume();
        if (effect instanceof RadioAudioEffect radioEffect) {
            Frequency.Modulation modulation = medium == null ? null : medium.getModulation();
            radioEffect.amplitudeModulation = modulation == Frequency.Modulation.AMPLITUDE;
            radioEffect.singleSideband = modulation == Frequency.Modulation.SINGLE_SIDEBAND;
            radioEffect.highFrequency = medium != null && HamFrequency.bandOf(medium.getFrequency()) == HamFrequency.Band.HF;
            radioEffect.stormy = message.stormy;
            radioEffect.offsetHz = message.offsetHz;
        }
    }

    /**
     * When an FM carrier drops, the receiver hears a short burst of noise until its squelch closes.
     */
    private void squelchTail(StreamKey key) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.frequency.squelchTail) return;

        Stream stream = streams.get(key);
        if (stream == null || stream.modulation != Frequency.Modulation.FREQUENCY) return;
        if (System.currentTimeMillis() - stream.lastAudio > 1500) return;

        for (int frame = 0; frame < TAIL_FRAMES; frame++) {
            short[] noise = new short[960];
            for (int i = 0; i < noise.length; i++) {
                double fade = frame == TAIL_FRAMES - 1 ? 1 - (double) i / noise.length : 1;
                noise[i] = (short) Math.round((random.nextDouble() * 2 - 1) * 0.35 * fade * Short.MAX_VALUE);
            }
            stream.frames.offer(stream.effect.apply(noise));
        }

        if (this.audioPlayer == null)
            getAudioPlayer().startPlaying();
    }

    private AudioPlayer getAudioPlayer() {
        if (this.audioPlayer == null) {

            WorldlyPosition location = this.getLocation();
            this.audioChannel = CommonRadioPlugin.serverApi.createLocationalAudioChannel(this.reference,
                    CommonRadioPlugin.serverApi.fromServerLevel(location.level),
                    CommonRadioPlugin.serverApi.createPosition(location.x + 0.5, location.y + 0.5, location.z + 0.5)
            );
            audioChannel.setDistance(range);
            audioChannel.setCategory(category);

            this.audioPlayer = CommonRadioPlugin.serverApi.createAudioPlayer(audioChannel, CommonRadioPlugin.serverApi.createEncoder(), this::retrieveFrame);
        }
        return this.audioPlayer;
    }

    @Override
    public void invalidate() {
        if (this.audioPlayer != null)
            this.audioPlayer.stopPlaying();

        super.invalidate();
    }
}
