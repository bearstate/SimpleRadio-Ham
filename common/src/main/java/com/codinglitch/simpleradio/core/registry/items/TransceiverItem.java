package com.codinglitch.simpleradio.core.registry.items;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.SimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.*;
import com.codinglitch.simpleradio.core.Frequencies;
import com.codinglitch.simpleradio.core.central.WorldTicking;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.SimpleRadioSounds;
import com.codinglitch.simpleradio.radio.BandPlan;
import com.codinglitch.simpleradio.radio.CommonRadioPlugin;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.codinglitch.simpleradio.routers.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class TransceiverItem extends Item implements Listening, Speaking, Receiving, Transmitting, WorldTicking {
    public static final String SHIFT = "shift";           // transmit offset in kHz
    public static final String BATTERY = "battery";       // remaining battery in standby ticks
    public static final String SWITCHED_ON = "switched_on";
    public static final String CTCSS_TX = "ctcss_tx";     // tone sent (Hz)
    public static final String CTCSS_RX = "ctcss_rx";     // tone squelch (Hz)
    public static final String MEMORIES = "memories";      // stored channels
    public static final String CHANNEL = "channel";        // recalled channel, -1 for a manually set frequency
    public static final String DUAL_WATCH = "dual_watch";
    public static final String WATCH_FREQUENCY = "watch_frequency";
    public static final String SCAN = "scan";
    public static final int MAX_MEMORIES = 16;
    public static final int MAX_MEMORY_NAME = 12;

    /**
     * Sends the live status (S-meter, scan) of a handheld in a player's hand. Set by the loader.
     */
    public static java.util.function.BiConsumer<net.minecraft.server.level.ServerPlayer, com.codinglitch.simpleradio.radio.HandheldExtras.Status> statusSender = (player, status) -> {};

    /**
     * Opens the settings screen on the client. Set by the loader's client setup.
     */
    public static Consumer<InteractionHand> settingsOpener = hand -> {};

    // Battery drain is accumulated in memory and only written to the stack once a second
    // to avoid syncing the item every tick. Keyed by identity (ItemStack does not override equals).
    private static final Map<ItemStack, float[]> PENDING_DRAIN = new WeakHashMap<>();
    private static final Map<ItemStack, Long> LAST_DRAIN_TICK = new WeakHashMap<>();

    public TransceiverItem(Properties settings) {
        super(settings);
    }

    // ---- Settings helpers ---- \\

    public static int getShift(CompoundTag tag) {
        return tag.contains(SHIFT) ? tag.getInt(SHIFT) : 0;
    }

    /**
     * @return the frequency this handheld transmits on (receive frequency + shift), or null if out of range
     */
    @Nullable
    public static String getTransmitFrequency(CompoundTag tag) {
        String frequency = tag.getString("frequency");
        if (frequency.isEmpty()) return null;

        int shift = getShift(tag);
        if (shift == 0) return frequency;
        return HamFrequency.shift(frequency, shift);
    }

    public static float getToneTx(CompoundTag tag) {
        return tag.getFloat(CTCSS_TX);
    }

    public static float getToneRx(CompoundTag tag) {
        return tag.getFloat(CTCSS_RX);
    }

    protected static void applyTones(CompoundTag tag, Receiver receiver, @Nullable Transmitter transmitter) {
        if (receiver instanceof com.codinglitch.simpleradio.radio.RadioReceiver radioReceiver) radioReceiver.toneSquelch = getToneRx(tag);
        if (transmitter instanceof com.codinglitch.simpleradio.radio.RadioTransmitter radioTransmitter) radioTransmitter.ctcssTone = getToneTx(tag);
    }

    // ---- Memories ---- \\

    /**
     * A stored channel.
     */
    public record Memory(String name, String frequency, Frequency.Modulation modulation, int shift, float toneTx, float toneRx) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("name", name);
            tag.putString("frequency", frequency);
            tag.putString("modulation", modulation.shorthand);
            tag.putInt("shift", shift);
            tag.putFloat("tone_tx", toneTx);
            tag.putFloat("tone_rx", toneRx);
            return tag;
        }

        @Nullable
        public static Memory load(CompoundTag tag) {
            String frequency = HamFrequency.normalize(tag.getString("frequency"));
            Frequency.Modulation modulation = Frequency.modulationOf(tag.getString("modulation"));
            if (frequency == null || modulation == null) return null;
            String name = tag.getString("name");
            if (name.length() > MAX_MEMORY_NAME) name = name.substring(0, MAX_MEMORY_NAME);
            return new Memory(name, frequency, modulation, tag.getInt("shift"),
                    com.codinglitch.simpleradio.radio.Ctcss.normalize(tag.getFloat("tone_tx")), com.codinglitch.simpleradio.radio.Ctcss.normalize(tag.getFloat("tone_rx")));
        }
    }

    public static java.util.List<Memory> getMemories(CompoundTag tag) {
        java.util.List<Memory> memories = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag entry : tag.getList(MEMORIES, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            Memory memory = Memory.load((CompoundTag) entry);
            if (memory != null) memories.add(memory);
            if (memories.size() >= MAX_MEMORIES) break;
        }
        return memories;
    }

    public static void setMemories(CompoundTag tag, java.util.List<Memory> memories) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Memory memory : memories.subList(0, Math.min(MAX_MEMORIES, memories.size()))) list.add(memory.save());
        tag.put(MEMORIES, list);
    }

    public static int getChannel(CompoundTag tag) {
        return tag.contains(CHANNEL) ? tag.getInt(CHANNEL) : -1;
    }

    /**
     * Tunes the handheld to a stored channel.
     */
    public void recallMemory(ItemStack stack, Level level, int index) {
        CompoundTag tag = stack.getOrCreateTag();
        java.util.List<Memory> memories = getMemories(tag);
        if (index < 0 || index >= memories.size()) return;

        Memory memory = memories.get(index);
        applySettings(stack, level, memory.frequency(), memory.modulation(), memory.shift(), isSwitchedOn(stack), memory.toneTx(), memory.toneRx());
        tag.putInt(CHANNEL, index);
    }

    /**
     * Channel up / down through the stored channels.
     */
    public void stepChannel(ItemStack stack, Level level, int delta) {
        int count = getMemories(stack.getOrCreateTag()).size();
        if (count == 0) return;
        int current = getChannel(stack.getOrCreateTag());
        int next = current < 0 ? (delta > 0 ? 0 : count - 1) : Math.floorMod(current + delta, count);
        recallMemory(stack, level, next);
    }

    /**
     * Stored channels, dual watch and scan from the settings screen.
     */
    public void applyExtras(ItemStack stack, java.util.List<Memory> memories, int channel, boolean dualWatch, String watchFrequency, boolean scan) {
        CompoundTag tag = stack.getOrCreateTag();
        setMemories(tag, memories);
        tag.putInt(CHANNEL, channel >= 0 && channel < memories.size() ? channel : -1);
        tag.putBoolean(DUAL_WATCH, dualWatch);
        String watch = HamFrequency.normalize(watchFrequency);
        tag.putString(WATCH_FREQUENCY, watch == null ? "" : watch);
        tag.putBoolean(SCAN, scan);
    }

    /**
     * The frequencing type of this handheld's receivers.
     */
    public com.codinglitch.simpleradio.central.FrequencingType receiverType() {
        return SimpleRadioFrequencing.TRANSCEIVER;
    }

    public static int maxBattery() {
        return (int) Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.handheld.batteryDays * 24000);
    }

    public static int getBattery(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BATTERY)) return maxBattery();
        return Mth.clamp(tag.getInt(BATTERY), 0, maxBattery());
    }

    public static void setBattery(ItemStack stack, int battery) {
        stack.getOrCreateTag().putInt(BATTERY, Mth.clamp(battery, 0, maxBattery()));
    }

    public static boolean isSwitchedOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(SWITCHED_ON) || tag.getBoolean(SWITCHED_ON);
    }

    public static boolean hasBattery(ItemStack stack) {
        return !SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery || getBattery(stack) > 0;
    }

    public static boolean isOperational(ItemStack stack) {
        return isSwitchedOn(stack) && hasBattery(stack);
    }

    /**
     * Applies new settings from the settings screen. Existing routers are torn down and the
     * handheld gets a new reference so both sides rebuild them with the new frequencies.
     */
    public void applySettings(ItemStack stack, Level level, String frequency, Frequency.Modulation modulation, int shift, boolean switchedOn, float toneTx, float toneRx) {
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains("reference")) {
            inactivate(level, tag);
            tag.remove("reference");
        }

        tag.putString("frequency", frequency);
        tag.putString("modulation", modulation.shorthand);
        tag.putInt(SHIFT, shift);
        tag.putBoolean(SWITCHED_ON, switchedOn);
        tag.putFloat(CTCSS_TX, com.codinglitch.simpleradio.radio.Ctcss.normalize(toneTx));
        tag.putFloat(CTCSS_RX, com.codinglitch.simpleradio.radio.Ctcss.normalize(toneRx));
        tag.putBoolean("using", false);
        if (tag.contains("user")) tag.remove("user");
    }

    // ---- Routers ---- \\

    protected void setupRouters(Listener listener, Speaker speaker, Receiver receiver, @Nullable Transmitter transmitter) {
        speaker.setRange(SimpleRadioLibrary.SERVER_CONFIG.transceiver.speakingRange);
        listener.setRange(SimpleRadioLibrary.SERVER_CONFIG.transceiver.listeningRange);
        speaker.setCategory(CommonRadioPlugin.TRANSCEIVERS_CATEGORY);

        receiver.frequencingType(SimpleRadioFrequencing.TRANSCEIVER);

        listener.setLink(this.getClass());
        speaker.setLink(this.getClass());
        receiver.setLink(this.getClass());

        if (transmitter != null) {
            transmitter.frequencingType(SimpleRadioFrequencing.TRANSCEIVER);
            transmitter.setLink(this.getClass());
        }
    }

    public void activate(Level level, ItemStack stack, String frequencyName, Frequency.Modulation modulation, Entity entity, UUID owner) {
        CommonSimpleRadio.info("Activating transceiver with reference {}", owner);

        String transmitFrequency = getTransmitFrequency(stack.getOrCreateTag());

        Listener listener = startListening(entity, owner);
        Speaker speaker = startSpeaking(entity, owner);
        Receiver receiver = startReceiving(entity, frequencyName, modulation, owner);
        Transmitter transmitter = transmitFrequency == null ? null : startTransmitting(entity, transmitFrequency, modulation, owner);

        listener.setOwner(entity);
        speaker.setOwner(entity);
        receiver.setOwner(entity);

        receiver.tryAddRouter(speaker);

        if (transmitter != null) {
            transmitter.setOwner(entity);
            listener.tryAddRouter(transmitter);

            // Set transmitter activation state only if a player isn't holding it
            transmitter.setActive(!(entity instanceof Player));
        }

        this.setupRouters(listener, speaker, receiver, transmitter);
        applyTones(stack.getOrCreateTag(), receiver, transmitter);
    }

    public void inactivate(Level level, String frequencyName, Frequency.Modulation modulation, UUID owner) {
        stopListening(owner, level.isClientSide);
        stopSpeaking(owner, level.isClientSide);
        stopReceiving(frequencyName, modulation, owner, level.isClientSide);
        stopTransmitting(frequencyName, modulation, owner, level.isClientSide);
    }

    public void inactivate(Level level, CompoundTag tag) {
        if (!tag.contains("reference") || !tag.contains("frequency") || !tag.contains("modulation")) return;

        UUID owner = tag.getUUID("reference");
        if (!level.isClientSide) com.codinglitch.simpleradio.radio.HandheldExtras.remove(owner);
        Frequency.Modulation modulation = Frequency.modulationOf(tag.getString("modulation"));
        String frequency = tag.getString("frequency");

        inactivate(level, frequency, modulation, owner);

        String transmitFrequency = getTransmitFrequency(tag);
        if (transmitFrequency != null && !transmitFrequency.equals(frequency))
            stopTransmitting(transmitFrequency, modulation, owner, level.isClientSide);
    }

    @Nullable
    protected Transmitter findTransmitter(Level level, CompoundTag tag) {
        if (!tag.contains("reference") || !tag.contains("frequency") || !tag.contains("modulation")) return null;

        String transmitFrequency = getTransmitFrequency(tag);
        if (transmitFrequency == null) return null;

        Frequency frequency = SimpleRadioApi.getInstance(level.isClientSide).frequencies().get(transmitFrequency, Frequency.modulationOf(tag.getString("modulation")));
        if (frequency == null) return null;
        return frequency.getTransmitter(tag.getUUID("reference"));
    }

    @Nullable
    protected Receiver findReceiver(Level level, CompoundTag tag) {
        if (!tag.contains("reference") || !tag.contains("frequency") || !tag.contains("modulation")) return null;

        Frequency frequency = SimpleRadioApi.getInstance(level.isClientSide).frequencies().get(tag.getString("frequency"), Frequency.modulationOf(tag.getString("modulation")));
        if (frequency == null) return null;
        return frequency.getReceiver(tag.getUUID("reference"));
    }

    public void begin(ItemStack stack, Entity entity) {
        Level level = entity.level();

        // Play the sound
        level.playSound(
                entity, entity.blockPosition(),
                SimpleRadioSounds.RADIO_OPEN,
                SoundSource.PLAYERS,
                1f,1f
        );

        // Get the transmitter and activate it
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            Transmitter transmitter = findTransmitter(level, tag);
            if (transmitter != null) transmitter.setActive(true);

            // half duplex: a handheld hears nothing while it transmits
            Receiver receiver = findReceiver(level, tag);
            if (receiver != null) receiver.setActive(false);
            if (tag.hasUUID("reference")) com.codinglitch.simpleradio.radio.HandheldExtras.setActive(tag.getUUID("reference"), false);
        }

        // required to change the arm pose for accessories
        if (tag != null) tag.putBoolean("using", true);
    }
    public void end(ItemStack stack, Entity entity) {
        Level level = entity.level();

        // Play the sound
        level.playSound(
                entity, entity.blockPosition(),
                SimpleRadioSounds.RADIO_CLOSE,
                SoundSource.PLAYERS,
                1f,1f
        );

        // Add cooldowns
        if (entity instanceof Player player) {
            player.getCooldowns().addCooldown(this, this.getCooldown());
        }

        // Get the transmitter and deactivate it
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            Transmitter transmitter = findTransmitter(level, tag);
            if (transmitter != null) {
                // the carrier drops: receivers close their squelch
                if (!level.isClientSide && transmitter instanceof RadioTransmitter radioTransmitter && tag.hasUUID("reference")) {
                    radioTransmitter.endTransmission(tag.getUUID("reference"), entity.getUUID());
                }
                transmitter.setActive(false);
            }

            Receiver receiver = findReceiver(level, tag);
            if (receiver != null) receiver.setActive(true);
            if (tag.hasUUID("reference")) com.codinglitch.simpleradio.radio.HandheldExtras.setActive(tag.getUUID("reference"), true);
        }

        // required to change the arm pose for accessories
        if (tag != null) tag.putBoolean("using", false);
    }

    public int getCooldown() {
        return SimpleRadioLibrary.SERVER_CONFIG.transceiver.cooldown;
    }

    /**
     * What kind of radio this is for the band plan.
     */
    public BandPlan.Equipment equipment() {
        return BandPlan.Equipment.AMATEUR;
    }

    /**
     * Receive-only radios (direction finders) have no transmitter.
     */
    public boolean canTransmit() {
        return true;
    }

    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        super.verifyTagAfterLoad(tag);

        if (tag.contains("activated"))
            tag.remove("activated");
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        super.onDestroyed(itemEntity);
        inactivate(itemEntity.level(), itemEntity.getItem().getOrCreateTag());
    }

    // ---- Battery ---- \\

    private void drainBattery(ItemStack stack, Entity entity, Level level) {
        if (level.isClientSide) return;
        if (!SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery) return;
        if (!isSwitchedOn(stack)) return;

        // Items in the off hand and armor slots are ticked twice per tick
        long now = level.getGameTime();
        Long last = LAST_DRAIN_TICK.get(stack);
        if (last != null && last == now) return;
        LAST_DRAIN_TICK.put(stack, now);

        CompoundTag tag = stack.getOrCreateTag();
        boolean transmitting = tag.getBoolean("using");

        float[] pending = PENDING_DRAIN.computeIfAbsent(stack, s -> new float[1]);
        pending[0] += transmitting ? SimpleRadioLibrary.SERVER_CONFIG.handheld.transmitDrainMultiplier.floatValue() : 1f;

        if (now % 20 == 0 || pending[0] >= getBattery(stack)) {
            int drained = (int) pending[0];
            pending[0] -= drained;

            int battery = getBattery(stack) - drained;
            setBattery(stack, battery);

            if (battery <= 0) {
                level.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.5f);
                if (entity instanceof LivingEntity living && living.isUsingItem() && living.getUseItem() == stack) {
                    living.releaseUsingItem();
                }
                if (entity instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.simpleradio.battery_empty").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }

    /**
     * Share of the rated range a battery at its current charge still gives: the output drops from full power
     * at half charge to a fifth of it when empty, and range grows with the square root of power.
     */
    public static float batteryRangeFactor(ItemStack stack) {
        if (!SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery) return 1;
        double charge = (double) getBattery(stack) / maxBattery();
        double power = charge >= 0.5 ? 1 : 0.2 + 0.8 * charge / 0.5;
        return (float) Math.sqrt(power);
    }

    private void applyBatteryLevel(ItemStack stack, Entity entity, Level level) {
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        if (findTransmitter(level, tag) instanceof RadioTransmitter transmitter) transmitter.powerMultiplier = batteryRangeFactor(stack);

        // a low battery beeps now and then
        if (SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery && isSwitchedOn(stack) && level.getGameTime() % 600 == 0) {
            int battery = getBattery(stack);
            if (battery > 0 && battery < maxBattery() / 10 && entity instanceof Player player) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.5f, 1.8f);
            }
        }
    }

    // Forge hook (IForgeItem): battery updates must not make the item bob in the hand
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // Forge hook (IForgeItem): battery updates must not interrupt push-to-talk
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery && getBattery(stack) < maxBattery();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getBattery(stack) / maxBattery());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float ratio = (float) getBattery(stack) / maxBattery();
        return Mth.hsvToRgb(ratio / 3f, 1f, 1f);
    }

    // ---- Ticking ---- \\

    public void entityTick(ItemStack stack, Entity entity) {
        if (entity.isRemoved()) return;

        Level level = entity.level();
        CompoundTag tag = stack.getOrCreateTag();

        String frequency = tag.getString("frequency");
        String modulation = tag.getString("modulation");
        tick(stack, level);
        if (frequency.isEmpty() || modulation.isEmpty()) return;

        Frequencies frequencies = SimpleRadioApi.getInstance(level.isClientSide).frequencies();
        if (!frequencies.check(frequency)) {
            String normalized = HamFrequency.normalize(frequency);
            CommonSimpleRadio.info("Invalid frequency {}, replacing with {}", frequency, normalized != null ? normalized : this.getDefaultFrequency());
            frequency = normalized != null ? normalized : this.getDefaultFrequency();
            tag.putString("frequency", frequency);
        }

        drainBattery(stack, entity, level);
        applyBatteryLevel(stack, entity, level);

        // Mode-switch deactivation (i.e. item is dropped)
        Router activeRouter = null;
        if (tag.contains("reference")) {
            activeRouter = SimpleRadioApi.getRouterSided(tag.getUUID("reference"), level.isClientSide);
        }

        if (activeRouter != null) {
            if (activeRouter.getOwner() == null) { // Invalid router, not ours
                activeRouter = null;
            } else if (!activeRouter.getOwner().getUUID().equals(entity.getUUID())) { // Found router does not match ours, discard
                activeRouter = null;
                //if (tag.contains("reference")) tag.remove("reference");
            } else if (tag.contains("reference")) { // Check for a duplicate UUID from a different ItemStack
                Iterable<ItemStack> items = List.of();
                if (entity instanceof Player player) {
                    items = player.getInventory().items;
                } else if (entity instanceof LivingEntity livingEntity) {
                    items = livingEntity.getAllSlots();
                }

                for (ItemStack slotStack : items) {
                    if (slotStack.isEmpty()) continue;
                    if (!slotStack.hasTag()) continue;

                    CompoundTag slotTag = slotStack.getTag();
                    if (slotTag == null) continue;
                    if (!slotTag.contains("reference")) continue;
                    if (!slotTag.getUUID("reference").equals(tag.getUUID("reference"))) continue;

                    if (!slotStack.equals(stack)) {
                        tag.remove("reference");
                        break;
                    }
                }

                if (!tag.contains("reference")) activeRouter = null;
            }
        }

        // Switched off or out of battery: tear down the routers and stay silent
        if (!isOperational(stack)) {
            if (activeRouter != null) inactivate(level, tag);
            return;
        }

        // Transceiver activation
        UUID activationUUID = null;
        if (entity.level().isClientSide) {

            if (tag.contains("reference") && activeRouter == null) {
                activationUUID = tag.getUUID("reference");
            }

        } else {
            if (activeRouter != null) {
                tickExtras(stack, entity, (net.minecraft.server.level.ServerLevel) level, tag);
                return;
            }

            if (!tag.contains("reference")) {
                activationUUID = UUID.randomUUID();
                tag.putUUID("reference", activationUUID);
            } else {
                activationUUID = tag.getUUID("reference");
            }
        }

        if (activationUUID == null) return;

        CommonSimpleRadio.debug("Activated transceiver using UUID {}!", activationUUID);

        frequency = tag.getString("frequency");
        modulation = tag.getString("modulation");
        activate(level, stack, frequency, Frequency.modulationOf(modulation), entity, activationUUID);
    }

    private void tickExtras(ItemStack stack, Entity entity, net.minecraft.server.level.ServerLevel level, CompoundTag tag) {
        if (!tag.hasUUID("reference")) return;
        UUID reference = tag.getUUID("reference");
        com.codinglitch.simpleradio.radio.HandheldExtras.tick(this, stack, entity, level, reference, receiverType());

        // the S-meter of a handheld in the hand
        if (level.getGameTime() % 4 == 0 && entity instanceof net.minecraft.server.level.ServerPlayer player
                && (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) {
            statusSender.accept(player, com.codinglitch.simpleradio.radio.HandheldExtras.status(stack, reference));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (selected) return;

        entityTick(stack, entity);
    }

    @Override
    public void worldTick(ItemEntity item, Level level) {
        entityTick(item.getItem(), item);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag tooltip) {
        CompoundTag tag = stack.getOrCreateTag();

        if (tag.contains("frequency")) {
            String modulation = tag.getString("modulation");
            components.add(Component.translatable("tooltip.simpleradio.handheld.rx",
                    HamFrequency.display(tag.getString("frequency")), modulation).withStyle(ChatFormatting.GRAY));

            int shift = getShift(tag);
            if (shift != 0) {
                String transmitFrequency = getTransmitFrequency(tag);
                components.add(Component.translatable("tooltip.simpleradio.handheld.tx",
                        transmitFrequency == null ? "---" : HamFrequency.display(transmitFrequency),
                        HamFrequency.displayShift(shift)).withStyle(ChatFormatting.GRAY));
            }
        }

        if (!isSwitchedOn(stack)) {
            components.add(Component.translatable("tooltip.simpleradio.handheld.off").withStyle(ChatFormatting.DARK_RED));
        }
        if (SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery) {
            int percent = Math.round(100f * getBattery(stack) / maxBattery());
            components.add(Component.translatable("tooltip.simpleradio.handheld.battery", percent)
                    .withStyle(percent > 20 ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        }

        components.add(Component.translatable("tooltip.simpleradio.handheld.settings_hint").withStyle(ChatFormatting.DARK_GRAY));

        if (Screen.hasShiftDown() && tag.contains("user")) {
            components.add(Component.translatable(
                    "tooltip.simpleradio.receiver_user",
                    tag.getUUID("user")
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        super.appendHoverText(stack, level, components, tooltip);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Sneak + use opens the frequency / shift settings
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) settingsOpener.accept(hand);
            return InteractionResultHolder.success(stack);
        }

        if (!isOperational(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable(isSwitchedOn(stack) ?
                        "message.simpleradio.battery_empty" : "message.simpleradio.handheld_off").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // band plan and licence: amateur radios on amateur bands with a licence, PMR walkie talkies on PMR446
        String transmitFrequency = getTransmitFrequency(stack.getOrCreateTag());
        String denial = transmitFrequency == null ? "message.simpleradio.invalid_frequency" : BandPlan.denial(player, transmitFrequency, equipment());
        if (denial != null) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(denial).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        begin(stack, player);

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
        end(stack, user);

        super.releaseUsing(stack, level, user, remainingUseTicks);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }
}
