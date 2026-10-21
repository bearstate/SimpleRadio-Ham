package com.codinglitch.simpleradio.ham.aprs;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.licence.Licences;
import com.codinglitch.simpleradio.ham.radio.PacedAudio;
import com.codinglitch.simpleradio.radio.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A handheld with APRS: a normal voice transceiver that also sends its position every minute or so on
 * 144.800 MHz and decodes the position reports it hears. The stations it heard are shown on its map.
 */
public class AprsRadioItem extends TransceiverItem {
    public static final String BEACON = "aprs_beacon";
    public static final String COMMENT = "aprs_comment";
    public static final String REFERENCE = "reference_aprs";
    public static final int MAX_COMMENT = 30;
    private static final int SSID = 7; // handheld

    private static final class State {
        RadioReceiver receiver;
        RadioTransmitter transmitter;
        final PacedAudio audio = new PacedAudio();
        long lastBeacon = Long.MIN_VALUE / 2;
        Vec3 lastPosition;
        long lastPositionTime;
        boolean sending;
        boolean warned;
        AprsPacket pendingPacket;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    public AprsRadioItem(Properties settings) {
        super(settings);
    }

    public static boolean isBeaconing(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(BEACON) || tag.getBoolean(BEACON);
    }

    public static String comment(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(COMMENT);
    }

    public static void applyAprs(ItemStack stack, boolean beacon, String comment) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(BEACON, beacon);
        String clean = comment.replaceAll("[^\\x20-\\x7E]", "");
        tag.putString(COMMENT, clean.length() > MAX_COMMENT ? clean.substring(0, MAX_COMMENT) : clean);
    }

