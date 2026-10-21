package com.codinglitch.simpleradio.ham.block;

import com.codinglitch.simpleradio.CompatCore;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.radio.Morse;
import com.codinglitch.simpleradio.ham.radio.PacedAudio;
import com.codinglitch.simpleradio.ham.radio.ToneBurst;
import com.codinglitch.simpleradio.radio.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A fox hunt (ARDF) transmitter: a small hidden beacon that keys "MOE", "MOI", "MOS", "MOH" or "MO5"
 * (fox 1-5) in slow morse over and over. Hunters find it with a direction finding receiver.
 */
public class FoxTransmitterBlockEntity extends BlockEntity implements Powered {
    public static final String[] IDENTITIES = {"MOE", "MOI", "MOS", "MOH", "MO5"};

    public String frequency;
    public int fox = 1;
    public UUID id;

    public final PowerBuffer power = new PowerBuffer(100_000);

    @Nullable private RadioTransmitter transmitter;
    @Nullable private WorldlyPosition location;
    private boolean built = false;
    private boolean settingsDirty = true;
    private final PacedAudio audio = new PacedAudio();
    private boolean wasOnAir = false;

    public FoxTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(HamContent.FOX_TRANSMITTER_BE.get(), pos, state);
        String defaultFrequency = HamFrequency.format(144_500);
        this.frequency = defaultFrequency != null ? defaultFrequency : HamFrequency.normalize("144.500");
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
        if (id == null && !level.isClientSide) id = UUID.randomUUID();
    }

    public String identity() {
        return IDENTITIES[Math.max(1, Math.min(IDENTITIES.length, fox)) - 1];
    }

    public void setFox(int fox) {
        this.fox = Math.max(1, Math.min(IDENTITIES.length, fox));
        audio.clear();
        changed();
    }

    public void setFrequency(String frequency) {
        this.frequency = frequency;
        this.settingsDirty = true;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * In an ARDF hunt the foxes take turns: fox n transmits in the n-th fifth of every cycle.
     */
    public boolean isOnAir(long gameTime) {
        int cycle = SimpleRadioLibrary.SERVER_CONFIG.fox.cycleSeconds;
        if (cycle <= 0) return true;
        long ticks = cycle * 20L;
        long slot = ticks / IDENTITIES.length;
        long position = Math.floorMod(gameTime, ticks);
        return position / slot == fox - 1;
    }

    private void build(ServerLevel level) {
        if (id == null) id = UUID.randomUUID();
        location = CompatCore.modifyPosition(WorldlyPosition.of(worldPosition, level, worldPosition));
        Frequency channel = RadioManager.getInstance().frequencies().getOrCreate(frequency, Frequency.Modulation.FREQUENCY);
        RadioTransmitter newTransmitter = new RadioTransmitter(channel, location, id);
        newTransmitter.frequencingType(SimpleRadioFrequencing.FOX);
        newTransmitter.setLink(FoxTransmitterBlock.class);
        channel.addTransmitter(newTransmitter);
        transmitter = newTransmitter;
        built = true;
        settingsDirty = false;
    }

    private void teardown() {
        if (transmitter != null) {
            if (transmitter.frequency != null) transmitter.frequency.removeTransmitter(transmitter);
            RadioManager.getInstance().removeRouter(transmitter);
        }
        transmitter = null;
        built = false;
        audio.clear();
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) teardown();
        super.setRemoved();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FoxTransmitterBlockEntity fox) {
        fox.tickServer((ServerLevel) level, state);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        long now = level.getGameTime();
        boolean powered = power.consume(SimpleRadioLibrary.SERVER_CONFIG.fox.usage);
        if (!powered) {
            if (built) teardown();
            updateState(level, state, false);
            if (now % 20 == 0) setChanged();
            return;
        }
        if (!built || settingsDirty) {
            teardown();
            build(level);
        }

        boolean onAir = isOnAir(now);
        if (onAir && !audio.isPlaying()) {
            var config = SimpleRadioLibrary.SERVER_CONFIG.fox;
            List<short[]> frames = new ArrayList<>(Morse.render(identity(), config.wpm, config.toneHz, 0.5));
            frames.addAll(ToneBurst.render(new int[][]{{0, 1000}}, 0));
            audio.enqueue(frames);
        } else if (!onAir && audio.isPlaying()) {
            audio.clear();
        }

        if (transmitter != null && location != null && id != null) {
            for (short[] frame : audio.poll()) {
                RadioMessage message = new RadioMessage(id, location, frame, 1f);
                message.activity = CommonRadioPlugin.analyzeActivity(frame);
                transmitter.accept(message);
            }
            if (wasOnAir && !onAir) transmitter.endTransmission(id, null);
        }
        wasOnAir = onAir;

        updateState(level, state, onAir);
        if (now % 20 == 0) setChanged();
    }

    private void updateState(Level level, BlockState state, boolean transmitting) {
        if (state.getValue(FoxTransmitterBlock.TRANSMITTING) == transmitting) return;
        level.setBlock(worldPosition, state.setValue(FoxTransmitterBlock.TRANSMITTING, transmitting), Block.UPDATE_CLIENTS);
    }

    // ---- Saving ---- \\

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadSettings(tag);
        if (tag.hasUUID("reference")) id = tag.getUUID("reference");
        power.load(tag);
    }

    public void loadSettings(CompoundTag tag) {
        String loaded = HamFrequency.normalize(tag.getString("frequency"));
        if (loaded != null) frequency = loaded;
        if (tag.contains("fox")) fox = Math.max(1, Math.min(IDENTITIES.length, tag.getInt("fox")));
        settingsDirty = true;
    }

    public void saveSettings(CompoundTag tag) {
        tag.putString("frequency", frequency);
        tag.putInt("fox", fox);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveSettings(tag);
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
}
