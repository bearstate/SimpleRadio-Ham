package com.codinglitch.simpleradio.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.FrequencingType;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.routers.Receiver;
import com.codinglitch.simpleradio.routers.Speaker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The second receivers of a handheld (server side):
 * <ul>
 *     <li>Dual watch: a second frequency is heard as well; the main one has priority.</li>
 *     <li>Memory scan: the stored channels are checked one after the other and the handheld stops
 *     on a channel with a signal until it has been quiet for a moment.</li>
 * </ul>
 * Transmitting always happens on the main frequency.
 */
public final class HandheldExtras {
    public static final String REFERENCE_WATCH = "reference_b";
    public static final String REFERENCE_SCAN = "reference_scan";

    private static final class State {
        @Nullable RadioReceiver watch;
        @Nullable RadioReceiver scan;
        int scanIndex = -1;
        long holdUntil = -1;
        long nextHop = 0;
        boolean holding = false;
        boolean suspended = false;
    }

    public record Status(float signalDb, @Nullable String heardOn, boolean scanning, boolean holding) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private HandheldExtras() {}

    public static void tick(TransceiverItem item, ItemStack stack, Entity entity, ServerLevel level, UUID reference, FrequencingType type) {
        CompoundTag tag = stack.getOrCreateTag();
        State state = STATES.computeIfAbsent(reference, key -> new State());
        Speaker speaker = RadioManager.getInstance().speakers().get(reference);
        String main = tag.getString("frequency");
        Frequency.Modulation modulation = Frequency.modulationOf(tag.getString("modulation"));
        if (speaker == null || modulation == null) {
            remove(reference);
            return;
        }

        // ---- Dual watch ---- \\
        String watchFrequency = HamFrequency.normalize(tag.getString(TransceiverItem.WATCH_FREQUENCY));
        boolean watching = tag.getBoolean(TransceiverItem.DUAL_WATCH) && watchFrequency != null && !watchFrequency.equals(main);
        if (watching) {
            if (state.watch == null || !watchFrequency.equals(state.watch.frequency.getFrequency())) {
                close(state.watch);
                state.watch = open(stack, entity, REFERENCE_WATCH, watchFrequency, modulation, type, speaker, reference, item);
            }
        } else {
            close(state.watch);
            state.watch = null;
        }

        // ---- Memory scan ---- \\
        List<TransceiverItem.Memory> memories = TransceiverItem.getMemories(tag);
        boolean scanning = tag.getBoolean(TransceiverItem.SCAN) && memories.size() > 1;
        if (!scanning) {
            close(state.scan);
            state.scan = null;
            state.holding = false;
            return;
        }

        long now = level.getGameTime();
        if (state.holding) {
            if (state.scan != null && state.scan.getActivityTime() > 0) state.holdUntil = now + SimpleRadioLibrary.SERVER_CONFIG.handheld.scanHangTicks;
            if (now < state.holdUntil) return;
            state.holding = false;
        }

        if (now < state.nextHop || state.suspended) return;
        state.nextHop = now + Math.max(1, 20 / Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.handheld.scanChannelsPerSecond));

        // next stored channel that is not the main frequency
        for (int attempt = 0; attempt < memories.size(); attempt++) {
            state.scanIndex = (state.scanIndex + 1) % memories.size();
            TransceiverItem.Memory memory = memories.get(state.scanIndex);
            if (memory.frequency().equals(main) && memory.modulation() == modulation) continue;

            if (state.scan == null || !memory.frequency().equals(state.scan.frequency.getFrequency()) || state.scan.frequency.getModulation() != memory.modulation()) {
                close(state.scan);
                state.scan = open(stack, entity, REFERENCE_SCAN, memory.frequency(), memory.modulation(), type, speaker, reference, item);
                state.scan.toneSquelch = memory.toneRx();
            }
            if (Carrier.present(state.scan.frequency, state.scan, level)) {
                state.holding = true;
                state.holdUntil = now + SimpleRadioLibrary.SERVER_CONFIG.handheld.scanHangTicks;
            }
            return;
        }
    }

    private static RadioReceiver open(ItemStack stack, Entity entity, String key, String frequencyName, Frequency.Modulation modulation,
                                      FrequencingType type, Speaker speaker, UUID mainReference, TransceiverItem item) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID(key)) tag.putUUID(key, UUID.randomUUID());

        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate(frequencyName, modulation);
        RadioReceiver receiver = new RadioReceiver(frequency, entity, tag.getUUID(key));
        receiver.frequencingType(type);
        receiver.setLink(item.getClass());
        receiver.setOwner(entity);
        receiver.tryAddRouter(speaker);

        if (REFERENCE_WATCH.equals(key)) {
            // the main frequency has priority: while it is busy the second one is muted
            receiver.setAcceptingCriteria(message -> {
                Receiver main = mainReceiver(tag, mainReference);
                return !(main instanceof RadioReceiver radioReceiver) || radioReceiver.getActivityTime() <= 0;
            });
        }
        return receiver;
    }

    @Nullable
    private static Receiver mainReceiver(CompoundTag tag, UUID reference) {
        Frequency frequency = RadioManager.getInstance().frequencies().get(tag.getString("frequency"), Frequency.modulationOf(tag.getString("modulation")));
        return frequency == null ? null : frequency.getReceiver(reference);
    }

    private static void close(@Nullable RadioReceiver receiver) {
        if (receiver == null) return;
        if (receiver.frequency != null) receiver.frequency.removeReceiver(receiver);
        RadioManager.getInstance().removeRouter(receiver);
    }

    /**
     * Half duplex: the second receivers are muted while the handheld transmits.
     */
    public static void setActive(UUID reference, boolean active) {
        State state = STATES.get(reference);
        if (state == null) return;
        state.suspended = !active;
        if (state.watch != null) state.watch.setActive(active);
        if (state.scan != null) state.scan.setActive(active);
    }

    public static void remove(UUID reference) {
        State state = STATES.remove(reference);
        if (state == null) return;
        close(state.watch);
        close(state.scan);
    }

    public static Status status(ItemStack stack, UUID reference) {
        CompoundTag tag = stack.getOrCreateTag();
        State state = STATES.get(reference);
        Receiver main = mainReceiver(tag, reference);

        float best = main instanceof RadioReceiver radioReceiver ? radioReceiver.currentSignalDb(10) : Float.NaN;
        String heardOn = Float.isNaN(best) ? null : tag.getString("frequency");
        boolean scanning = state != null && state.scan != null;
        boolean holding = state != null && state.holding;

        if (state != null) {
            for (RadioReceiver extra : new RadioReceiver[]{state.watch, state.scan}) {
                if (extra == null) continue;
                float signal = extra.currentSignalDb(10);
                if (!Float.isNaN(signal) && (Float.isNaN(best) || signal > best)) {
                    best = signal;
                    heardOn = extra.frequency.getFrequency();
                }
            }
            if (heardOn == null && state.scan != null) heardOn = state.scan.frequency.getFrequency();
        }
        return new Status(best, heardOn, scanning, holding);
    }

    public static void clear() {
        for (UUID reference : List.copyOf(STATES.keySet())) remove(reference);
    }
}
