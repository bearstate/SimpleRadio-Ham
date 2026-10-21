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
import com.codinglitch.simpleradio.ham.menu.ScannerMenu;
import com.codinglitch.simpleradio.ham.radio.Propagation;
import com.codinglitch.simpleradio.ham.radio.StationReceiver;
import com.codinglitch.simpleradio.radio.*;
import com.codinglitch.simpleradio.routers.Speaker;
import com.codinglitch.simpleradio.routers.Transmitter;
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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A frequency scanner: sweeps a frequency range channel by channel and stops on the first channel
 * where a transmitter within reach is on the air. It stays there while the signal lasts and resumes
 * scanning a moment after it stops. When scanning is stopped it simply listens on the current channel.
 */
public class ScannerBlockEntity extends BlockEntity implements Powered, MenuProvider {
    public static final int[] STEPS = {5, 10, 25, 50, 100};

    public static final int ACTION_TOGGLE = 0;
    public static final int ACTION_SKIP = 1;
    public static final int ACTION_UP = 2;
    public static final int ACTION_DOWN = 3;

    // ---- Settings ---- \\
    public String startFrequency;
    public String endFrequency;
    public int stepKHz = 25;
    public Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    public boolean scanning = false;
    public UUID id;

    public final PowerBuffer power = new PowerBuffer(2_000);
    public final DeviceHealth health = new DeviceHealth();

    // ---- Runtime (server) ---- \\
    private long cursorKHz = -1;
    private boolean holding = false;
    private long lastHeard = 0;
    private long now = 0;
    private double channelBudget = 0;
    private final Map<Long, Long> skipped = new HashMap<>();

    @Nullable private WorldlyPosition location;
    @Nullable private Speaker speaker;
    @Nullable private StationReceiver probe;
    @Nullable private StationReceiver receiver;
    private long tunedKHz = -1;
    private boolean built = false;

