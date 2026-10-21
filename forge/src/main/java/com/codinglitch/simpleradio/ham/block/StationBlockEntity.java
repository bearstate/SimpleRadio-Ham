package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.CompatCore;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.SimpleRadioSounds;
import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.compat.Interference;
import com.codinglitch.simpleradio.ham.menu.StationMenu;
import com.codinglitch.simpleradio.ham.radio.*;
import com.codinglitch.simpleradio.radio.*;
import com.codinglitch.simpleradio.radio.propagation.Ionosphere;
import com.codinglitch.simpleradio.routers.Listener;
import com.codinglitch.simpleradio.routers.Speaker;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A fixed HF/VHF/UHF transceiver with adjustable output power (1-100 W).
 * <ul>
 *     <li>Received audio plays from the built-in speaker; an S-meter shows the signal strength.</li>
 *     <li>Players key it with the push-to-talk key, a desk or hand microphone, or by voice (VOX).</li>
 *     <li>A CW key next to it transmits a morse tone.</li>
 *     <li>A yagi antenna on an antenna mast above it makes it directional and much farther reaching.</li>
 *     <li>A mismatched antenna (high SWR) costs power and heats the power amplifier; an antenna tuner fixes the match.</li>
 *     <li>It needs electricity, and strong transmissions disturb nearby bare Electrodynamics wires.</li>
 * </ul>
 */
public class StationBlockEntity extends BlockEntity implements Powered, MenuProvider, CoaxLink.Endpoint {
    public static final int MAX_CLARIFIER = 990;
    private static final int VOX_HANG_TICKS = 20;

    // ---- Settings ---- \\
    public String frequency;
    public Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    public int shift = 0;
    public int watts = 25;
    public float toneTx = 0;
    public float toneRx = 0;
    public int clarifierHz = 0;
    public boolean vox = false;
    public UUID id;

    public final PowerBuffer power = new PowerBuffer(10_000);
    public final DeviceHealth health = new DeviceHealth();

    // ---- Runtime (server) ---- \\
    @Nullable private StationReceiver receiver;
    @Nullable private StationTransmitter transmitter;
    @Nullable private Speaker speaker;
    @Nullable private Listener listener;
    @Nullable private WorldlyPosition location;
    private boolean routersBuilt = false;
    private boolean settingsDirty = true;
    private boolean antennaDirty = true;

    @Nullable private UUID keyedBy;
    @Nullable private UUID voxSpeaker;
    @Nullable private UUID lastSpeaker;
    private long voxUntil = -1;
    private long now = 0;
    private final CwGenerator cw = new CwGenerator();
    private boolean transmitting = false;
    private boolean wasTransmitting = false;
    private double temperature = 0;
    private boolean overheated = false;
    private boolean tuner = false;
    private long deniedUntil = -1;

