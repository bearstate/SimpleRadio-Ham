package com.codinglitch.simpleradio.core.registry.blocks;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.CompatCore;
import com.codinglitch.simpleradio.SimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.core.central.PowerBuffer;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.central.Transmitting;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlockEntities;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.SimpleRadioSounds;
import com.codinglitch.simpleradio.platform.Services;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.codinglitch.simpleradio.routers.Router;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TransmitterBlockEntity extends CatalyzingBlockEntity implements Transmitting, Powered {
    public boolean isDirty = true;
    public int antennaPower = 0;

    public final PowerBuffer power = new PowerBuffer(2000);

    @Override
    public PowerBuffer getPowerBuffer() {
        return power;
    }

    public TransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(SimpleRadioBlockEntities.TRANSMITTER, pos, state);
    }

    @Override
    public BlockPos getAdaptorLocation() {
        return getBlockPos().relative(getBlockState().getValue(TransmitterBlock.FACING).getOpposite());
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide && this.transmitter != null) {
            level.playSound(
                    null, transmitter.getPosition().x, transmitter.getPosition().y, transmitter.getPosition().z,
                    SimpleRadioSounds.RADIO_CLOSE,
                    SoundSource.PLAYERS,
                    1f, 1f
            );
        }

        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag tag) {
        //inactivate();
        super.loadTag(tag);
    }
    @Override
    public void saveTag(CompoundTag tag) {
        super.saveTag(tag);

        tag.putInt("antennaPower", antennaPower);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadTag(tag);
        power.load(tag);

        if (tag.contains("antennaPower")) {
            this.antennaPower = tag.getInt("antennaPower");
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        saveTag(tag);
        power.save(tag);
        super.saveAdditional(tag);
    }

    @Override
    public void saveToItem(ItemStack stack) {
        saveTag(stack.getOrCreateTag());
        super.saveToItem(stack);
    }

    @Override
    public void markDirty() {
        this.isDirty = true;
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, TransmitterBlockEntity blockEntity) {
        if (blockEntity.frequency != null && blockEntity.id != null && !blockEntity.active) {
            blockEntity.activate();
        }
        CatalyzingBlockEntity.tick(level, pos, blockState, blockEntity);

        if (!level.isClientSide) {
            boolean powered = blockEntity.power.consume(SimpleRadioLibrary.SERVER_CONFIG.power.transmitterUsage);
            if (blockEntity.transmitter != null) blockEntity.transmitter.setActive(blockEntity.catalyst != null && powered);
            if (blockEntity.transmitter instanceof RadioTransmitter transmitter) {
                // the catalyst's efficiency scales the output, an antenna structure is outside the building
                transmitter.powerMultiplier = blockEntity.catalyst != null ? blockEntity.catalyst.efficiency : 1;
                transmitter.externalAntenna = blockEntity.antennaPower > 0;
            }
            if (level.getGameTime() % 20 == 0) blockEntity.setChanged();
        } else if (blockEntity.transmitter != null) {
            blockEntity.transmitter.setActive(blockEntity.catalyst != null);
        }

        if (!blockEntity.catalyzed) return;

        if (blockEntity.isDirty && level.getGameTime() % 200 == 0 && !level.isClientSide) {
            blockEntity.antennaPower = blockEntity.calculateAntennaPower(blockEntity.getAdaptorLocation(), level);
            Router router = blockEntity.getRouter();
            if (router instanceof RadioTransmitter transmitter) transmitter.antennaPower = blockEntity.antennaPower;

            level.sendBlockUpdated(pos, blockState, blockState, Block.UPDATE_CLIENTS);
            blockEntity.setChanged();
            blockEntity.isDirty = false;
        }
    }

    @Override
    public void deactivate() {
        if (this.frequency != null)
            stopTransmitting(frequency.getFrequency(), frequency.getModulation(), id, level.isClientSide);

        // Clean up the invalidated routers.
        super.deactivate();
    }

    @Override
    public void activate() {
        CommonSimpleRadio.info("Activating transmitter with reference {}", id);
        refreshFrequency();
        WorldlyPosition location = CompatCore.modifyPosition(WorldlyPosition.of(worldPosition, level, worldPosition));

        this.transmitter = SimpleRadioBlocks.TRANSMITTER.getOrCreateTransmitter(location, frequency, id, this.getBlockState());
        if (!level.isClientSide) {
            level.playSound(
                    null, location.x, location.y, location.z,
                    SimpleRadioSounds.RADIO_OPEN,
                    SoundSource.PLAYERS,
                    1f, 1f
            );
        }

        // Mark this block as active.
        super.activate();
        markDirty();
    }

    public int getAntennaPower() {
        return antennaPower;
    }
}
