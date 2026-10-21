package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.CompatCore;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Antennal;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.blocks.AntennaBlock;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.menu.RepeaterMenu;
import com.codinglitch.simpleradio.ham.radio.*;
import com.codinglitch.simpleradio.ham.HamChunkLoading;
import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.codinglitch.simpleradio.radio.Ctcss;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A repeater listens on its input frequency (output + shift) and immediately
 * retransmits everything it hears on its output frequency.
 * <p>
 * Example (UHF, shift -7.600 MHz): output 438.650 MHz, input 431.050 MHz.
 * Handhelds are set to receive 438.650 MHz with a -7.600 MHz shift.
 * <ul>
 *     <li>An access tone (CTCSS) makes it open only for users sending that tone; it can send its own tone too.</li>
 *     <li>The time-out timer cuts off transmissions that go on too long.</li>
 *     <li>With a call sign it identifies itself in morse every few minutes of activity.</li>
 *     <li>Repeaters with the same link number relay each other's traffic, however far apart.</li>
 * </ul>
 */
public class RepeaterBlockEntity extends BlockEntity implements Powered, MenuProvider, CoaxLink.Endpoint, RepeaterLinks.Node {
    private static final int RELAY_HOLD_TICKS = 10;
    private static final int NEW_TRANSMISSION_GAP = 20;
    public static final int MAX_CALLSIGN = 12;
    public static final int MAX_LINK = 9999;

    // ---- Settings ---- \\
    public String frequency;
    public Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    public int shift;
    public boolean enabled = true;
    public float accessTone = 0;
    public float outputTone = 0;
    public String callsign = "";
    public int linkId = 0;
    public UUID id;

    public final PowerBuffer power = new PowerBuffer(5_000);
    public final DeviceHealth health = new DeviceHealth();