    // data transmissions (SSTV pictures): audio played out in real time with data for every frame
    private final PacedAudio dataAudio = new PacedAudio();
    @Nullable private java.util.function.IntFunction<Object> dataPayload;
    private int dataFrames = 0;
    private int dataSent = 0;
    private final java.util.Set<java.util.function.Consumer<RadioMessage>> dataListeners = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ---- Antenna ---- \\
    public int mastHeight = 0;
    public int antennaScore = 0;
    @Nullable public BlockPos yagiPos;
    public int yagiRotation = -1;
    @Nullable public BlockPos verticalPos;
    public final CoaxLink coax = new CoaxLink();

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case StationMenu.DATA_ENERGY -> (int) power.getStored();
                case StationMenu.DATA_CAPACITY -> (int) power.getCapacity();
                case StationMenu.DATA_FLAGS -> flags();
                case StationMenu.DATA_YAGI -> yagiPos == null ? -1 : yagiRotation;
                case StationMenu.DATA_MAST -> mastHeight;
                case StationMenu.DATA_RANGE -> (int) Math.min(Short.MAX_VALUE, forwardRange() / 10);
                case StationMenu.DATA_ANTENNA -> verticalPos != null ? StationMenu.ANTENNA_VERTICAL
                        : yagiPos != null ? (coax.isConnected() ? StationMenu.ANTENNA_YAGI_COAX : StationMenu.ANTENNA_YAGI_MAST)
                        : StationMenu.ANTENNA_MAST;
                case StationMenu.DATA_COAX -> (int) Math.round(coax.getLength() * 10);
                case StationMenu.DATA_COAX_LOSS -> (int) Math.round(coax.lossDb(Math.max(0, HamFrequency.parseKHz(frequency))) * 100);
                case StationMenu.DATA_SIGNAL -> receiver == null ? Short.MIN_VALUE : RepeaterBlockEntity.signalData(receiver.currentSignalDb(10));
                case StationMenu.DATA_SWR -> (int) Math.round(Math.min(99, radioSwr(transmitKHz())) * 10);
                case StationMenu.DATA_TEMPERATURE -> (int) Math.round(temperature * 100);
                case StationMenu.DATA_SOLAR_FLUX -> level instanceof ServerLevel serverLevel ? (int) Math.round(Ionosphere.conditions(serverLevel).solarFlux()) : 0;
                case StationMenu.DATA_K_INDEX -> level instanceof ServerLevel serverLevel ? Ionosphere.conditions(serverLevel).kIndex() : 0;
                case StationMenu.DATA_BANDS -> bandConditions();
                case StationMenu.DATA_NOISE -> receiver == null ? 0 : Math.round(receiver.localNoise);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return StationMenu.DATA_COUNT;
        }
    };

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.STATION_BE.get(), pos, state);

        String defaultFrequency = HamFrequency.normalize(FrequenciesImpl.DEFAULT_FREQUENCY);
        this.frequency = defaultFrequency != null ? defaultFrequency : HamFrequency.format(145_500);
    }

    @Override
    public PowerBuffer getPowerBuffer() {
        return power;
    }

    @Override
    public boolean needsDc() {
        return true;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (this.id == null && !level.isClientSide) this.id = UUID.randomUUID();
    }

    private int flags() {
        int flags = 0;
        if (power.isPowered() && !health.isBurned()) flags |= StationMenu.FLAG_POWERED;
        if (transmitting) flags |= StationMenu.FLAG_TRANSMITTING;
        if (keyedBy != null) flags |= StationMenu.FLAG_KEYED;
        if (cw.isActive()) flags |= StationMenu.FLAG_CW;
        if (receiver != null && receiver.getActivityTime() > 0) flags |= StationMenu.FLAG_RECEIVING;
        if (health.isBurned()) flags |= StationMenu.FLAG_BURNED;
        if (tuner) flags |= StationMenu.FLAG_TUNER;
        if (overheated) flags |= StationMenu.FLAG_OVERHEATED;
        if (vox) flags |= StationMenu.FLAG_VOX;
        if (now < deniedUntil) flags |= StationMenu.FLAG_DENIED;
        if (power.hasWrongVoltage(now)) flags |= StationMenu.FLAG_WRONG_VOLTAGE;
        return flags;
    }

    // 2 bits per displayed HF band: 0 closed, 1 poor, 2 fair, 3 good
    private int bandConditions() {
        if (!(level instanceof ServerLevel serverLevel)) return 0;
        int bits = 0;
        for (int i = 0; i < Ionosphere.DISPLAY_BANDS.length; i++) {
            bits |= Ionosphere.bandState(serverLevel, Ionosphere.DISPLAY_BANDS[i]).ordinal() << (i * 2);
        }
        return bits;
    }

    // ---- Settings ---- \\

    @Nullable
    public String getTransmitFrequency() {
        return shift == 0 ? frequency : HamFrequency.shift(frequency, shift);
    }

    private long transmitKHz() {
        String transmitFrequency = getTransmitFrequency();
        return Math.max(0, HamFrequency.parseKHz(transmitFrequency != null ? transmitFrequency : frequency));
    }

    public void applySettings(String frequency, Frequency.Modulation modulation, int shift, int watts) {
        applySettings(frequency, modulation, shift, watts, toneTx, toneRx, clarifierHz, vox);
    }

    public void applySettings(String frequency, Frequency.Modulation modulation, int shift, int watts,
                              float toneTx, float toneRx, int clarifierHz, boolean vox) {
        boolean retune = !frequency.equals(this.frequency) || shift != this.shift;

        this.frequency = frequency;
        this.modulation = modulation;
        this.shift = shift;
        this.watts = clampWatts(watts);
        this.toneTx = Ctcss.normalize(toneTx);
        this.toneRx = Ctcss.normalize(toneRx);
        this.clarifierHz = Math.max(-MAX_CLARIFIER, Math.min(MAX_CLARIFIER, clarifierHz / 10 * 10));
        this.vox = vox;
        this.settingsDirty = true;

        // the tuner's relays click while it finds the match for the new frequency
        if (retune && tuner && level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4f, 1.8f);
        }

        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static int clampWatts(int watts) {
        return Math.max(SimpleRadioLibrary.SERVER_CONFIG.station.minWatts, Math.min(SimpleRadioLibrary.SERVER_CONFIG.station.maxWatts, watts));
    }

    // ---- Coax ---- \\

    @Override
    public CoaxLink getCoax() {
        return coax;
    }

    @Override
    public boolean acceptsAntenna(BlockState antenna) {
        return antenna.getBlock() instanceof DualBandAntennaBlock || antenna.getBlock() instanceof YagiAntennaBlock;
    }

    @Override
    public void onCoaxChanged() {
        antennaDirty = true;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos antenna = coax.getAntenna();
        if (antenna == null) return super.getRenderBoundingBox();
        return new AABB(worldPosition).minmax(new AABB(antenna)).inflate(1);
    }

    // ---- Keying ---- \\

    public boolean isKeyedBy(UUID player) {
        return player.equals(keyedBy);
    }

    public boolean isBusyFor(UUID player) {
        return keyedBy != null && !keyedBy.equals(player);
    }

    /**
     * @return null when the player may transmit now, otherwise the translation key of the reason
     */
    @Nullable
    public String transmitDenial(@Nullable Player player) {
        if (health.isBurned()) return "message.simpleradio.burned_short";
        if (!power.isPowered()) return "message.simpleradio.station_no_power";
        if (overheated) return "message.simpleradio.overheated";
        String transmitFrequency = getTransmitFrequency();
        if (transmitFrequency == null) return "message.simpleradio.invalid_frequency";
        return BandPlan.denial(player, transmitFrequency, BandPlan.Equipment.AMATEUR);
    }

    public boolean key(ServerPlayer player) {
        if (level == null) return false;
        if (keyedBy != null && !keyedBy.equals(player.getUUID())) return false; // someone else is talking

        String denial = transmitDenial(player);
        if (denial != null) {
            deniedUntil = now + 60;
            player.displayClientMessage(Component.translatable(denial).withStyle(ChatFormatting.RED), true);
            return false;
        }

        if (keyedBy == null) {
            level.playSound(null, worldPosition, SimpleRadioSounds.RADIO_OPEN, SoundSource.BLOCKS, 0.6f, 1.1f);
        }
        keyedBy = player.getUUID();
        lastSpeaker = keyedBy;
        if (transmitter != null) transmitter.setActive(true);
        return true;
    }

    public void unkey(@Nullable UUID player) {
        if (keyedBy == null) return;
        if (player != null && !player.equals(keyedBy)) return;

        keyedBy = null;
        if (level != null) level.playSound(null, worldPosition, SimpleRadioSounds.RADIO_CLOSE, SoundSource.BLOCKS, 0.6f, 1.1f);
    }

    public void setCwKey(boolean down, @Nullable Player operator) {
        if (down) {
            String denial = transmitDenial(operator);
            if (denial != null) {
                deniedUntil = now + 60;
                if (operator != null) operator.displayClientMessage(Component.translatable(denial).withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        cw.setKey(down);
    }

    private boolean isPlayerInRange(ServerPlayer player) {
        double range = SimpleRadioLibrary.SERVER_CONFIG.station.pttRange + SimpleRadioLibrary.SERVER_CONFIG.station.microphoneReach + 1;
        return player.level() == level && player.distanceToSqr(worldPosition.getCenter()) <= range * range;
    }

    // VOX: talking near the station keys it (voice from the station's own speaker never reaches its microphone)
    private boolean acceptVoice(com.codinglitch.simpleradio.radio.Message message) {
        UUID speakerId = message.getRealOwner();
        if (keyedBy != null) return keyedBy.equals(speakerId);
        if (!vox || speakerId == null || level == null || cw.isActive()) return false;
        if (voxSpeaker != null && now < voxUntil && !voxSpeaker.equals(speakerId)) return false;

        Player player = level.getPlayerByUUID(speakerId);
        if (player == null || transmitDenial(player) != null) return false;

        voxSpeaker = speakerId;
        lastSpeaker = speakerId;
        voxUntil = now + VOX_HANG_TICKS;
        if (transmitter != null && !transmitter.isActive()) transmitter.setActive(true);
        return true;
    }

    // ---- Data ---- \\

    /**
     * Starts sending pre-rendered audio (e.g. an SSTV picture) with data attached to every frame.
     * @return null when started, otherwise the translation key of the reason it cannot send
     */
    @Nullable
    public String transmitData(List<short[]> frames, java.util.function.IntFunction<Object> payload, @Nullable Player operator) {
        String denial = transmitDenial(operator);
        if (denial != null) return denial;
        if (keyedBy != null || cw.isActive() || dataAudio.isPlaying()) return "message.simpleradio.station_busy";

        dataPayload = payload;
        dataFrames = frames.size();
        dataSent = 0;
        if (operator != null) lastSpeaker = operator.getUUID();
        dataAudio.enqueue(frames);
        return null;
    }

    public boolean isSendingData() {
        return dataAudio.isPlaying();
    }

    public float dataProgress() {
        return dataFrames == 0 ? 0 : (float) dataSent / dataFrames;
    }

    public void cancelData() {
        dataAudio.clear();
    }

    /**
     * Receives every message with data (e.g. SSTV picture lines) this station hears.
     */
    public void addDataListener(java.util.function.Consumer<RadioMessage> listener) {
        dataListeners.add(listener);
    }

    public void removeDataListener(java.util.function.Consumer<RadioMessage> listener) {
        dataListeners.remove(listener);
    }

    // ---- Antenna ---- \\

    private void scanAntenna(Level level) {
        verticalPos = null;
        tuner = hasTuner(level);

        // an antenna connected with coaxial cable takes precedence over a mast on top
        if (coax.isConnected() && coax.getAntenna() != null) {
            BlockPos antenna = coax.getAntenna();
            if (!level.isLoaded(antenna)) return; // keep the last known antenna while its chunk is unloaded

            BlockState state = level.getBlockState(antenna);
            if (state.getBlock() instanceof YagiAntennaBlock) {
                yagiPos = antenna;
                yagiRotation = state.getValue(BlockStateProperties.ROTATION_16);
                mastHeight = 0;
                antennaScore = 0;
                return;
            }
            if (state.getBlock() instanceof DualBandAntennaBlock) {
                verticalPos = antenna;
                yagiPos = null;
                yagiRotation = -1;
                mastHeight = 0;
                antennaScore = 0;
                return;
            }

            // the antenna is gone
            coax.disconnect(level, worldPosition);
            onCoaxChanged();
        }

        int maxHeight = SimpleRadioLibrary.SERVER_CONFIG.yagi.maxMastHeight;

        BlockPos.MutableBlockPos cursor = worldPosition.above().mutable();
        BlockPos foundYagi = null;
        int mast = 0;
        for (int i = 0; i <= maxHeight; i++) {
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof YagiAntennaBlock) {
                foundYagi = cursor.immutable();
                yagiRotation = state.getValue(BlockStateProperties.ROTATION_16);
                break;
            }
            if (!(state.getBlock() instanceof AntennaBlock)) break;

            mast++;
            cursor.move(Direction.UP);
        }

        yagiPos = foundYagi;
        mastHeight = mast;
        if (yagiPos == null) yagiRotation = -1;

        BlockState above = level.getBlockState(worldPosition.above());
        antennaScore = yagiPos == null && above.getBlock() instanceof Antennal antennal ? antennal.climbAntenna(worldPosition.above(), level) : 0;
    }

    private boolean hasTuner(Level level) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(worldPosition.relative(direction)).getBlock() instanceof AntennaTunerBlock) return true;
        }
        return false;
    }

    public void markAntennaDirty() {
        this.antennaDirty = true;
    }

    public double yagiAzimuth() {
        return yagiRotation * 22.5;
    }

    private double antennaTopY() {
        if (yagiPos != null) return yagiPos.getY() + 0.5;
        if (verticalPos != null) return verticalPos.getY() + DualBandAntennaBlock.HEIGHT;
        return worldPosition.getY() + 0.5 + mastHeight;
    }

    private boolean usesMast() {
        return yagiPos == null && verticalPos == null;
    }

    public BlockPos radiatingAntenna() {
        if (yagiPos != null) return yagiPos;
        if (verticalPos != null) return verticalPos;
        return worldPosition.above(mastHeight);
    }

    /**
     * Where the signal is radiated from: the yagi, the cable connected vertical or the top of the mast.
     */
    public org.joml.Vector3f antennaTop() {
        BlockPos antenna = radiatingAntenna();
        double top = verticalPos != null ? verticalPos.getY() + DualBandAntennaBlock.HEIGHT : antenna.getY() + 0.5;
        return new org.joml.Vector3f(antenna.getX() + 0.5f, (float) top, antenna.getZ() + 0.5f);
    }

    public Swr.Antenna antennaKind() {
        if (yagiPos != null) return Swr.Antenna.YAGI;
        if (verticalPos != null) return Swr.Antenna.VERTICAL;
        return mastHeight > 0 ? Swr.Antenna.MAST : Swr.Antenna.NONE;
    }

    public double antennaSwr(long kHz) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.swr.enabled) return 1;
        return Swr.of(antennaKind(), kHz, mastHeight);
    }

    /**
     * SWR the radio sees (after the tuner).
     */
    public double radioSwr(long kHz) {
        return Swr.atRadio(antennaSwr(kHz), tuner);
    }

    /**
     * Share of the set power that leaves the antenna (fold-back and mismatch loss).
     */
    public double outputShare(long kHz) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.swr.enabled) return 1;
        double swr = radioSwr(kHz);
        return Swr.foldback(swr) * (1 - Swr.reflected(swr));
    }

    private double gainTowards(@Nullable WorldlyPosition target, String onFrequency) {
        long kHz = Math.max(0, HamFrequency.parseKHz(onFrequency));
        boolean hf = HamFrequency.bandOf(kHz) == HamFrequency.Band.HF;

        double gain;
        if (yagiPos != null) {
            gain = hf && SimpleRadioLibrary.SERVER_CONFIG.swr.enabled ? Math.pow(10, SimpleRadioLibrary.SERVER_CONFIG.swr.yagiHfGainDbi / 10.0)
                    : target == null ? Propagation.yagiForwardGain()
                    : Propagation.yagiGain(Propagation.angleBetween(Propagation.azimuth(yagiPos, target), yagiAzimuth()));
        } else if (verticalPos != null) {
            gain = DualBandAntennaBlock.gain(kHz);
        } else if (mastHeight == 0 && SimpleRadioLibrary.SERVER_CONFIG.swr.enabled) {
            gain = Math.pow(10, -SimpleRadioLibrary.SERVER_CONFIG.swr.noAntennaLossDb / 10.0); // no antenna at all
        } else {
            gain = 1;
        }
        return gain * coax.lossFactor(kHz);
    }

    /**
     * Transmission power (range in blocks) towards a receiver; forward direction when target is null.
     */
    public double transmitPowerTowards(@Nullable WorldlyPosition target) {
        String transmitFrequency = getTransmitFrequency();
        String onFrequency = transmitFrequency != null ? transmitFrequency : frequency;
        long kHz = Math.max(0, HamFrequency.parseKHz(onFrequency));

        double effectiveWatts = watts * outputShare(kHz);
        double range = Propagation.stationRange(effectiveWatts, modulation, onFrequency, gainTowards(target, onFrequency));
        range += Propagation.heightBonus(antennaTopY());
        if (usesMast()) range += antennaScore * SimpleRadioFrequencing.STATION.antennaAptitude;
        return range;
    }

    /**
     * Reception power from a transmitter; forward direction when source is null.
     */
    public double receivePowerFrom(@Nullable WorldlyPosition source) {
        double reception = SimpleRadioFrequencing.STATION.receptionPower * Math.sqrt(gainTowards(source, frequency));
        reception += Propagation.heightBonus(antennaTopY());
        if (usesMast()) reception += antennaScore * SimpleRadioFrequencing.STATION.antennaAptitude;
        return reception;
    }

    public double forwardRange() {
        return transmitPowerTowards(null);
    }

    public void rotateYagi(int steps) {
        if (level == null || yagiPos == null) return;
        BlockState state = level.getBlockState(yagiPos);
        if (!(state.getBlock() instanceof YagiAntennaBlock)) return;

        YagiAntennaBlock.rotate(level, yagiPos, state, steps);
        scanAntenna(level);
    }

    // ---- Routers ---- \\

    private void buildRouters(ServerLevel level) {
        if (id == null) id = UUID.randomUUID();

        location = CompatCore.modifyPosition(WorldlyPosition.of(worldPosition, level, worldPosition));
        RadioManager manager = RadioManager.getInstance();

        // Receiver + speaker
        Frequency receiveFrequency = manager.frequencies().getOrCreate(frequency, modulation);
        StationReceiver newReceiver = new StationReceiver(receiveFrequency, location, id, this::receivePowerFrom);
        newReceiver.frequencingType(SimpleRadioFrequencing.STATION);
        newReceiver.setLink(StationBlock.class);
        newReceiver.antennaSupplier = this::antennaTop;
        newReceiver.toneSquelch = toneRx;
        newReceiver.clarifierHz = clarifierHz;
        newReceiver.setAcceptingCriteria(message -> {
            // data (e.g. SSTV picture lines) is decoded from carriers strong enough to be heard
            if (message instanceof RadioMessage radioMessage && radioMessage.payload != null && message.getPower() > 0
                    && (toneRx <= 0 || modulation != Frequency.Modulation.FREQUENCY || Ctcss.matches(radioMessage.ctcss, toneRx))) {
                dataListeners.forEach(listener -> listener.accept(radioMessage));
            }
            return true;
        });
        receiveFrequency.addReceiver(newReceiver);
        receiver = newReceiver;

        speaker = manager.speakers().getOrCreate(location, id);
        speaker.setRange(SimpleRadioLibrary.SERVER_CONFIG.station.speakingRange);
        speaker.setCategory(CommonRadioPlugin.RADIOS_CATEGORY);
        speaker.setLink(StationBlock.class);
        receiver.addRouter(speaker);

        // Microphone + transmitter
        listener = manager.listeners().getOrCreate(location, id);
        listener.setRange(SimpleRadioLibrary.SERVER_CONFIG.station.listeningRange);
        listener.setLink(StationBlock.class);
        listener.setActive(false);
        // only the player holding push-to-talk (or talking with VOX on) is transmitted
        listener.setRoutingCriteria((message, router) -> acceptVoice(message));

        String transmitFrequencyName = getTransmitFrequency();
        if (transmitFrequencyName != null) {
            Frequency transmitFrequency = manager.frequencies().getOrCreate(transmitFrequencyName, modulation);
            StationTransmitter newTransmitter = new StationTransmitter(transmitFrequency, location, id, this::transmitPowerTowards);
            newTransmitter.frequencingType(SimpleRadioFrequencing.STATION);
            newTransmitter.setLink(StationBlock.class);
            newTransmitter.antennaSupplier = this::antennaTop;
            newTransmitter.ctcssTone = toneTx;
            newTransmitter.setActive(false);
            transmitFrequency.addTransmitter(newTransmitter);
            transmitter = newTransmitter;

            listener.addRouter(transmitter);
        }

        routersBuilt = true;
        settingsDirty = false;
    }

    private void teardownRouters() {
        RadioManager manager = RadioManager.getInstance();

        if (transmitter != null) {
            if (transmitter.frequency != null) transmitter.frequency.removeTransmitter(transmitter);
            manager.removeRouter(transmitter);
        }
        if (receiver != null) {
            if (receiver.frequency != null) receiver.frequency.removeReceiver(receiver);
            manager.removeRouter(receiver);
        }
        if (id != null) {
            manager.removeRouter(manager.speakers().remove(id));
            manager.removeRouter(manager.listeners().remove(id));
        }

        transmitter = null;
        receiver = null;
        speaker = null;
        listener = null;
        routersBuilt = false;
        wasTransmitting = false;
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            teardownRouters();
            cw.reset();
        }
        super.setRemoved();
    }

    // ---- Ticking ---- \\

    public static void serverTick(Level level, BlockPos pos, BlockState state, StationBlockEntity station) {
        station.tickServer((ServerLevel) level, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        now = level.getGameTime();

        // release push-to-talk when the player left
        if (keyedBy != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(keyedBy);
            if (player == null || !isPlayerInRange(player)) unkey(null);
        }

        boolean wantsToTransmit = keyedBy != null || cw.isActive() || now < voxUntil || dataAudio.isPlaying();
        double usage = SimpleRadioLibrary.SERVER_CONFIG.power.stationIdleUsage;
        if (wantsToTransmit) usage += watts / Math.max(0.05, SimpleRadioLibrary.SERVER_CONFIG.power.stationEfficiency) / 20.0;
        boolean powered = !health.isBurned() && power.consume(usage);

        if (now % 20 == 0 || antennaDirty) {
            scanAntenna(level);
            antennaDirty = false;
        }

        if (now % 20 == 0 && health.tick(level, worldPosition, radiatingAntenna(), Lightning.heightAboveSupport(level, radiatingAntenna()))) {
            onHealthChanged();
        }

        if (!powered) {
            if (routersBuilt) teardownRouters();
            if (keyedBy != null) unkey(null);
            cw.reset();
            voxUntil = -1;
            dataAudio.clear();
            transmitting = false;
            coolDown();
            updateState(level, state, false, false);
            if (now % 20 == 0) setChanged();
            return;
        }

        if (!routersBuilt || settingsDirty) {
            teardownRouters();
            buildRouters(level);
        }

        if (now % 40 == 0 && receiver != null) receiver.localNoise = Qrm.noiseAt(level, worldPosition);

        List<short[]> frames = cw.generate(SimpleRadioLibrary.SERVER_CONFIG.cw_key.toneFrequency, SimpleRadioLibrary.SERVER_CONFIG.cw_key.volume);

        wantsToTransmit = (keyedBy != null || cw.isActive() || now < voxUntil || dataAudio.isPlaying()) && !overheated;
        transmitting = wantsToTransmit && transmitter != null;

        // the carrier drops: receivers close their squelch
        if (wasTransmitting && !transmitting && transmitter != null && id != null) {
            transmitter.endTransmission(id, lastSpeaker);
        }
        wasTransmitting = transmitting;

        // a desk or hand microphone may be a few blocks away: the built-in microphone hears its user
        if (listener != null) {
            listener.setActive(keyedBy != null || vox);
            listener.setRange((float) listeningRange(level));
        }
        if (transmitter != null) transmitter.setActive(wantsToTransmit);
        if (receiver != null) receiver.setActive(!wantsToTransmit); // half duplex

        for (short[] frame : frames) sendCwFrame(frame);
        sendDataFrames();

        heat(transmitting);

        if (transmitting && now % 10 == 0) {
            BlockPos antenna = radiatingAntenna();
            Interference.emit(level, antenna, (int) Math.round(watts * outputShare(transmitKHz())), yagiPos != null ? yagiAzimuth() : Double.NaN);
        }

        updateState(level, state, true, transmitting);
        if (now % 20 == 0) setChanged();
    }

    private double listeningRange(ServerLevel level) {
        double range = SimpleRadioLibrary.SERVER_CONFIG.station.listeningRange;
        if (keyedBy != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(keyedBy);
            if (player != null) range = Math.max(range, Math.sqrt(player.distanceToSqr(worldPosition.getCenter())) + 1.5);
        }
        return range;
    }

    // the power amplifier warms up while transmitting, much faster into a mismatched antenna
    private void heat(boolean transmitting) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.swr;
        double cooling = 1.946 / Math.max(1, config.heatSeconds * 20);
        if (transmitting) {
            double swr = radioSwr(transmitKHz());
            double output = watts * outputShare(transmitKHz()) / 100.0;
            temperature += 0.7 * cooling * output * (1 + 6 * Swr.reflected(swr)) - cooling * temperature;
        } else {
            coolDown();
        }

        if (!config.overheating) {
            overheated = false;
            return;
        }
        if (!overheated && temperature >= 1) {
            overheated = true;
            if (level != null) level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);
        } else if (overheated && temperature < 0.6) {
            overheated = false;
        }
    }

    private void coolDown() {
        double cooling = 1.946 / Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.swr.heatSeconds * 20);
        temperature = Math.max(0, temperature - cooling * temperature - 0.00002);
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public boolean isOverheated() {
        return overheated;
    }

    public boolean hasTuner() {
        return tuner;
    }

    @Nullable
    public StationReceiver getReceiver() {
        return receiver;
    }

    private void sendDataFrames() {
        if (!dataAudio.isPlaying() && dataSent == 0) return;
        for (short[] frame : dataAudio.poll()) {
            if (location == null || id == null || transmitter == null) continue;
            RadioMessage message = new RadioMessage(id, location, frame, 1f);
            message.activity = CommonRadioPlugin.analyzeActivity(frame);
            if (dataPayload != null) message.payload = dataPayload.apply(dataSent);
            dataSent++;
            if (transmitter.isActive() && !overheated) transmitter.accept(message);
        }
        if (!dataAudio.isPlaying()) {
            dataSent = 0;
            dataFrames = 0;
            dataPayload = null;
        }
    }

    private void sendCwFrame(short[] frame) {
        if (location == null || id == null) return;

        RadioMessage message = new RadioMessage(id, location, frame, 1f);
        message.activity = CommonRadioPlugin.analyzeActivity(frame);

        // side tone, so the operator hears the keying
        if (speaker != null) speaker.accept(message.copy());
        if (transmitter != null && transmitter.isActive() && !overheated) {
            lastSpeaker = null;
            transmitter.accept(message);
        }
    }

    private void updateState(Level level, BlockState state, boolean on, boolean transmitting) {
        if (state.getValue(StationBlock.ON) == on && state.getValue(StationBlock.TRANSMITTING) == transmitting) return;
        level.setBlock(worldPosition, state.setValue(StationBlock.ON, on).setValue(StationBlock.TRANSMITTING, transmitting), Block.UPDATE_CLIENTS);
    }

    public void onHealthChanged() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Saving ---- \\

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        loadSettings(tag);
        coax.load(tag);
        if (tag.hasUUID("reference")) this.id = tag.getUUID("reference");
        power.load(tag);
        health.load(tag);
        temperature = tag.getDouble("temperature");
        antennaDirty = true;

        this.settingsDirty = true;
    }

    public void loadSettings(CompoundTag tag) {
        if (tag.contains("frequency")) {
            String loaded = HamFrequency.normalize(tag.getString("frequency"));
            if (loaded != null) this.frequency = loaded;
        }
        if (tag.contains("modulation")) {
            Frequency.Modulation loaded = Frequency.modulationOf(tag.getString("modulation"));
            if (loaded != null) this.modulation = loaded;
        }
        this.shift = tag.getInt("shift");
        if (tag.contains("watts")) this.watts = tag.getInt("watts");
        this.toneTx = Ctcss.normalize(tag.getFloat("tone_tx"));
        this.toneRx = Ctcss.normalize(tag.getFloat("tone_rx"));
        this.clarifierHz = Math.max(-MAX_CLARIFIER, Math.min(MAX_CLARIFIER, tag.getInt("clarifier")));
        this.vox = tag.getBoolean("vox");
        this.settingsDirty = true;
    }

    public void saveSettings(CompoundTag tag) {
        tag.putString("frequency", frequency);
        tag.putString("modulation", modulation.shorthand);
        tag.putInt("shift", shift);
        tag.putInt("watts", watts);
        tag.putFloat("tone_tx", toneTx);
        tag.putFloat("tone_rx", toneRx);
        tag.putInt("clarifier", clarifierHz);
        tag.putBoolean("vox", vox);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveSettings(tag);
        coax.save(tag);
        health.save(tag);
        if (id != null) tag.putUUID("reference", id);
        power.save(tag);
        tag.putDouble("temperature", temperature);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveSettings(tag);
        coax.save(tag);
        health.save(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Menu ---- \\

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.simpleradio.station");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StationMenu(containerId, inventory, worldPosition, dataAccess);
    }
}