    private int antennaScore = 0;
    private int mastHeight = 0;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            long cursor = Math.max(0, cursorKHz);
            return switch (index) {
                case ScannerMenu.DATA_ENERGY -> (int) power.getStored();
                case ScannerMenu.DATA_CAPACITY -> (int) power.getCapacity();
                case ScannerMenu.DATA_FLAGS -> (power.isPowered() ? ScannerMenu.FLAG_POWERED : 0)
                        | (scanning ? ScannerMenu.FLAG_SCANNING : 0)
                        | (holding ? ScannerMenu.FLAG_HOLDING : 0)
                        | (receiver != null && receiver.getActivityTime() > 0 ? ScannerMenu.FLAG_AUDIO : 0)
                        | (health.isBurned() ? ScannerMenu.FLAG_BURNED : 0);
                case ScannerMenu.DATA_CURSOR_MHZ -> (int) (cursor / 1000);
                case ScannerMenu.DATA_CURSOR_KHZ -> (int) (cursor % 1000);
                case ScannerMenu.DATA_SIGNAL -> receiver == null ? Short.MIN_VALUE : RepeaterBlockEntity.signalData(receiver.currentSignalDb(10));
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return ScannerMenu.DATA_COUNT;
        }
    };

    public ScannerBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.SCANNER_BE.get(), pos, state);
        this.startFrequency = HamFrequency.format(144_000);
        this.endFrequency = HamFrequency.format(146_000);
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

    // ---- Settings & controls ---- \\

    public void applySettings(String start, String end, int step, Frequency.Modulation modulation) {
        long startKHz = HamFrequency.parseKHz(start), endKHz = HamFrequency.parseKHz(end);
        if (startKHz > endKHz) {
            String swap = start;
            start = end;
            end = swap;
        }
        this.startFrequency = start;
        this.endFrequency = end;
        this.stepKHz = step;
        this.modulation = modulation;
        this.cursorKHz = -1;
        this.holding = false;
        untune();

        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void control(int action) {
        switch (action) {
            case ACTION_TOGGLE -> {
                scanning = !scanning;
                holding = false;
                untune();
                if (scanning) cursorKHz = next(cursorKHz);
            }
            case ACTION_SKIP -> {
                if (scanning && holding) {
                    skipped.put(cursorKHz, now + SimpleRadioLibrary.SERVER_CONFIG.scanner.skipTicks);
                    holding = false;
                    untune();
                    cursorKHz = next(cursorKHz);
                }
            }
            case ACTION_UP, ACTION_DOWN -> {
                if (!scanning) cursorKHz = action == ACTION_UP ? next(cursorKHz) : previous(cursorKHz);
            }
            default -> {}
        }
        setChanged();
    }

    public long getCursorKHz() {
        return cursorKHz;
    }

    public boolean isHolding() {
        return holding;
    }

    // ---- Channels ---- \\

    private long startKHz() {
        return Math.max(0, HamFrequency.parseKHz(startFrequency));
    }

    private long endKHz() {
        return Math.max(startKHz(), HamFrequency.parseKHz(endFrequency));
    }

    private long next(long kHz) {
        long start = startKHz(), end = endKHz();
        if (kHz < start || kHz >= end) return start;
        long next = kHz + Math.max(1, stepKHz);
        return next > end ? start : next;
    }

    private long previous(long kHz) {
        long start = startKHz(), end = endKHz();
        if (kHz <= start || kHz > end) return start + (end - start) / Math.max(1, stepKHz) * Math.max(1, stepKHz);
        return kHz - Math.max(1, stepKHz);
    }

    private boolean isSkipped(long kHz) {
        Long until = skipped.get(kHz);
        return until != null && until > now;
    }

    /**
     * Carrier detection: is a transmitter within reach on the air on this channel right now?
     */
    private boolean hasCarrier(long kHz) {
        if (probe == null || location == null) return false;

        String name = HamFrequency.format(kHz);
        if (name == null) return false;

        return Carrier.present(RadioManager.getInstance().frequencies().get(name, modulation), probe, location.level);
    }

    // ---- Antenna ---- \\

    private void scanAntenna(Level level) {
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
    }

    public org.joml.Vector3f antennaTop() {
        return new org.joml.Vector3f(worldPosition.getX() + 0.5f, worldPosition.getY() + 0.5f + mastHeight, worldPosition.getZ() + 0.5f);
    }

    private double receptionPower(@Nullable WorldlyPosition from) {
        return SimpleRadioFrequencing.SCANNER.receptionPower + antennaScore * SimpleRadioFrequencing.SCANNER.antennaAptitude
                + Propagation.heightBonus(worldPosition.getY() + 0.5 + mastHeight);
    }

    // ---- Routers ---- \\

    private void build(ServerLevel level) {
        if (id == null) id = UUID.randomUUID();
        location = CompatCore.modifyPosition(WorldlyPosition.of(worldPosition, level, worldPosition));
        RadioManager manager = RadioManager.getInstance();

        speaker = manager.speakers().getOrCreate(location, id);
        speaker.setRange(SimpleRadioLibrary.SERVER_CONFIG.scanner.speakingRange);
        speaker.setCategory(CommonRadioPlugin.RADIOS_CATEGORY);
        speaker.setLink(ScannerBlock.class);

        // never registered anywhere: only used to evaluate whether signals reach the scanner
        String startName = startFrequency != null ? startFrequency : HamFrequency.format(144_000);
        probe = new StationReceiver(manager.frequencies().getOrCreate(startName, modulation), location, id, this::receptionPower);
        probe.frequencingType(SimpleRadioFrequencing.SCANNER);
        probe.antennaSupplier = this::antennaTop;

        built = true;
    }

    private void tune(long kHz) {
        if (receiver != null && tunedKHz == kHz) return;
        untune();
        if (location == null || speaker == null) return;

        String name = HamFrequency.format(kHz);
        if (name == null) return;

        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate(name, modulation);
        StationReceiver newReceiver = new StationReceiver(frequency, location, id, this::receptionPower);
        newReceiver.frequencingType(SimpleRadioFrequencing.SCANNER);
        newReceiver.setLink(ScannerBlock.class);
        newReceiver.antennaSupplier = this::antennaTop;
        newReceiver.setAcceptingCriteria(message -> {
            if (!((RadioMessage) message).endOfTransmission) lastHeard = now;
            return true;
        });
        frequency.addReceiver(newReceiver);
        newReceiver.addRouter(speaker);

        receiver = newReceiver;
        tunedKHz = kHz;
    }

    private void untune() {
        if (receiver != null) {
            if (receiver.frequency != null) receiver.frequency.removeReceiver(receiver);
            RadioManager.getInstance().removeRouter(receiver);
        }
        receiver = null;
        tunedKHz = -1;
    }

    private void teardown() {
        untune();
        if (id != null) {
            RadioManager manager = RadioManager.getInstance();
            manager.removeRouter(manager.speakers().remove(id));
        }
        speaker = null;
        probe = null;
        built = false;
        holding = false;
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) teardown();
        super.setRemoved();
    }

    // ---- Ticking ---- \\

    public static void serverTick(Level level, BlockPos pos, BlockState state, ScannerBlockEntity scanner) {
        scanner.tickServer((ServerLevel) level, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        now = level.getGameTime();
        boolean powered = !health.isBurned() && power.consume(SimpleRadioLibrary.SERVER_CONFIG.power.scannerUsage);

        if (now % 20 == 0 || !built) scanAntenna(level);
        if (now % 20 == 0) {
            BlockPos antenna = worldPosition.above(mastHeight);
            if (health.tick(level, worldPosition, antenna, com.codinglitch.simpleradio.ham.radio.Lightning.heightAboveSupport(level, antenna))) onHealthChanged();
        }

        if (!powered) {
            if (built) teardown();
            updateState(level, state, false, false);
            if (now % 20 == 0) setChanged();
            return;
        }
        if (!built) build(level);

        if (cursorKHz < startKHz() || cursorKHz > endKHz()) cursorKHz = startKHz();

        if (!scanning) {
            tune(cursorKHz);
        } else if (holding) {
            if (now - lastHeard > SimpleRadioLibrary.SERVER_CONFIG.scanner.hangTicks) {
                holding = false;
                untune();
                cursorKHz = next(cursorKHz);
            }
        } else {
            channelBudget += SimpleRadioLibrary.SERVER_CONFIG.scanner.channelsPerSecond / 20.0;
            while (channelBudget >= 1) {
                channelBudget -= 1;
                if (!isSkipped(cursorKHz) && hasCarrier(cursorKHz)) {
                    holding = true;
                    lastHeard = now;
                    channelBudget = 0;
                    tune(cursorKHz);
                    break;
                }
                cursorKHz = next(cursorKHz);
            }
        }

        if (now % 100 == 0) skipped.values().removeIf(until -> until <= now);

        boolean signal = holding || (receiver != null && receiver.getActivityTime() > 0);
        updateState(level, state, true, signal);
        if (now % 20 == 0) setChanged();
    }

    private void updateState(Level level, BlockState state, boolean on, boolean signal) {
        if (state.getValue(ScannerBlock.ON) == on && state.getValue(ScannerBlock.SIGNAL) == signal) return;
        level.setBlock(worldPosition, state.setValue(ScannerBlock.ON, on).setValue(ScannerBlock.SIGNAL, signal), Block.UPDATE_CLIENTS);
    }

    public void onHealthChanged() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---- Saving ---- \\

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        String start = HamFrequency.normalize(tag.getString("start"));
        String end = HamFrequency.normalize(tag.getString("end"));
        if (start != null) startFrequency = start;
        if (end != null) endFrequency = end;
        if (tag.contains("step")) stepKHz = tag.getInt("step");
        Frequency.Modulation loaded = Frequency.modulationOf(tag.getString("modulation"));
        if (loaded != null) modulation = loaded;
        scanning = tag.getBoolean("scanning");
        if (tag.contains("cursor")) cursorKHz = tag.getLong("cursor");
        if (tag.hasUUID("reference")) id = tag.getUUID("reference");
        power.load(tag);
        health.load(tag);
    }

    private void saveSettings(CompoundTag tag) {
        health.save(tag);
        tag.putString("start", startFrequency);
        tag.putString("end", endFrequency);
        tag.putInt("step", stepKHz);
        tag.putString("modulation", modulation.shorthand);
        tag.putBoolean("scanning", scanning);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveSettings(tag);
        tag.putLong("cursor", cursorKHz);
        if (id != null) tag.putUUID("reference", id);
        power.save(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveSettings(tag);
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
        return Component.translatable("block.simpleradio.scanner");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ScannerMenu(containerId, inventory, worldPosition, dataAccess);
    }
}
