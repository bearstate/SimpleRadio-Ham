package com.codinglitch.simpleradio.ham.gametest;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.Frequencies;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.*;
import com.codinglitch.simpleradio.ham.radio.StationReceiver;
import com.codinglitch.simpleradio.ham.radio.StationTransmitter;
import com.codinglitch.simpleradio.radio.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder(CommonSimpleRadio.ID)
public class HamTests {
    // an empty bedrock floor (the system test structures carry blocks with fixed ids)
    static final String TEMPLATE = "simpleradio:gametest/ham/floor";

    @GameTestGenerator
    public static Collection<TestFunction> generate() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("frequency_math", 20, HamTests::frequencyMath));
        tests.add(test("station_power_and_routers", 200, HamTests::stationPowerAndRouters));
        tests.add(test("repeater_relays", 200, HamTests::repeaterRelays));
        tests.add(test("yagi_directivity", 200, HamTests::yagiDirectivity));
        // the morse audio is generated in real time; the test server can run thousands of ticks a second
        tests.add(test("cw_keying_transmits", 100_000, HamTests::cwKeyingTransmits));
        tests.add(test("handheld_battery", 200, HamTests::handheldBattery));
        tests.add(test("charger_charges", 200, HamTests::chargerCharges));
        tests.add(test("original_blocks_need_power", 200, HamTests::originalBlocksNeedPower));
        tests.add(test("repeater_courtesy_tone", 200, HamTests::repeaterCourtesyTone));
        tests.add(test("repeater_chunk_loading", 200, HamTests::repeaterChunkLoading));
        tests.add(test("audio_quality", 20, HamTests::audioQuality));
        tests.add(test("battery_swap_recipe", 20, HamTests::batterySwapRecipe));
        tests.add(test("charger_charges_battery", 200, HamTests::chargerChargesBattery));
        tests.add(test("coax_dual_band_antenna", 200, HamTests::coaxDualBandAntenna));
        tests.add(test("scanner_stops_on_signal", 600, HamTests::scannerStopsOnSignal));
        tests.add(test("voice_chat_groups_disabled", 300, HamTests::voiceChatGroupsDisabled));

        tests.addAll(PropagationTests.generate());
        tests.addAll(RadioBehaviourTests.generate());
        tests.addAll(FeatureTests.generate());

        if (ModList.get().isLoaded("electrodynamics")) {
            tests.addAll(ElectrodynamicsTests.generate());
        }
        return tests;
    }

    static TestFunction test(String name, int ticks, Consumer<GameTestHelper> function) {
        return test(name, ticks, false, function);
    }

    /**
     * @param realism whether terrain and building/cave losses apply. The game test structures are placed deep
     *                underground in a normal world, so tests of other features switch them off.
     */
    static TestFunction test(String name, int ticks, boolean realism, Consumer<GameTestHelper> function) {
        // own batch per test so radio traffic of one test never reaches another
        return new TestFunction("ham_" + name, "hamtests." + name, TEMPLATE, ticks, 0, true, helper -> {
            setRealism(realism);
            com.codinglitch.simpleradio.ham.radio.PacedAudio.speed = 1;
            // the original system tests switch these off
            com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.power.requirePower = true;
            com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.band_plan.enforce = true;
            com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.band_plan.requireLicense = false;
            BandPlan.licensed = com.codinglitch.simpleradio.ham.licence.Licences::isLicensed;
            function.accept(helper);
        });
    }

    static void setRealism(boolean enabled) {
        var config = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.propagation;
        config.terrainEnabled = enabled;
        config.environmentEnabled = enabled;
        com.codinglitch.simpleradio.radio.propagation.Terrain.clear();
        com.codinglitch.simpleradio.radio.propagation.Environment.clear();
    }

    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> T be(GameTestHelper helper, BlockPos pos) {
        return (T) helper.getBlockEntity(pos);
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    static void power(BlockEntity blockEntity, int joules) {
        blockEntity.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).ifPresent(energy -> energy.receiveEnergy(joules, false));
    }

    static short[] tone() {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 700 / 48000) * 12000);
        return pcm;
    }

    /**
     * A test receiver standing on a real receiver block, so the router garbage collector keeps it.
     */
    static RadioReceiver listener(GameTestHelper helper, Frequency frequency, BlockPos relative, java.util.function.Predicate<com.codinglitch.simpleradio.radio.Message> onMessage) {
        helper.setBlock(relative, SimpleRadioBlocks.RECEIVER.defaultBlockState());
        RadioReceiver receiver = new RadioReceiver(frequency, at(helper, relative.getX() + 0.5, relative.getY() + 0.5, relative.getZ() + 0.5), UUID.randomUUID());
        receiver.frequencingType(SimpleRadioFrequencing.RECEIVER);
        receiver.setAcceptingCriteria(message -> {
            onMessage.test(message);
            return false;
        });
        frequency.addReceiver(receiver);
        return receiver;
    }

    static void remove(RadioReceiver receiver) {
        if (receiver.frequency != null) receiver.frequency.removeReceiver(receiver);
        RadioManager.getInstance().removeRouter(receiver);
    }

    static WorldlyPosition at(GameTestHelper helper, double dx, double dy, double dz) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return WorldlyPosition.of(new Vector3f((float) (origin.getX() + dx), (float) (origin.getY() + dy), (float) (origin.getZ() + dz)), helper.getLevel());
    }

    // ---- Tests ---- \\

    static void frequencyMath(GameTestHelper helper) {
        check(helper, "431.050".equals(HamFrequency.shift("438.650", -7600)), "UHF shift -7.600 MHz");
        check(helper, "145.000".equals(HamFrequency.shift("145.600", -600)), "VHF shift -0.600 MHz");
        check(helper, "145.500".equals(HamFrequency.normalize("145,5")), "normalize comma input");
        check(helper, Integer.valueOf(-7600).equals(HamFrequency.parseShiftKHz("-7.6")), "parse shift");
        check(helper, Integer.valueOf(600).equals(HamFrequency.parseShiftKHz("+0.600")), "parse positive shift");
        check(helper, HamFrequency.bandOf("007.050") == HamFrequency.Band.HF, "HF band");
        check(helper, HamFrequency.bandOf("145.500") == HamFrequency.Band.VHF, "VHF band");
        check(helper, HamFrequency.bandOf("438.650") == HamFrequency.Band.UHF, "UHF band");
        check(helper, HamFrequency.shift("001.000", -7600) == null, "negative frequency rejected");

        CompoundTag tag = new CompoundTag();
        tag.putString("frequency", "438.650");
        tag.putInt(TransceiverItem.SHIFT, -7600);
        check(helper, "431.050".equals(TransceiverItem.getTransmitFrequency(tag)), "handheld transmit frequency");
        helper.succeed();
    }

    static void stationPowerAndRouters(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("145.010", Frequency.Modulation.FREQUENCY, 0, 50);

        Frequencies frequencies = RadioManager.getInstance().frequencies();

        helper.runAtTickTime(5, () -> {
            check(helper, !StationBlock.isOn(helper.getBlockState(pos)), "station must be off without power");
            power(station, 8000);
        });
        helper.runAtTickTime(10, () -> {
            check(helper, StationBlock.isOn(helper.getBlockState(pos)), "station must turn on with power");
            Frequency frequency = frequencies.get("145.010", Frequency.Modulation.FREQUENCY);
            check(helper, frequency != null, "frequency channel exists");
            check(helper, frequency.getReceivers().stream().anyMatch(r -> r instanceof StationReceiver && station.id.equals(r.getReference())), "station receiver registered");
            check(helper, frequency.getTransmitters().stream().anyMatch(t -> t instanceof StationTransmitter && station.id.equals(t.getReference())), "station transmitter registered");
            station.power.setStored(0);
        });
        helper.runAtTickTime(20, () -> {
            check(helper, !StationBlock.isOn(helper.getBlockState(pos)), "station turns off when power runs out");
            Frequency frequency = frequencies.get("145.010", Frequency.Modulation.FREQUENCY);
            check(helper, frequency == null || frequency.getReceivers().stream().noneMatch(r -> station.id.equals(r.getReference())), "routers removed without power");
            helper.succeed();
        });
    }

    static void repeaterRelays(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        repeater.applySettings("439.100", Frequency.Modulation.FREQUENCY, -7600, true);
        power(repeater, 4000);

        helper.runAtTickTime(10, () -> {
            check(helper, "431.500".equals(repeater.getInputFrequency()), "repeater input frequency");

            Frequencies frequencies = RadioManager.getInstance().frequencies();
            Frequency input = frequencies.getOrCreate("431.500", Frequency.Modulation.FREQUENCY);
            Frequency output = frequencies.getOrCreate("439.100", Frequency.Modulation.FREQUENCY);
            WorldlyPosition location = at(helper, 1.5, 2.5, 3.5);
            UUID sender = UUID.randomUUID();

            AtomicBoolean heard = new AtomicBoolean(false);
            AtomicBoolean echoed = new AtomicBoolean(false);
            float[] power = new float[1];

            RadioReceiver other = new RadioReceiver(output, location, UUID.randomUUID());
            other.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
            other.setAcceptingCriteria(message -> {
                heard.set(true);
                power[0] = message.getPower();
                return false;
            });
            output.addReceiver(other);

            RadioReceiver own = new RadioReceiver(output, location, sender);
            own.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
            own.setAcceptingCriteria(message -> {
                echoed.set(true);
                return false;
            });
            output.addReceiver(own);

            RadioTransmitter transmitter = new RadioTransmitter(input, location, sender);
            transmitter.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
            input.addTransmitter(transmitter);

            RadioMessage message = new RadioMessage(sender, location, tone(), 1f);
            message.activity = 1000;
            transmitter.accept(message);

            output.removeReceiver(other);
            output.removeReceiver(own);
            input.removeTransmitter(transmitter);
            RadioManager.getInstance().removeRouter(other);
            RadioManager.getInstance().removeRouter(own);
            RadioManager.getInstance().removeRouter(transmitter);

            check(helper, heard.get(), "other users hear the repeated signal on the output frequency");
            check(helper, power[0] > 0, "repeated signal has power");
            check(helper, !echoed.get(), "the sender does not hear itself echoed");
            helper.succeed();
        });
    }

    static void yagiDirectivity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        helper.setBlock(pos.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState());
        helper.setBlock(pos.above(2), HamContent.YAGI_ANTENNA.get().defaultBlockState().setValue(YagiAntennaBlock.ROTATION, 0));
        StationBlockEntity station = be(helper, pos);
        station.applySettings("144.300", Frequency.Modulation.FREQUENCY, 0, 100);
        power(station, 8000);

        helper.runAtTickTime(30, () -> {
            check(helper, station.yagiPos != null, "yagi detected on the mast");
            check(helper, station.mastHeight == 1, "mast height");

            WorldlyPosition north = at(helper, 1.5, 3, 1.5 - 2000);
            WorldlyPosition south = at(helper, 1.5, 3, 1.5 + 2000);
            double forward = station.transmitPowerTowards(north);
            double backward = station.transmitPowerTowards(south);
            check(helper, forward > backward * 3, "yagi pointing north: forward " + forward + " vs back " + backward);
            check(helper, station.receivePowerFrom(north) > station.receivePowerFrom(south), "directional reception");

            station.rotateYagi(8); // turn to the south
            check(helper, station.transmitPowerTowards(south) > station.transmitPowerTowards(north) * 3, "rotated yagi points south");
            helper.succeed();
        });
    }

    static void cwKeyingTransmits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos keyPos = new BlockPos(2, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        helper.setBlock(keyPos, HamContent.CW_KEY.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("014.060", Frequency.Modulation.AMPLITUDE, 0, 10);
        power(station, 9000);

        // CW audio is generated in real time; the test server may tick faster than 20 TPS,
        // so the steps are driven by the received audio instead of fixed tick numbers.
        AtomicInteger frames = new AtomicInteger();
        AtomicInteger step = new AtomicInteger();
        RadioReceiver[] listener = new RadioReceiver[1];

        helper.onEachTick(() -> {
            BlockPos absoluteKey = helper.absolutePos(keyPos);
            boolean transmitting = StationBlock.isTransmitting(helper.getBlockState(pos));

            switch (step.get()) {
                case 0 -> {
                    if (!StationBlock.isOn(helper.getBlockState(pos))) return;
                    Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate("014.060", Frequency.Modulation.AMPLITUDE);
                    listener[0] = listener(helper, frequency, new BlockPos(40, 2, 40), message -> {
                        if (message.getActivity() > 100) frames.incrementAndGet();
                        return true;
                    });
                    CwKeyBlock.setPressed(helper.getLevel(), absoluteKey, true);
                    step.set(1);
                }
                case 1 -> {
                    check(helper, helper.getBlockState(keyPos).getValue(CwKeyBlock.PRESSED), "key shows pressed");
                    if (transmitting && frames.get() >= 20) {
                        CwKeyBlock.setPressed(helper.getLevel(), absoluteKey, false);
                        step.set(2);
                    }
                }
                case 2 -> {
                    check(helper, !helper.getBlockState(keyPos).getValue(CwKeyBlock.PRESSED), "key released");
                    if (!transmitting) {
                        remove(listener[0]);
                        step.set(3);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    static void handheldBattery(GameTestHelper helper) {
        ItemStack stack = new ItemStack(SimpleRadioItems.TRANSCEIVER);
        TransceiverItem.setBattery(stack, 30);
        check(helper, TransceiverItem.isOperational(stack), "charged handheld works");

        BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
        ItemEntity item = new ItemEntity(helper.getLevel(), absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5, stack);
        item.setDeltaMovement(0, 0, 0);
        item.setPickUpDelay(32767);
        helper.getLevel().addFreshEntity(item);

        helper.succeedWhen(() -> {
            ItemStack current = item.getItem();
            check(helper, TransceiverItem.getBattery(current) == 0, "battery drains to zero");
            check(helper, !TransceiverItem.isOperational(current), "empty handheld stops working");
            item.discard();
        });
    }

    static void chargerCharges(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = be(helper, pos);

        ItemStack stack = new ItemStack(SimpleRadioItems.TRANSCEIVER);
        TransceiverItem.setBattery(stack, 0);
        charger.setStack(stack);

        helper.runAtTickTime(5, () -> {
            check(helper, TransceiverItem.getBattery(charger.getStack()) == 0, "no charging without power");
            power(charger, 2000);
        });

        helper.succeedWhen(() -> {
            check(helper, TransceiverItem.getBattery(charger.getStack()) > 0, "battery charges from the grid");
            check(helper, helper.getBlockState(pos).getValue(ChargerBlock.STATUS) == ChargerBlock.STATUS_CHARGING, "charging status shown");
        });
    }

    static void repeaterCourtesyTone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        repeater.applySettings("438.825", Frequency.Modulation.FREQUENCY, -7600, true);
        power(repeater, 4000);

        AtomicInteger voiceFrames = new AtomicInteger();
        AtomicInteger toneFrames = new AtomicInteger();
        RadioReceiver[] listener = new RadioReceiver[1];

        helper.runAtTickTime(10, () -> {
            Frequencies frequencies = RadioManager.getInstance().frequencies();
            Frequency input = frequencies.getOrCreate("431.225", Frequency.Modulation.FREQUENCY);
            Frequency output = frequencies.getOrCreate("438.825", Frequency.Modulation.FREQUENCY);
            UUID sender = UUID.randomUUID();

            listener[0] = listener(helper, output, new BlockPos(1, 2, 3), message -> {
                if (repeater.id.equals(message.getOwner())) toneFrames.incrementAndGet();
                else voiceFrames.incrementAndGet();
                return true;
            });

            RadioTransmitter transmitter = new RadioTransmitter(input, at(helper, 3.5, 2.5, 3.5), sender);
            transmitter.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
            input.addTransmitter(transmitter);
            for (int i = 0; i < 3; i++) transmitter.accept(new RadioMessage(sender, transmitter.getLocation(), tone(), 1f));
            input.removeTransmitter(transmitter);
            RadioManager.getInstance().removeRouter(transmitter);
        });

        helper.runAtTickTime(14, () -> check(helper, toneFrames.get() == 0, "no courtesy tone while the input is still active"));

        helper.runAtTickTime(40, () -> {
            remove(listener[0]);
            check(helper, voiceFrames.get() == 3, "voice relayed (" + voiceFrames.get() + " frames)");
            check(helper, toneFrames.get() > 0, "courtesy tone sent after the transmission ended");
            helper.succeed();
        });
    }

    static boolean isForceLoaded(GameTestHelper helper, BlockPos relative) {
        net.minecraft.world.level.ForcedChunksSavedData data = helper.getLevel().getDataStorage()
                .get(net.minecraft.world.level.ForcedChunksSavedData::load, "chunks");
        if (data == null) return false;

        BlockPos absolute = helper.absolutePos(relative);
        long chunk = net.minecraft.world.level.ChunkPos.asLong(absolute.getX() >> 4, absolute.getZ() >> 4);
        // TicketOwner hides its fields; its hash is Objects.hash(modId, owner). Only look at this repeater's
        // tickets, repeaters of earlier tests may still stand next to this one.
        int ownerHash = java.util.Objects.hash(CommonSimpleRadio.ID, absolute);
        return data.getBlockForcedChunks().getTickingChunks().entrySet().stream()
                .anyMatch(entry -> entry.getKey().hashCode() == ownerHash && entry.getValue().contains(chunk));
    }

    static void repeaterChunkLoading(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        power(repeater, 4000);

        helper.runAtTickTime(5, () -> {
            check(helper, isForceLoaded(helper, pos), "an enabled repeater keeps its chunk loaded");
            repeater.applySettings(repeater.frequency, repeater.modulation, repeater.shift, false);
        });
        helper.runAtTickTime(10, () -> {
            check(helper, !isForceLoaded(helper, pos), "a disabled repeater releases its chunks");
            repeater.applySettings(repeater.frequency, repeater.modulation, repeater.shift, true);
        });
        helper.runAtTickTime(15, () -> {
            check(helper, isForceLoaded(helper, pos), "re-enabled repeater loads its chunk again");
            helper.setBlock(pos, Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(20, () -> {
            check(helper, !isForceLoaded(helper, pos), "a removed repeater releases its chunks");
            helper.succeed();
        });
    }

    /**
     * Share of a 1 kHz test tone's energy that is still the tone after the radio effect (1 = perfectly clean).
     */
    static double tonePurity(float severity, boolean am) {
        com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect effect = new com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect();
        effect.severity = severity;
        effect.volume = 1;
        effect.amplitudeModulation = am;

        double toneEnergy = 0, totalEnergy = 0;
        long sampleIndex = 0;
        for (int frame = 0; frame < 50; frame++) {
            short[] pcm = new short[960];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(2 * Math.PI * 1000 * (sampleIndex + i) / 48000.0) * 9000);
            short[] out = effect.apply(pcm);
            if (frame < 5) { sampleIndex += 960; continue; } // let the filters settle

            // Goertzel-style projection on the 1 kHz tone
            double re = 0, im = 0, energy = 0;
            for (int i = 0; i < out.length; i++) {
                double phase = 2 * Math.PI * 1000 * (sampleIndex + i) / 48000.0;
                re += out[i] * Math.cos(phase);
                im += out[i] * Math.sin(phase);
                energy += (double) out[i] * out[i];
            }
            toneEnergy += 2 * (re * re + im * im) / out.length;
            totalEnergy += energy;
            sampleIndex += 960;
        }
        return totalEnergy == 0 ? 0 : toneEnergy / totalEnergy;
    }

    /**
     * Level of a test tone after the radio effect relative to its input level.
     */
    static double toneGain(double hz) {
        com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect effect = new com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect();
        effect.severity = 0;
        effect.volume = 1;

        double amplitude = 0;
        long sampleIndex = 0;
        for (int frame = 0; frame < 50; frame++) {
            short[] pcm = new short[960];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(2 * Math.PI * hz * (sampleIndex + i) / 48000.0) * 6000);
            short[] out = effect.apply(pcm);
            if (frame >= 5) {
                double re = 0, im = 0;
                for (int i = 0; i < out.length; i++) {
                    double phase = 2 * Math.PI * hz * (sampleIndex + i) / 48000.0;
                    re += out[i] * Math.cos(phase);
                    im += out[i] * Math.sin(phase);
                }
                amplitude += 2 * Math.sqrt(re * re + im * im) / out.length;
            }
            sampleIndex += 960;
        }
        return amplitude / 45 / 6000;
    }

    static void audioQuality(GameTestHelper helper) {
        // the original effect measured 0.62 at 1 kHz and 0.28 at 2.5 kHz (very muffled)
        double gain1k = toneGain(1000);
        double gain2k5 = toneGain(2500);
        double gain6k = toneGain(6000);
        check(helper, gain1k > 0.9, "1 kHz passes (" + gain1k + ")");
        check(helper, gain2k5 > 0.75, "2.5 kHz passes, speech stays intelligible (" + gain2k5 + ")");
        check(helper, gain6k < 0.4, "above the voice band is cut like a real radio (" + gain6k + ")");

        double strongFm = tonePurity(0, false);
        double strongAm = tonePurity(6, true);
        double weakFm = tonePurity(90, false);
        check(helper, strongFm > 0.97, "strong FM signal is clean (purity " + strongFm + ")");
        check(helper, strongAm > 0.9, "strong AM signal is almost clean (purity " + strongAm + ")");
        check(helper, weakFm < strongFm - 0.1, "weak FM signal is noisy (purity " + weakFm + ")");
        helper.succeed();
    }

    static void batterySwapRecipe(GameTestHelper helper) {
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        net.minecraft.world.inventory.TransientCraftingContainer grid = new net.minecraft.world.inventory.TransientCraftingContainer(
                new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory()), 3, 3);

        ItemStack radio = new ItemStack(SimpleRadioItems.WALKIE_TALKIE);
        radio.getOrCreateTag().putString("frequency", "438.650");
        radio.getOrCreateTag().putInt(TransceiverItem.SHIFT, -7600);
        TransceiverItem.setBattery(radio, 100);

        ItemStack battery = new ItemStack(HamContent.BATTERY.get());
        com.codinglitch.simpleradio.ham.item.BatteryItem.setCharge(battery, TransceiverItem.maxBattery());

        grid.setItem(3, radio);
        grid.setItem(5, battery);

        var recipe = helper.getLevel().getRecipeManager()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, helper.getLevel());
        check(helper, recipe.isPresent() && recipe.get() instanceof com.codinglitch.simpleradio.ham.item.BatterySwapRecipe, "battery swap recipe is found for handheld + battery");

        ItemStack result = recipe.get().assemble(grid, helper.getLevel().registryAccess());
        check(helper, result.getItem() == SimpleRadioItems.WALKIE_TALKIE, "result is the handheld");
        check(helper, TransceiverItem.getBattery(result) == TransceiverItem.maxBattery(), "handheld is fully charged");
        check(helper, "438.650".equals(result.getTag().getString("frequency")) && TransceiverItem.getShift(result.getTag()) == -7600, "settings are kept");

        ItemStack oldBattery = recipe.get().getRemainingItems(grid).get(5);
        check(helper, oldBattery.getItem() == HamContent.BATTERY.get(), "a battery is given back");
        check(helper, com.codinglitch.simpleradio.ham.item.BatteryItem.getCharge(oldBattery) == 100, "the returned battery holds the handheld's old charge");

        grid.setItem(4, new ItemStack(net.minecraft.world.item.Items.STICK));
        check(helper, !recipe.get().matches(grid, helper.getLevel()), "extra items do not match");
        helper.succeed();
    }

    static void chargerChargesBattery(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = be(helper, pos);

        ItemStack battery = new ItemStack(HamContent.BATTERY.get());
        check(helper, com.codinglitch.simpleradio.ham.item.BatteryItem.getCharge(battery) == 0, "new batteries are empty");
        charger.setStack(battery);
        power(charger, 2000);

        helper.succeedWhen(() -> check(helper, com.codinglitch.simpleradio.ham.item.BatteryItem.getCharge(charger.getStack()) > 0, "battery pack charges in the charger"));
    }

    static void coaxDualBandAntenna(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos antenna = new BlockPos(6, 2, 7);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        helper.setBlock(antenna, HamContent.DUAL_BAND_ANTENNA.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("433.500", Frequency.Modulation.FREQUENCY, 0, 50);
        power(station, 8000);

        double[] bare = new double[1];
        helper.runAtTickTime(25, () -> {
            bare[0] = station.forwardRange();
            BlockPos absoluteAntenna = helper.absolutePos(antenna);
            station.coax.connect(absoluteAntenna, 1, Math.sqrt(absoluteAntenna.distSqr(helper.absolutePos(pos))), helper.getLevel(), helper.absolutePos(pos));
            station.onCoaxChanged();
        });
        helper.runAtTickTime(45, () -> {
            check(helper, station.verticalPos != null, "the cable connected vertical is used");
            double withAntenna = station.forwardRange();
            check(helper, withAntenna > bare[0] * 1.5, "a 7 dBi UHF vertical extends the range (" + bare[0] + " -> " + withAntenna + ")");
            check(helper, station.coax.lossDb(433_500) > 0.3, "the cable costs some signal");

            helper.setBlock(antenna, Blocks.AIR.defaultBlockState());
        });
        helper.runAtTickTime(70, () -> {
            check(helper, !station.coax.isConnected(), "the cable is removed when the antenna is gone");
            check(helper, station.verticalPos == null, "back to no antenna");
            helper.succeed();
        });
    }

    static void scannerStopsOnSignal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos transmitterPos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, HamContent.SCANNER.get().defaultBlockState());
        // a real transmitter block keeps the test transmitter valid for the router garbage collector
        helper.setBlock(transmitterPos, SimpleRadioBlocks.TRANSMITTER.defaultBlockState());
        ScannerBlockEntity scanner = be(helper, pos);
        scanner.applySettings("145.000", "146.000", 25, Frequency.Modulation.FREQUENCY);
        power(scanner, 2000);

        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate("145.675", Frequency.Modulation.FREQUENCY);
        UUID sender = UUID.randomUUID();
        RadioTransmitter transmitter = new RadioTransmitter(frequency, at(helper, 3.5, 2.5, 3.5), sender);
        transmitter.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        frequency.addTransmitter(transmitter);

        AtomicInteger step = new AtomicInteger();
        AtomicInteger talkUntil = new AtomicInteger(Integer.MAX_VALUE);
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < talkUntil.get() && tick > 5) {
                transmitter.accept(new RadioMessage(sender, transmitter.getLocation(), tone(), 1f));
            }

            switch (step.get()) {
                case 0 -> {
                    if (tick < 10) return;
                    scanner.control(ScannerBlockEntity.ACTION_TOGGLE);
                    step.set(1);
                }
                case 1 -> {
                    if (scanner.isHolding()) {
                        check(helper, scanner.getCursorKHz() == 145_675, "scanner stopped on the active channel (" + scanner.getCursorKHz() + ")");
                        check(helper, helper.getBlockState(pos).getValue(ScannerBlock.SIGNAL), "signal lamp on");
                        talkUntil.set((int) tick + 10);
                        step.set(2);
                    }
                }
                case 2 -> {
                    if (!scanner.isHolding()) {
                        check(helper, tick > talkUntil.get(), "scanner held the channel while the signal lasted");
                        frequency.removeTransmitter(transmitter);
                        RadioManager.getInstance().removeRouter(transmitter);
                        step.set(3);
                        helper.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    static void voiceChatGroupsDisabled(GameTestHelper helper) {
        var api = CommonRadioPlugin.serverApi;
        check(helper, api != null, "voice chat server is running");

        var config = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.voice_chat;
        boolean previous = config.disableGroups;
        config.disableGroups = false;

        de.maxhenkel.voicechat.api.Group group = api.groupBuilder().setName("hamtest").setPersistent(true)
                .setType(de.maxhenkel.voicechat.api.Group.Type.OPEN).build();
        // note: api.getGroup(id) never returns null (it wraps a missing group), so look at the group list
        java.util.function.BooleanSupplier exists = () -> api.getGroups().stream().anyMatch(g -> g.getId().equals(group.getId()));
        check(helper, exists.getAsBoolean(), "a persistent group exists while groups are allowed");

        config.disableGroups = true;
        helper.onEachTick(() -> {
            if (exists.getAsBoolean()) return; // the sweep runs once a second
            config.disableGroups = previous;
            helper.succeed();
        });
        // restore the config and clean up if the group is never removed (the test then times out)
        helper.runAtTickTime(280, () -> {
            config.disableGroups = previous;
            api.removeGroup(group.getId());
        });
    }

    static void originalBlocksNeedPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, SimpleRadioBlocks.RADIO.defaultBlockState());
        RadioBlockEntity radio = be(helper, pos);
        radio.frequency = RadioManager.getInstance().frequencies().getOrCreate("144.800", Frequency.Modulation.FREQUENCY);

        helper.runAtTickTime(5, () -> {
            check(helper, radio.receiver != null, "radio activated");
            check(helper, !radio.receiver.isActive(), "radio receiver inactive without power");
            power(radio, 500);
        });
        helper.runAtTickTime(8, () -> {
            check(helper, radio.receiver.isActive(), "radio receiver active with power");
            check(helper, radio.speaker != null && radio.speaker.isActive(), "radio speaker active with power");
            helper.setBlock(pos, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }
}
