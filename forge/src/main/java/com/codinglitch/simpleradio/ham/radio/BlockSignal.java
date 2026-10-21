package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.central.AuditoryBlockEntity;
import com.codinglitch.simpleradio.core.central.Powered;
import com.codinglitch.simpleradio.core.registry.blocks.CatalyzingBlockEntity;
import com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity;
import com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity;
import com.codinglitch.simpleradio.ham.block.RepeaterBlockEntity;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * S-meter of the original radio and receiver blocks: what a player looking at one sees.
 */
public final class BlockSignal {
    /** How far away (blocks) a player can read the meter. */
    public static final double MAX_DISTANCE = 8;
    /** Ticks a signal stays on the meter after the last audio. */
    private static final int HOLD_TICKS = 10;

    public static final int STATUS_ON = 0;
    public static final int STATUS_NO_POWER = 1;
    public static final int STATUS_NO_CATALYST = 2;
    public static final int STATUS_OFF = 3;

    /**
     * @param status one of the STATUS_ values
     * @param signal the signal margin in tenths of a dB, Short.MIN_VALUE when nothing is heard
     */
    public record Reading(int status, int signal, String frequency, String modulation) {}

    private BlockSignal() {}

    public static boolean hasMeter(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof RadioBlockEntity || blockEntity instanceof ReceiverBlockEntity;
    }

    @Nullable
    public static Reading read(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!hasMeter(blockEntity)) return null;

        AuditoryBlockEntity radio = (AuditoryBlockEntity) blockEntity;
        String frequency = radio.frequency == null ? "" : radio.frequency.getFrequency();
        String modulation = radio.frequency == null ? "" : radio.frequency.getModulation().display();

        int status = STATUS_ON;
        if (blockEntity instanceof CatalyzingBlockEntity catalyzing && catalyzing.catalyst == null) status = STATUS_NO_CATALYST;
        else if (blockEntity instanceof Powered powered && !powered.getPowerBuffer().isPowered()) status = STATUS_NO_POWER;
        else if (radio.receiver == null || !radio.receiver.isActive()) status = STATUS_OFF;

        int signal = status == STATUS_ON && radio.receiver instanceof RadioReceiver receiver
                ? RepeaterBlockEntity.signalData(receiver.currentSignalDb(HOLD_TICKS)) : Short.MIN_VALUE;
        return new Reading(status, signal, frequency, modulation);
    }
}