    // ---- Runtime (server) ---- \\
    @Nullable private StationReceiver receiver;
    @Nullable private RepeaterTransmitter transmitter;
    private boolean routersBuilt = false;
    private boolean settingsDirty = true;
    private long lastRelay = -1000;
    private long lastTail = -1000;
    private long lastInput = -1000;
    private long relayStart = -1000;
    private long lastId = Long.MIN_VALUE / 2;
    private boolean relayedSinceTail = false;
    private boolean activitySinceId = false;
    private boolean timedOut = false;
    private boolean endPending = false;
    private int joinedLink = 0;
    private final PacedAudio audio = new PacedAudio();
    private boolean chunkLoaded = false;
    @Nullable private WorldlyPosition location;
    private long now = 0;
    private int antennaScore = 0;
    private int mastHeight = 0;
    @Nullable private BlockPos verticalPos;
    public final CoaxLink coax = new CoaxLink();

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case RepeaterMenu.DATA_ENERGY -> (int) power.getStored();
                case RepeaterMenu.DATA_CAPACITY -> (int) power.getCapacity();
                case RepeaterMenu.DATA_FLAGS -> (power.isPowered() ? 1 : 0) | (enabled ? 2 : 0) | (isRelaying() ? 4 : 0)
                        | (health.isBurned() ? RepeaterMenu.FLAG_BURNED : 0) | (timedOut ? RepeaterMenu.FLAG_TIMED_OUT : 0);
                case RepeaterMenu.DATA_RANGE -> (int) Math.min(Short.MAX_VALUE, outputRange() / 10);
                case RepeaterMenu.DATA_LINKED -> linkId > 0 ? Math.max(0, RepeaterLinks.members(linkId) - 1) : 0;
                case RepeaterMenu.DATA_SIGNAL -> receiver == null ? Short.MIN_VALUE : signalData(receiver.currentSignalDb(10));
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return RepeaterMenu.DATA_COUNT;
        }
    };

    /**
     * Signal strength in tenths of a dB for menus (they sync shorts); Short.MIN_VALUE when nothing is heard.
     */
    public static int signalData(float signalDb) {
        if (Float.isNaN(signalDb)) return Short.MIN_VALUE;
        return (int) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, Math.round(signalDb * 10)));
    }

    public RepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.REPEATER_BE.get(), pos, state);

        String defaultFrequency = HamFrequency.format(438_650);
        this.frequency = defaultFrequency != null ? defaultFrequency : HamFrequency.normalize("145.600");
        this.shift = SimpleRadioLibrary.SERVER_CONFIG.repeater.defaultShift;
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

    // ---- Settings ---- \\

    @Nullable
    public String getInputFrequency() {
        return HamFrequency.shift(frequency, shift);
    }

    public static String cleanCallsign(String callsign) {
        String clean = callsign == null ? "" : callsign.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]", "");
        return clean.length() > MAX_CALLSIGN ? clean.substring(0, MAX_CALLSIGN) : clean;
    }

    public void applySettings(String frequency, Frequency.Modulation modulation, int shift, boolean enabled) {
        applySettings(frequency, modulation, shift, enabled, accessTone, outputTone, callsign, linkId);
    }

    public void applySettings(String frequency, Frequency.Modulation modulation, int shift, boolean enabled,
                              float accessTone, float outputTone, String callsign, int linkId) {
        this.frequency = frequency;
        this.modulation = modulation;
        this.shift = shift;
        this.enabled = enabled;
        this.accessTone = Ctcss.normalize(accessTone);
        this.outputTone = Ctcss.normalize(outputTone);
        this.callsign = cleanCallsign(callsign);
        this.linkId = Math.max(0, Math.min(MAX_LINK, linkId));
        this.settingsDirty = true;

        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public boolean isRelaying() {
        return now - lastRelay < RELAY_HOLD_TICKS || now - lastTail < RELAY_HOLD_TICKS || audio.isPlaying();
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    /**
     * Called for every frame heard on the input (or over the link).
     * @return whether the frame is relayed
     */
    private boolean onInput(RadioMessage message) {
        // other repeaters' courtesy tones are relayed, but do not count as traffic
        if (RepeaterTransmitter.isRepeater(message.getOwner())) return true;

        if (now - lastInput > NEW_TRANSMISSION_GAP) {
            relayStart = now;
            timedOut = false;
        }
        lastInput = now;

        int timeout = SimpleRadioLibrary.SERVER_CONFIG.repeater.timeoutSeconds;
        if (!timedOut && timeout > 0 && now - relayStart > timeout * 20L) {
            timedOut = true;
            // a low "bonk" tells the user the repeater dropped them
            enqueueOwn(ToneBurst.render(new int[][]{{400, 600}, {0, 80}, {300, 500}}, SimpleRadioLibrary.SERVER_CONFIG.repeater.courtesyToneVolume));
        }
        if (timedOut) return false;

        lastRelay = now;
        relayedSinceTail = true;
        activitySinceId = true;
        return true;
    }

    private void onLink(RadioMessage message) {
        if (joinedLink > 0) RepeaterLinks.forward(joinedLink, this, message);
    }

    @Override
    public RepeaterTransmitter linkTransmitter() {
        return transmitter;
    }

    @Nullable
    private static int[][] courtesyPattern() {
        return switch (SimpleRadioLibrary.SERVER_CONFIG.repeater.courtesyTone.toUpperCase(Locale.ROOT)) {
            case "NONE" -> null;
            case "K" -> new int[][]{{1000, 180}, {0, 60}, {1000, 60}, {0, 60}, {1000, 180}};
            default -> new int[][]{{880, 90}, {0, 30}, {1320, 120}};
        };
    }

    // The courtesy (tail) tone tells users the other side finished and the repeater is free
    private void sendCourtesyTone() {
        lastTail = now;
        int[][] pattern = courtesyPattern();
        if (pattern != null) enqueueOwn(ToneBurst.render(pattern, SimpleRadioLibrary.SERVER_CONFIG.repeater.courtesyToneVolume));

        // the station identification is due every few minutes of activity
        var config = SimpleRadioLibrary.SERVER_CONFIG.repeater;
        if (!callsign.isEmpty() && activitySinceId && now - lastId >= config.idIntervalMinutes * 1200L) {
            List<short[]> id = Morse.render(callsign, config.idWpm, config.idToneHz, config.idVolume);
            enqueueOwn(ToneBurst.render(new int[][]{{0, 300}}, 0));
            enqueueOwn(id);
            lastId = now;
            activitySinceId = false;
        }
        endPending = true;
    }

    private void enqueueOwn(List<short[]> frames) {
        audio.enqueue(frames);
    }

    private void playOwnAudio() {
        if (transmitter == null || location == null || id == null) {
            audio.clear();
            return;
        }

        for (short[] frame : audio.poll()) {
            RadioMessage message = new RadioMessage(id, location, frame, 1f);
            message.activity = CommonRadioPlugin.analyzeActivity(frame);
            transmitter.transmitOwn(message);
        }

        // the carrier drops after the last tone: receivers close their squelch
        if (endPending && !audio.isPlaying() && now - lastRelay >= RELAY_HOLD_TICKS) {
            endPending = false;
            boolean ownAudio = courtesyPattern() != null || now - lastId < 5;
            UUID owner = ownAudio ? id : transmitter.lastOwner;
            UUID speaker = ownAudio ? null : transmitter.lastRealOwner;
            if (owner != null) transmitter.transmitOwn(RadioMessage.endOfTransmission(owner, speaker, location));
        }
    }

    private double bonus() {
        double top = verticalPos != null ? verticalPos.getY() + DualBandAntennaBlock.HEIGHT : worldPosition.getY() + 0.5 + mastHeight;
        return Propagation.heightBonus(top);
    }

    // a cable-connected vertical scales the range with the square root of its gain (after cable loss)
    private double antennaFactor(@Nullable String onFrequency) {
        if (verticalPos == null || onFrequency == null) return 1;
        long kHz = Math.max(0, HamFrequency.parseKHz(onFrequency));
        return Math.sqrt(DualBandAntennaBlock.gain(kHz) * coax.lossFactor(kHz));
    }

    public org.joml.Vector3f antennaTop() {
        if (verticalPos != null) return new org.joml.Vector3f(verticalPos.getX() + 0.5f, verticalPos.getY() + (float) DualBandAntennaBlock.HEIGHT, verticalPos.getZ() + 0.5f);
        return new org.joml.Vector3f(worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f + mastHeight, worldPosition.getZ() + 0.5f);
    }

    public double outputRange() {
        return (SimpleRadioFrequencing.REPEATER.getTransmissionPower(modulation)
                + antennaScore * SimpleRadioFrequencing.REPEATER.antennaAptitude) * antennaFactor(frequency) + bonus();
    }

    private double receptionPower(@Nullable WorldlyPosition from) {
        return (SimpleRadioFrequencing.REPEATER.receptionPower + antennaScore * SimpleRadioFrequencing.REPEATER.antennaAptitude)
                * antennaFactor(getInputFrequency()) + bonus();
    }

    private void scanAntenna(Level level) {
        verticalPos = null;
        if (coax.isConnected() && coax.getAntenna() != null) {
            BlockPos antenna = coax.getAntenna();
            if (!level.isLoaded(antenna)) return;

            if (level.getBlockState(antenna).getBlock() instanceof DualBandAntennaBlock) {
                verticalPos = antenna;
                mastHeight = 0;
                antennaScore = 0;
                if (transmitter != null) transmitter.antennaPower = 0;
                return;
            }

            coax.disconnect(level, worldPosition);
            onCoaxChanged();
        }

        int maxHeight = SimpleRadioLibrary.SERVER_CONFIG.yagi.maxMastHeight;
        BlockPos.MutableBlockPos cursor = worldPosition.above().mutable();
        int mast = 0;
        while (mast < maxHeight && level.getBlockState(cursor).getBlock() instanceof AntennaBlock) {
            mast++;
            cursor.move(Direction.UP);
        }
        mastHeight = mast;

        BlockState above = level.getBlockState(worldPosition.above());
        antennaScore = above.getBlock() instanceof Antennal antennal ? antennal.climbAntenna(worldPosition.above(), level) : 0;

        if (transmitter != null) transmitter.antennaPower = antennaScore;
    }

    // ---- Coax ---- \\

    @Override
    public CoaxLink getCoax() {
        return coax;
    }

    @Override
    public boolean acceptsAntenna(BlockState antenna) {
        return antenna.getBlock() instanceof DualBandAntennaBlock;
    }

    @Override
    public void onCoaxChanged() {
        if (level != null) scanAntenna(level);
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos antenna = coax.getAntenna();
        if (antenna == null) return super.getRenderBoundingBox();
        return new AABB(worldPosition).minmax(new AABB(antenna)).inflate(1);
    }

    // ---- Routers ---- \\

    private void buildRouters(ServerLevel level) {
        if (id == null) id = UUID.randomUUID();
        routersBuilt = true;
        settingsDirty = false;

        String inputName = getInputFrequency();
        if (inputName == null || inputName.equals(frequency)) return; // a repeater needs a shift

        location = CompatCore.modifyPosition(WorldlyPosition.of(worldPosition, level, worldPosition));
        RadioManager manager = RadioManager.getInstance();

        Frequency output = manager.frequencies().getOrCreate(frequency, modulation);
        RepeaterTransmitter newTransmitter = new RepeaterTransmitter(output, location, id, this::bonus, () -> antennaFactor(frequency), this::onInput, this::onLink);
        newTransmitter.frequencingType(SimpleRadioFrequencing.REPEATER);
        newTransmitter.setLink(RepeaterBlock.class);
        newTransmitter.antennaSupplier = this::antennaTop;
        newTransmitter.antennaPower = antennaScore;
        newTransmitter.ctcssTone = outputTone;
        output.addTransmitter(newTransmitter);
        transmitter = newTransmitter;

        Frequency input = manager.frequencies().getOrCreate(inputName, modulation);
        StationReceiver newReceiver = new StationReceiver(input, location, id, this::receptionPower);
        newReceiver.frequencingType(SimpleRadioFrequencing.REPEATER);
        newReceiver.setLink(RepeaterBlock.class);
        newReceiver.antennaSupplier = this::antennaTop;
        newReceiver.toneSquelch = accessTone;
        input.addReceiver(newReceiver);
        receiver = newReceiver;

        receiver.addRouter(transmitter);
        RepeaterTransmitter.REPEATER_IDS.add(id);

        if (linkId > 0) {
            RepeaterLinks.join(linkId, this);
            joinedLink = linkId;
        }
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

        if (joinedLink > 0) RepeaterLinks.leave(joinedLink, this);
        joinedLink = 0;

        if (id != null) RepeaterTransmitter.REPEATER_IDS.remove(id);
        transmitter = null;
        receiver = null;
        routersBuilt = false;
        audio.clear();
        endPending = false;
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) teardownRouters();
        super.setRemoved();
    }

    // ---- Ticking ---- \\

    public static void serverTick(Level level, BlockPos pos, BlockState state, RepeaterBlockEntity repeater) {
        repeater.tickServer((ServerLevel) level, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        now = level.getGameTime();

        double usage = SimpleRadioLibrary.SERVER_CONFIG.power.repeaterIdleUsage;
        if (isRelaying()) usage += SimpleRadioLibrary.SERVER_CONFIG.power.repeaterTransmitUsage;
        boolean powered = enabled && !health.isBurned() && power.consume(usage);

        boolean wantsChunks = enabled && SimpleRadioLibrary.SERVER_CONFIG.repeater.chunkLoading;
        if (wantsChunks != chunkLoaded) {
            HamChunkLoading.setLoaded(level, worldPosition, wantsChunks);
            chunkLoaded = wantsChunks;
        }

        if (now % 20 == 0) scanAntenna(level);
        if (now % 20 == 0) {
            BlockPos antenna = verticalPos != null ? verticalPos : worldPosition.above(mastHeight);
            if (health.tick(level, worldPosition, antenna, Lightning.heightAboveSupport(level, antenna))) onHealthChanged();
        }

        if (!powered) {
            if (routersBuilt) teardownRouters();
            updateState(level, state, false, false);
            if (now % 20 == 0) setChanged();
            return;
        }

        if (!routersBuilt || settingsDirty) {
            teardownRouters();
            scanAntenna(level);
            buildRouters(level);
        }

        if (relayedSinceTail && now - lastInput >= Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.repeater.courtesyToneDelay)) {
            relayedSinceTail = false;
            sendCourtesyTone();
        }
        if (timedOut && now - lastInput > NEW_TRANSMISSION_GAP) timedOut = false;

        playOwnAudio();

        updateState(level, state, true, isRelaying());
        if (now % 20 == 0) setChanged();
    }

    private void updateState(Level level, BlockState state, boolean on, boolean transmitting) {
        if (state.getValue(RepeaterBlock.ON) == on && state.getValue(RepeaterBlock.TRANSMITTING) == transmitting) return;
        level.setBlock(worldPosition, state.setValue(RepeaterBlock.ON, on).setValue(RepeaterBlock.TRANSMITTING, transmitting), Block.UPDATE_CLIENTS);
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
        if (tag.hasUUID("reference")) this.id = tag.getUUID("reference");
        power.load(tag);
        health.load(tag);

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
        if (tag.contains("shift")) this.shift = tag.getInt("shift");
        if (tag.contains("enabled")) this.enabled = tag.getBoolean("enabled");
        this.accessTone = Ctcss.normalize(tag.getFloat("access_tone"));
        this.outputTone = Ctcss.normalize(tag.getFloat("output_tone"));
        this.callsign = cleanCallsign(tag.getString("callsign"));
        this.linkId = Math.max(0, Math.min(MAX_LINK, tag.getInt("link")));
        coax.load(tag);
        this.settingsDirty = true;
    }

    public void saveSettings(CompoundTag tag) {
        coax.save(tag);
        tag.putString("frequency", frequency);
        tag.putString("modulation", modulation.shorthand);
        tag.putInt("shift", shift);
        tag.putBoolean("enabled", enabled);
        tag.putFloat("access_tone", accessTone);
        tag.putFloat("output_tone", outputTone);
        tag.putString("callsign", callsign);
        tag.putInt("link", linkId);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveSettings(tag);
        health.save(tag);
        if (id != null) tag.putUUID("reference", id);
        power.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveSettings(tag);
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
        return Component.translatable("block.simpleradio.repeater");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RepeaterMenu(containerId, inventory, worldPosition, dataAccess);
    }
}
