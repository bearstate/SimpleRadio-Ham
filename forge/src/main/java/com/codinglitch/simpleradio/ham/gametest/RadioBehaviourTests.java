package com.codinglitch.simpleradio.ham.gametest;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.core.registry.filters.RadioAudioEffect;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.CwKeyBlock;
import com.codinglitch.simpleradio.ham.block.RepeaterBlockEntity;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.radio.Swr;
import com.codinglitch.simpleradio.radio.*;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.codinglitch.simpleradio.ham.gametest.HamTests.*;

/**
 * CTCSS, off-frequency reception, the end of a transmission, the capture effect, SSB, the repeater's
 * time-out / ID / link, SWR and the antenna tuner, the band plan and weak batteries.
 */
public class RadioBehaviourTests {
    static List<TestFunction> generate() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("ctcss_tone_squelch", 20, RadioBehaviourTests::ctcssToneSquelch));
        tests.add(test("off_frequency_reception", 20, RadioBehaviourTests::offFrequencyReception));
        tests.add(test("squelch_tail", 40, RadioBehaviourTests::squelchTail));
        tests.add(test("capture_effect", 20, RadioBehaviourTests::captureEffect));
        tests.add(test("ssb_pitch_shift", 20, RadioBehaviourTests::ssbPitchShift));
        tests.add(test("hf_handhelds_and_ssb", 20, RadioBehaviourTests::hfHandheldsAndSsb));
        tests.add(test("repeater_access_tone", 60, RadioBehaviourTests::repeaterAccessTone));
        tests.add(test("repeater_time_out", 400, RadioBehaviourTests::repeaterTimeOut));
        tests.add(test("repeater_morse_id", 100_000, RadioBehaviourTests::repeaterMorseId));
        tests.add(test("repeater_link", 100_000, RadioBehaviourTests::repeaterLink));
        tests.add(test("station_swr_and_tuner", 100, RadioBehaviourTests::stationSwrAndTuner));
        tests.add(test("station_overheats", 100_000, RadioBehaviourTests::stationOverheats));
        tests.add(test("band_plan", 20, RadioBehaviourTests::bandPlan));
        tests.add(test("battery_sag", 20, RadioBehaviourTests::batterySag));
        return tests;
    }

    /**
     * Counts what a receiver lets through (after its tone squelch), like a speaker behind it would.
     */
    static void sink(RadioReceiver receiver, Consumer<RadioMessage> onMessage) {
        RadioRouter sink = new RadioRouter(UUID.randomUUID()) {
            @Override
            public void accept(Message message) {
                onMessage.accept((RadioMessage) message);
            }
        };
        sink.position = receiver.getLocation();
        receiver.addRouter(sink);
    }

    /**
     * A receiver standing on a real receiver block (the router garbage collector keeps it) without filters.
     */
    static RadioReceiver receiver(GameTestHelper helper, Frequency frequency, BlockPos relative) {
        helper.setBlock(relative, SimpleRadioBlocks.RECEIVER.defaultBlockState());
        RadioReceiver receiver = new RadioReceiver(frequency, at(helper, relative.getX() + 0.5, relative.getY() + 0.5, relative.getZ() + 0.5), UUID.randomUUID());
        receiver.frequencingType(SimpleRadioFrequencing.RECEIVER);
        frequency.addReceiver(receiver);
        return receiver;
    }

    static RadioTransmitter transmitter(GameTestHelper helper, Frequency frequency, BlockPos relative, UUID sender) {
        helper.setBlock(relative, SimpleRadioBlocks.TRANSMITTER.defaultBlockState());
        RadioTransmitter transmitter = new RadioTransmitter(frequency, at(helper, relative.getX() + 0.5, relative.getY() + 0.5, relative.getZ() + 0.5), sender);
        transmitter.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        frequency.addTransmitter(transmitter);
        return transmitter;
    }

    static void remove(RadioTransmitter transmitter) {
        if (transmitter.frequency != null) transmitter.frequency.removeTransmitter(transmitter);
        RadioManager.getInstance().removeRouter(transmitter);
    }

    static void send(RadioTransmitter transmitter, UUID sender) {
        transmitter.accept(new RadioMessage(sender, transmitter.getLocation(), HamTests.tone(), 1f));
    }

    static Frequency frequency(String name, Frequency.Modulation modulation) {
        return RadioManager.getInstance().frequencies().getOrCreate(name, modulation);
    }

    // ---- Receivers ---- \\

    static void ctcssToneSquelch(GameTestHelper helper) {
        Frequency channel = frequency("145.330", Frequency.Modulation.FREQUENCY);
        UUID sender = UUID.randomUUID();
        RadioTransmitter transmitter = transmitter(helper, channel, new BlockPos(1, 2, 1), sender);

        AtomicInteger heard = new AtomicInteger();
        AtomicReference<Float> tone = new AtomicReference<>(0f);
        RadioReceiver receiver = receiver(helper, channel, new BlockPos(1, 2, 6));
        receiver.toneSquelch = 88.5f;
        sink(receiver, message -> {
            heard.incrementAndGet();
            tone.set(message.ctcss);
        });

        send(transmitter, sender);
        check(helper, heard.get() == 0, "tone squelch stays closed without the tone");
        transmitter.ctcssTone = 100.0f;
        send(transmitter, sender);
        check(helper, heard.get() == 0, "and with the wrong tone");
        transmitter.ctcssTone = 88.5f;
        send(transmitter, sender);
        check(helper, heard.get() == 1 && tone.get() == 88.5f, "it opens for 88.5 Hz");
        check(helper, !Float.isNaN(receiver.lastSignalDb), "the S-meter still showed the carriers");

        remove(transmitter);
        HamTests.remove(receiver);
        helper.succeed();
    }

    static void offFrequencyReception(GameTestHelper helper) {
        UUID sender = UUID.randomUUID();
        RadioTransmitter transmitter = transmitter(helper, frequency("145.505", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 1), sender);

        List<RadioMessage> exact = new ArrayList<>(), near = new ArrayList<>(), far = new ArrayList<>();
        RadioReceiver onChannel = receiver(helper, frequency("145.505", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 5));
        RadioReceiver fiveOff = receiver(helper, frequency("145.500", Frequency.Modulation.FREQUENCY), new BlockPos(3, 2, 5));
        RadioReceiver tenOff = receiver(helper, frequency("145.515", Frequency.Modulation.FREQUENCY), new BlockPos(5, 2, 5));
        sink(onChannel, exact::add);
        sink(fiveOff, near::add);
        sink(tenOff, far::add);

        send(transmitter, sender);
        remove(transmitter);
        for (RadioReceiver receiver : List.of(onChannel, fiveOff, tenOff)) HamTests.remove(receiver);

        check(helper, exact.size() == 1 && exact.get(0).offsetHz == 0, "heard cleanly on the channel");
        check(helper, near.size() == 1 && near.get(0).offsetHz == 5000, "5 kHz off it is still heard, detuned (" + (near.isEmpty() ? "-" : near.get(0).offsetHz) + " Hz)");
        check(helper, near.get(0).signalDb < exact.get(0).signalDb - 20, "and much weaker (" + near.get(0).signalDb + " vs " + exact.get(0).signalDb + " dB)");
        check(helper, far.isEmpty(), "10 kHz off it is not heard");
        helper.succeed();
    }

    static void squelchTail(GameTestHelper helper) {
        Frequency fm = frequency("145.350", Frequency.Modulation.FREQUENCY);
        Frequency am = frequency("145.350", Frequency.Modulation.AMPLITUDE);
        UUID sender = UUID.randomUUID();

        RadioTransmitter fmTransmitter = transmitter(helper, fm, new BlockPos(1, 2, 1), sender);
        RadioTransmitter amTransmitter = transmitter(helper, am, new BlockPos(3, 2, 1), sender);

        RadioSpeaker[] speakers = new RadioSpeaker[2];
        RadioReceiver[] receivers = new RadioReceiver[2];
        Frequency[] frequencies = {fm, am};
        for (int i = 0; i < 2; i++) {
            BlockPos pos = new BlockPos(1 + i * 2, 2, 6);
            receivers[i] = receiver(helper, frequencies[i], pos);
            helper.setBlock(pos.above(), SimpleRadioBlocks.SPEAKER.defaultBlockState());
            speakers[i] = new RadioSpeaker(at(helper, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5), UUID.randomUUID());
            receivers[i].addRouter(speakers[i]);
        }

        RadioTransmitter[] transmitters = {fmTransmitter, amTransmitter};
        int[] tails = new int[2];
        for (int i = 0; i < 2; i++) {
            for (int frame = 0; frame < 3; frame++) send(transmitters[i], sender);
            int before = speakers[i].queuedFrames();
            transmitters[i].endTransmission(sender, sender);
            tails[i] = speakers[i].queuedFrames() - before;
        }

        for (RadioTransmitter transmitter : transmitters) remove(transmitter);
        for (RadioReceiver receiver : receivers) HamTests.remove(receiver);
        for (RadioSpeaker speaker : speakers) RadioManager.getInstance().removeRouter(speaker);

        check(helper, tails[0] >= 6, "an FM receiver plays a burst of noise when the carrier drops (" + tails[0] + " frames)");
        check(helper, tails[1] <= 0, "AM has no squelch tail (" + tails[1] + ")");
        helper.succeed();
    }

    static short[] tone(double hz, double amplitude) {
        short[] frame = new short[960];
        for (int i = 0; i < frame.length; i++) frame[i] = (short) (Math.sin(2 * Math.PI * hz * i / 48000.0) * amplitude * 32767);
        return frame;
    }

    static void captureEffect(GameTestHelper helper) {
        ChannelInterference interference = new ChannelInterference();
        short[] strong = tone(700, 0.3), weak = tone(1100, 0.3);

        short[] captured = interference.mix(List.of(
                new ChannelInterference.Input(strong, 30, Frequency.Modulation.FREQUENCY, 0),
                new ChannelInterference.Input(weak, 12, Frequency.Modulation.FREQUENCY, 0)));
        check(helper, java.util.Arrays.equals(captured, strong), "FM: the stronger station captures the channel");

        short[] doubled = interference.mix(List.of(
                new ChannelInterference.Input(strong, 20, Frequency.Modulation.FREQUENCY, 0),
                new ChannelInterference.Input(weak, 18, Frequency.Modulation.FREQUENCY, 0)));
        check(helper, !java.util.Arrays.equals(doubled, strong) && !java.util.Arrays.equals(doubled, weak), "FM: two equal stations garble each other");

        short[] am = interference.mix(List.of(
                new ChannelInterference.Input(strong, 30, Frequency.Modulation.AMPLITUDE, 400),
                new ChannelInterference.Input(weak, 12, Frequency.Modulation.AMPLITUDE, 900)));
        short[] plain = CommonRadioPlugin.combineAudio(List.of(strong, weak));
        check(helper, !java.util.Arrays.equals(am, plain), "AM: both are heard with a heterodyne whistle");
        helper.succeed();
    }

    // energy of one frequency in a signal (Goertzel)
    static double energyAt(short[] samples, double hz) {
        double coefficient = 2 * Math.cos(2 * Math.PI * hz / 48000.0), s1 = 0, s2 = 0;
        for (short sample : samples) {
            double s0 = sample + coefficient * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coefficient * s1 * s2;
    }

    static short[] throughEffect(RadioAudioEffect effect, double hz) {
        short[] last = null;
        long index = 0;
        for (int frame = 0; frame < 20; frame++) {
            short[] pcm = new short[960];
            for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(2 * Math.PI * hz * (index + i) / 48000.0) * 9000);
            index += 960;
            last = effect.apply(pcm);
        }
        return last;
    }

    static void ssbPitchShift(GameTestHelper helper) {
        RadioAudioEffect tuned = new RadioAudioEffect();
        tuned.singleSideband = true;
        tuned.volume = 1;
        short[] clean = throughEffect(tuned, 700);
        check(helper, energyAt(clean, 700) > 20 * energyAt(clean, 1700), "tuned SSB keeps the pitch");

        RadioAudioEffect detuned = new RadioAudioEffect();
        detuned.singleSideband = true;
        detuned.volume = 1;
        detuned.offsetHz = 1000;
        short[] shifted = throughEffect(detuned, 700);
        check(helper, energyAt(shifted, 1700) > 20 * energyAt(shifted, 700), "1 kHz off tune the 700 Hz tone comes out at 1700 Hz");
        helper.succeed();
    }

    static void hfHandheldsAndSsb(GameTestHelper helper) {
        setRealism(true);
        Frequency hf = frequency("007.050", Frequency.Modulation.FREQUENCY);
        RadioTransmitter handheld = new RadioTransmitter(hf, at(helper, 1.5, 300, 1.5), UUID.randomUUID());
        handheld.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        RadioReceiver other = new RadioReceiver(hf, at(helper, 1.5, 300, 101.5), UUID.randomUUID());
        other.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        double handheldLoss = RadioLink.compute(handheld, other, hf).lossDb();

        Frequency ssb = frequency("145.050", Frequency.Modulation.SINGLE_SIDEBAND);
        RadioTransmitter station = new RadioTransmitter(ssb, at(helper, 1.5, 300, 1.5), UUID.randomUUID());
        station.frequencingType(SimpleRadioFrequencing.STATION);
        RadioReceiver receiver = new RadioReceiver(ssb, at(helper, 1.5, 300, 101.5), UUID.randomUUID());
        receiver.frequencingType(SimpleRadioFrequencing.STATION);
        double ssbLoss = RadioLink.compute(station, receiver, ssb).lossDb();
        setRealism(false);

        check(helper, handheldLoss >= 39, "two handhelds' short antennas lose ~40 dB on HF (" + handheldLoss + ")");
        check(helper, ssbLoss <= -5, "SSB gets through ~6 dB better than FM (" + ssbLoss + ")");
        helper.succeed();
    }

    // ---- Repeater ---- \\

    static void repeaterAccessTone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        repeater.applySettings("439.200", Frequency.Modulation.FREQUENCY, -7600, true, 88.5f, 100.0f, "", 0);
        power(repeater, 4000);

        helper.runAtTickTime(10, () -> {
            UUID sender = UUID.randomUUID();
            RadioTransmitter transmitter = transmitter(helper, frequency("431.600", Frequency.Modulation.FREQUENCY), new BlockPos(3, 2, 3), sender);
            List<RadioMessage> heard = new ArrayList<>();
            RadioReceiver output = receiver(helper, frequency("439.200", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 5));
            sink(output, message -> {
                if (!repeater.id.equals(message.getOwner())) heard.add(message);
            });

            send(transmitter, sender);
            int withoutTone = heard.size();
            transmitter.ctcssTone = 88.5f;
            send(transmitter, sender);

            remove(transmitter);
            HamTests.remove(output);
            check(helper, withoutTone == 0, "the repeater ignores users without its access tone");
            check(helper, heard.size() == 1, "it relays users sending 88.5 Hz");
            check(helper, heard.get(0).ctcss == 100.0f, "its output carries its own tone (" + heard.get(0).ctcss + ")");
            helper.succeed();
        });
    }

    static void repeaterTimeOut(GameTestHelper helper) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.repeater;
        int previous = config.timeoutSeconds;
        config.timeoutSeconds = 1;

        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        repeater.applySettings("438.950", Frequency.Modulation.FREQUENCY, -7600, true);
        power(repeater, 5000);

        UUID sender = UUID.randomUUID();
        AtomicInteger relayed = new AtomicInteger();
        RadioTransmitter[] transmitter = new RadioTransmitter[1];
        RadioReceiver[] output = new RadioReceiver[1];
        AtomicInteger relayedWhenTimedOut = new AtomicInteger(-1);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                transmitter[0] = transmitter(helper, frequency("431.350", Frequency.Modulation.FREQUENCY), new BlockPos(3, 2, 3), sender);
                output[0] = receiver(helper, frequency("438.950", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 5));
                sink(output[0], message -> {
                    if (!repeater.id.equals(message.getOwner())) relayed.incrementAndGet();
                });
            }
            // a user keeps talking for 60 ticks
            if (tick > 10 && tick <= 70) send(transmitter[0], sender);
            if (tick == 70) {
                check(helper, repeater.isTimedOut(), "the time-out timer tripped");
                relayedWhenTimedOut.set(relayed.get());
            }
            if (tick == 100) {
                config.timeoutSeconds = previous;
                remove(transmitter[0]);
                HamTests.remove(output[0]);
                check(helper, relayed.get() >= 18 && relayed.get() <= 24, "about one second was relayed (" + relayed.get() + " frames)");
                check(helper, !repeater.isTimedOut(), "after a pause the repeater is available again");
                helper.succeed();
            }
        });
    }

    static void repeaterMorseId(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity repeater = be(helper, pos);
        repeater.applySettings("438.975", Frequency.Modulation.FREQUENCY, -7600, true, 0, 0, "ta1zz", 0);
        power(repeater, 5000);
        check(helper, repeater.callsign.equals("TA1ZZ"), "the call sign is cleaned up");

        UUID sender = UUID.randomUUID();
        AtomicInteger own = new AtomicInteger();
        AtomicInteger ended = new AtomicInteger();
        RadioReceiver[] output = new RadioReceiver[1];

        helper.onEachTick(() -> {
            // the audio is played out in real time, the test server ticks much faster: keep it powered
            HamTests.power(repeater, 50);
            long tick = helper.getTick();
            if (tick == 10) {
                RadioTransmitter transmitter = transmitter(helper, frequency("431.375", Frequency.Modulation.FREQUENCY), new BlockPos(3, 2, 3), sender);
                output[0] = receiver(helper, frequency("438.975", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 5));
                sink(output[0], message -> {
                    if (!repeater.id.equals(message.getOwner())) return;
                    if (message.endOfTransmission) ended.incrementAndGet();
                    else own.incrementAndGet();
                });
                for (int i = 0; i < 3; i++) send(transmitter, sender);
                remove(transmitter);
            }
            // courtesy beep (~12 frames) + pause + "TA1ZZ" at 20 WPM (~190 frames), then the carrier drops
            if (tick > 10 && ended.get() > 0) {
                HamTests.remove(output[0]);
                check(helper, own.get() > 150, "the repeater sent its call sign in morse (" + own.get() + " frames)");
                helper.succeed();
            }
        });
    }

    static void repeaterLink(GameTestHelper helper) {
        BlockPos posA = new BlockPos(1, 2, 1), posB = new BlockPos(5, 2, 1);
        helper.setBlock(posA, HamContent.REPEATER.get().defaultBlockState());
        helper.setBlock(posB, HamContent.REPEATER.get().defaultBlockState());
        RepeaterBlockEntity a = be(helper, posA), b = be(helper, posB);
        a.applySettings("438.600", Frequency.Modulation.FREQUENCY, -7600, true, 0, 0, "", 4242);
        b.applySettings("439.050", Frequency.Modulation.FREQUENCY, -7600, true, 0, 0, "", 4242);
        power(a, 5000);
        power(b, 5000);

        UUID sender = UUID.randomUUID();
        AtomicInteger heardOnB = new AtomicInteger();
        RadioReceiver[] output = new RadioReceiver[1];

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick == 10) {
                check(helper, com.codinglitch.simpleradio.ham.radio.RepeaterLinks.members(4242) == 2, "both repeaters joined link 4242");
                output[0] = receiver(helper, frequency("439.050", Frequency.Modulation.FREQUENCY), new BlockPos(5, 2, 5));
                sink(output[0], message -> {
                    if (sender.equals(message.getRealOwner())) heardOnB.incrementAndGet();
                });

                // a user of repeater A: repeater B does not listen on this frequency
                RadioTransmitter transmitter = transmitter(helper, frequency("431.000", Frequency.Modulation.FREQUENCY), new BlockPos(1, 2, 3), sender);
                for (int i = 0; i < 3; i++) send(transmitter, sender);
                remove(transmitter);
            }
            if (tick > 10 && heardOnB.get() >= 3) {
                HamTests.remove(output[0]);
                helper.succeed();
            }
        });
    }

    // ---- Station ---- \\

    static void stationSwrAndTuner(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("007.050", Frequency.Modulation.SINGLE_SIDEBAND, 0, 100);
        power(station, 9000);

        helper.runAtTickTime(25, () -> {
            check(helper, station.antennaKind() == Swr.Antenna.NONE && station.radioSwr(7050) >= 19, "without an antenna nearly everything is reflected");
            check(helper, station.outputShare(7050) < 0.1, "so the station puts almost nothing out (" + station.outputShare(7050) + ")");
            // antenna blocks stand at most 9 high without guying
            for (int i = 1; i <= 9; i++) helper.setBlock(pos.above(i), SimpleRadioBlocks.ANTENNA.defaultBlockState());
            station.markAntennaDirty();
        });
        helper.runAtTickTime(50, () -> {
            double forty = station.radioSwr(7050), twenty = station.radioSwr(14100);
            check(helper, station.mastHeight == 9 && forty < 2.5, "a 9 block mast is close to a quarter wave on 40 m (SWR " + forty + ")");
            check(helper, twenty > 3, "but a poor match on 20 m (SWR " + twenty + ")");
            check(helper, station.outputShare(14100) < 0.5, "the station turns its power down on 20 m (" + station.outputShare(14100) + ")");
            helper.setBlock(pos.east(), HamContent.ANTENNA_TUNER.get().defaultBlockState());
        });
        helper.runAtTickTime(75, () -> {
            check(helper, station.hasTuner(), "the tuner next to the station is found");
            check(helper, station.radioSwr(14100) < 1.2, "the tuner matches the mast on 20 m (SWR " + station.radioSwr(14100) + ")");
            check(helper, station.outputShare(14100) > 0.95, "full power again");
            helper.succeed();
        });
    }

    static void stationOverheats(GameTestHelper helper) {
        var config = SimpleRadioLibrary.SERVER_CONFIG.swr;
        double previous = config.heatSeconds;
        config.heatSeconds = 1d;

        BlockPos pos = new BlockPos(1, 2, 1), key = new BlockPos(2, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        helper.setBlock(key, HamContent.CW_KEY.get().defaultBlockState());
        // an 8 block mast on 40 m: SWR about 3, just below where the station would turn its power down
        for (int i = 1; i <= 8; i++) helper.setBlock(pos.above(i), SimpleRadioBlocks.ANTENNA.defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("007.050", Frequency.Modulation.AMPLITUDE, 0, 100);
        power(station, 10_000);

        AtomicInteger step = new AtomicInteger();
        helper.onEachTick(() -> {
            HamTests.power(station, 200);
            long tick = helper.getTick();
            if (step.get() == 0 && tick > 25) {
                check(helper, station.radioSwr(7050) > 2.5 && station.radioSwr(7050) < 3, "SWR just below 3 (" + station.radioSwr(7050) + ")");
                CwKeyBlock.setPressed(helper.getLevel(), helper.absolutePos(key), true);
                step.set(1);
            } else if (step.get() == 1 && station.isOverheated()) {
                CwKeyBlock.setPressed(helper.getLevel(), helper.absolutePos(key), false);
                check(helper, station.getTemperature() >= 1, "the power amplifier got too hot");
                step.set(2);
            } else if (step.get() == 2 && !station.isOverheated()) {
                config.heatSeconds = previous;
                check(helper, station.getTemperature() < 0.6, "it cooled down before transmitting again");
                helper.succeed();
            }
        });
    }

    // ---- Rules ---- \\

    static void bandPlan(GameTestHelper helper) {
        var previous = BandPlan.licensed;
        var config = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.band_plan;
        boolean previousRequire = config.requireLicense;
        Player player = helper.makeMockPlayer();
        try {
            // by default no licence is needed
            config.requireLicense = false;
            BandPlan.licensed = p -> false;
            check(helper, BandPlan.denial(player, "145.500", BandPlan.Equipment.AMATEUR) == null, "without the licence rule anyone may transmit on 2 m");
            check(helper, "message.simpleradio.denied.out_of_band".equals(BandPlan.denial(player, "100.000", BandPlan.Equipment.AMATEUR)), "the band plan still applies");

            config.requireLicense = true;
            check(helper, "message.simpleradio.denied.license".equals(BandPlan.denial(player, "145.500", BandPlan.Equipment.AMATEUR)), "amateur bands need a licence");
            check(helper, BandPlan.denial(player, BandPlan.pmrChannelFrequency(3), BandPlan.Equipment.PMR) == null, "PMR446 is licence free with a PMR radio");

            BandPlan.licensed = p -> true;
            check(helper, BandPlan.denial(player, "145.500", BandPlan.Equipment.AMATEUR) == null, "licensed amateurs may transmit on 2 m");
            check(helper, BandPlan.denial(player, "007.100", BandPlan.Equipment.AMATEUR) == null, "and on 40 m");
            check(helper, "message.simpleradio.denied.out_of_band".equals(BandPlan.denial(player, "100.000", BandPlan.Equipment.AMATEUR)), "not outside the amateur bands");
            check(helper, "message.simpleradio.denied.out_of_band".equals(BandPlan.denial(player, BandPlan.pmrChannelFrequency(1), BandPlan.Equipment.AMATEUR)), "an amateur radio may not use PMR446");
            check(helper, "message.simpleradio.denied.pmr_only".equals(BandPlan.denial(player, "145.500", BandPlan.Equipment.PMR)), "a PMR walkie talkie only on PMR446");

            check(helper, "446.006".equals(BandPlan.pmrChannelFrequency(1)), "PMR channel 1 is 446.00625 MHz (" + BandPlan.pmrChannelFrequency(1) + ")");
            check(helper, "446.194".equals(BandPlan.pmrChannelFrequency(16)), "PMR channel 16 is 446.19375 MHz (" + BandPlan.pmrChannelFrequency(16) + ")");
            check(helper, BandPlan.pmrChannelOf("446.019") == 2, "446.019 is channel 2");
        } finally {
            BandPlan.licensed = previous;
            config.requireLicense = previousRequire;
        }
        helper.succeed();
    }

    static void batterySag(GameTestHelper helper) {
        ItemStack stack = new ItemStack(SimpleRadioItems.TRANSCEIVER);
        int max = TransceiverItem.maxBattery();
        TransceiverItem.setBattery(stack, max);
        check(helper, TransceiverItem.batteryRangeFactor(stack) == 1, "a full battery gives full range");
        TransceiverItem.setBattery(stack, max / 2);
        check(helper, TransceiverItem.batteryRangeFactor(stack) == 1, "and still at half charge");
        TransceiverItem.setBattery(stack, max / 4);
        float quarter = TransceiverItem.batteryRangeFactor(stack);
        TransceiverItem.setBattery(stack, 1);
        float empty = TransceiverItem.batteryRangeFactor(stack);
        check(helper, quarter < 1 && quarter > empty, "the output drops as the battery runs down (" + quarter + ")");
        check(helper, Math.abs(empty - Math.sqrt(0.2)) < 0.02, "an almost empty battery gives a fifth of the power (" + empty + ")");
        helper.succeed();
    }
}