    @Override
    public void entityTick(ItemStack stack, Entity entity) {
        super.entityTick(stack, entity);
        if (!(entity.level() instanceof ServerLevel level)) return;

        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.hasUUID("reference") || !isOperational(stack)) {
            if (tag.hasUUID(REFERENCE)) remove(tag.getUUID(REFERENCE));
            return;
        }
        if (!tag.hasUUID(REFERENCE)) tag.putUUID(REFERENCE, UUID.randomUUID());
        tickAprs(stack, entity, level, tag.getUUID(REFERENCE));
    }

    private void tickAprs(ItemStack stack, Entity entity, ServerLevel level, UUID reference) {
        State state = STATES.computeIfAbsent(reference, key -> new State());
        String frequencyName = HamFrequency.normalize(SimpleRadioLibrary.SERVER_CONFIG.aprs.frequency);
        if (frequencyName == null) return;

        // ---- routers on the APRS frequency ---- \\
        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate(frequencyName, Frequency.Modulation.FREQUENCY);
        if (state.receiver == null || state.receiver.frequency != frequency || !state.receiver.isValid()) {
            close(state);
            RadioReceiver receiver = new RadioReceiver(frequency, entity, reference);
            receiver.frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            receiver.setLink(this.getClass());
            receiver.setOwner(entity);
            UUID listener = entity.getUUID();
            receiver.setAcceptingCriteria(message -> {
                // decode silently: packets are shown on the map, not played from this receiver
                if (message instanceof RadioMessage radioMessage && radioMessage.payload instanceof AprsPacket packet
                        && !Float.isNaN(radioMessage.signalDb) && radioMessage.signalDb >= SimpleRadioLibrary.SERVER_CONFIG.aprs.decodeMarginDb
                        && !reference.equals(radioMessage.getOwner())) {
                    AprsNetwork.heard(listener, packet, radioMessage.signalDb, level.getGameTime());
                }
                return false;
            });
            state.receiver = receiver;

            RadioTransmitter transmitter = new RadioTransmitter(frequency, entity, reference);
            transmitter.frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            transmitter.setLink(this.getClass());
            transmitter.setActive(false);
            frequency.addTransmitter(transmitter);
            state.transmitter = transmitter;
        }
        state.transmitter.powerMultiplier = batteryRangeFactor(stack);

        // ---- beacon ---- \\
        long now = level.getGameTime();
        Vec3 position = entity.position();
        double speedKmh = 0, course = 0;
        if (state.lastPosition != null && now > state.lastPositionTime) {
            Vec3 moved = position.subtract(state.lastPosition);
            double seconds = (now - state.lastPositionTime) / 20.0;
            speedKmh = Math.sqrt(moved.x * moved.x + moved.z * moved.z) / seconds * 3.6;
            course = (Math.toDegrees(Math.atan2(moved.x, -moved.z)) + 360) % 360;
        }

        var config = SimpleRadioLibrary.SERVER_CONFIG.aprs;
        long interval = (speedKmh >= config.fastSpeedKmh ? config.fastBeaconSeconds : config.beaconSeconds) * 20L;
        if (isBeaconing(stack) && !state.audio.isPlaying() && now - state.lastBeacon >= interval) {
            state.lastBeacon = now;
            Player player = entity instanceof Player p ? p : null;
            String denial = BandPlan.denial(player, frequencyName, BandPlan.Equipment.AMATEUR);
            if (denial != null) {
                if (!state.warned && player != null) player.displayClientMessage(Component.translatable("message.simpleradio.aprs.denied").withStyle(ChatFormatting.RED), true);
                state.warned = true;
            } else {
                String callsign = player != null ? Licences.identity(player) : "NOCALL";
                String base = callsign.length() > 6 ? callsign.substring(0, 6) : callsign;
                AprsPacket packet = new AprsPacket(base + "-" + SSID, level.dimension().location().toString(),
                        position.x, position.y, position.z, speedKmh, course, comment(stack), now);
                state.audio.enqueue(Afsk.render(Afsk.frame(base, SSID, packet.information()), config.volume));
                state.pendingPacket = packet;
            }
            state.lastPosition = position;
            state.lastPositionTime = now;
        }

        // ---- send the packet audio in real time ---- \\
        if (state.audio.isPlaying() || state.sending) {
            state.transmitter.setActive(true);
            List<short[]> frames = state.audio.poll();
            com.codinglitch.simpleradio.central.WorldlyPosition origin = com.codinglitch.simpleradio.central.WorldlyPosition.of(entity.position().toVector3f(), entity.level());
            for (int i = 0; i < frames.size(); i++) {
                RadioMessage message = new RadioMessage(reference, origin, frames.get(i), 1f);
                message.activity = CommonRadioPlugin.analyzeActivity(frames.get(i));
                // the packet is complete (and decodable) with its last frame
                if (!state.audio.isPlaying() && i == frames.size() - 1) message.payload = state.pendingPacket;
                state.transmitter.accept(message);
            }
            state.sending = state.audio.isPlaying();
            if (!state.sending) {
                state.transmitter.endTransmission(reference, null);
                state.transmitter.setActive(false);
                state.pendingPacket = null;
            }
        }
    }

    private static void close(State state) {
        if (state.receiver != null) {
            if (state.receiver.frequency != null) state.receiver.frequency.removeReceiver(state.receiver);
            RadioManager.getInstance().removeRouter(state.receiver);
        }
        if (state.transmitter != null) {
            if (state.transmitter.frequency != null) state.transmitter.frequency.removeTransmitter(state.transmitter);
            RadioManager.getInstance().removeRouter(state.transmitter);
        }
        state.receiver = null;
        state.transmitter = null;
    }

    public static void remove(UUID reference) {
        State state = STATES.remove(reference);
        if (state != null) close(state);
    }

    public static void clear() {
        for (UUID reference : List.copyOf(STATES.keySet())) remove(reference);
    }

    @Override
    public void inactivate(Level level, CompoundTag tag) {
        super.inactivate(level, tag);
        if (!level.isClientSide && tag.hasUUID(REFERENCE)) remove(tag.getUUID(REFERENCE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag tooltip) {
        components.add(Component.translatable(isBeaconing(stack) ? "tooltip.simpleradio.aprs.beacon_on" : "tooltip.simpleradio.aprs.beacon_off")
                .withStyle(isBeaconing(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        super.appendHoverText(stack, level, components, tooltip);
    }
}
