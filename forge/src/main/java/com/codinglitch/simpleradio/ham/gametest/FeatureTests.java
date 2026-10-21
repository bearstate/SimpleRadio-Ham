package com.codinglitch.simpleradio.ham.gametest;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.aprs.Afsk;
import com.codinglitch.simpleradio.ham.aprs.AprsPacket;
import com.codinglitch.simpleradio.ham.block.FoxTransmitterBlockEntity;
import com.codinglitch.simpleradio.ham.block.SstvTerminalBlockEntity;
import com.codinglitch.simpleradio.ham.block.StationBlock;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.licence.CallsignRegistry;
import com.codinglitch.simpleradio.ham.licence.LicenceExam;
import com.codinglitch.simpleradio.ham.menu.SstvMenu;
import com.codinglitch.simpleradio.ham.radio.CwGenerator;
import com.codinglitch.simpleradio.ham.radio.DirectionalReceiver;
import com.codinglitch.simpleradio.ham.radio.PacedAudio;
import com.codinglitch.simpleradio.ham.sstv.Robot36;
import com.codinglitch.simpleradio.radio.BandPlan;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codinglitch.simpleradio.ham.gametest.HamTests.*;

/**
 * Game tests of the operating features: SSTV, APRS, licences, memories and fox hunting.
 */
public class FeatureTests {
    static Collection<TestFunction> generate() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("sstv_picture", 40_000, FeatureTests::sstvPicture));
        tests.add(test("robot36_encoding", 20, FeatureTests::robot36Encoding));
        tests.add(test("afsk_packet", 20, FeatureTests::afskPacket));
        tests.add(test("licence_and_callsigns", 20, FeatureTests::licenceAndCallsigns));
        tests.add(test("handheld_memories", 20, FeatureTests::handheldMemories));
        tests.add(test("fox_hunt", 6000, FeatureTests::foxHunt));
        tests.add(test("mobile_radio", 400, FeatureTests::mobileRadio));
        tests.add(test("config_sync", 20, FeatureTests::configSync));
        tests.add(test("settings_kept_when_broken", 60, FeatureTests::settingsKeptWhenBroken));
        tests.add(test("recipes_loaded", 20, FeatureTests::recipesLoaded));
        tests.add(test("block_s_meter", 60, FeatureTests::blockSMeter));
        return tests;
    }

    // ---- SSTV ---- \\

    static void sstvPicture(GameTestHelper helper) {
        BlockPos stationA = new BlockPos(1, 2, 1), terminalA = new BlockPos(1, 2, 3);
        BlockPos stationB = new BlockPos(8, 2, 1), terminalB = new BlockPos(8, 2, 3);
        for (BlockPos pos : List.of(stationA, stationB)) {
            helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
            helper.setBlock(pos.above(), com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.ANTENNA.defaultBlockState());
        }
        for (BlockPos pos : List.of(terminalA, terminalB)) helper.setBlock(pos, HamContent.SSTV_TERMINAL.get().defaultBlockState());
        StationBlockEntity a = be(helper, stationA), b = be(helper, stationB);
        a.applySettings("145.500", Frequency.Modulation.FREQUENCY, 0, 5);
        b.applySettings("145.500", Frequency.Modulation.FREQUENCY, 0, 5);
        SstvTerminalBlockEntity sender = be(helper, terminalA), receiver = be(helper, terminalB);

        // a picture: red and blue halves, swapped in the lower half
        ServerLevel level = helper.getLevel();
        int id = level.getFreeMapId();
        MapItemSavedData picture = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension()).locked();
        byte red = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.NORMAL);
        byte blue = MapColor.COLOR_BLUE.getPackedId(MapColor.Brightness.HIGH);
        for (int i = 0; i < 128 * 128; i++) {
            boolean left = i % 128 < 64, top = i / 128 < 64;
            picture.colors[i] = left == top ? red : blue;
        }
        level.setMapData(MapItem.makeKey(id), picture);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.getOrCreateTag().putInt("map", id);
        sender.items.setItem(SstvTerminalBlockEntity.SLOT_SEND, map);
        receiver.items.setItem(SstvTerminalBlockEntity.SLOT_BLANK, new ItemStack(Items.MAP, 2));

        // the picture takes 37 seconds in real time: played out faster here
        BandPlan.licensed = player -> true;
        PacedAudio.speed = 25;
        Player operator = helper.makeMockPlayer();
        AtomicInteger step = new AtomicInteger();
        AtomicBoolean sawReceiving = new AtomicBoolean();
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger heardFrames = new AtomicInteger();
        java.util.function.Consumer<com.codinglitch.simpleradio.radio.RadioMessage> counter = message -> heardFrames.incrementAndGet();
        b.addDataListener(counter);

        helper.onEachTick(() -> {
            power(a, 400);
            power(b, 400);
            if (ticks.incrementAndGet() == 35_000) {
                b.removeDataListener(counter);
                helper.fail("stuck at step " + step.get() + ": on " + StationBlock.isOn(helper.getBlockState(stationA)) + "/" + StationBlock.isOn(helper.getBlockState(stationB))
                        + ", terminals see stations " + sender.hasStation() + "/" + receiver.hasStation()
                        + ", sending " + a.isSendingData() + " (" + a.dataProgress() + "), data frames heard " + heardFrames.get()
                        + ", receiver flags " + receiver.dataAccess.get(SstvMenu.DATA_FLAGS) + ", rows " + receiver.dataAccess.get(SstvMenu.DATA_RECEIVE_PROGRESS));
                return;
            }
            switch (step.get()) {
                case 0 -> {
                    if (!StationBlock.isOn(helper.getBlockState(stationA)) || !StationBlock.isOn(helper.getBlockState(stationB))) return;
                    if (!sender.hasStation() || !receiver.hasStation()) return;
                    String denial = sender.startSending(operator);
                    check(helper, denial == null, "the terminal sends through its station (" + denial + ")");
                    check(helper, sender.startSending(operator) != null, "a second picture waits for the first");
                    step.set(1);
                }
                case 1 -> {
                    if ((receiver.dataAccess.get(SstvMenu.DATA_FLAGS) & SstvMenu.FLAG_RECEIVING) != 0) sawReceiving.set(true);
                    ItemStack printed = receiver.items.getItem(SstvTerminalBlockEntity.SLOT_OUTPUT);
                    if (printed.isEmpty()) return;
                    step.set(2);

                    MapItemSavedData copy = MapItem.getSavedData(printed, level);
                    check(helper, printed.is(Items.FILLED_MAP) && copy != null && copy.locked, "the picture is printed on a locked map");
                    int same = 0;
                    for (int i = 0; i < 128 * 128; i++) if (copy.colors[i] == picture.colors[i]) same++;
                    check(helper, same > 128 * 128 * 0.97, "the picture arrives clean from a nearby station (" + same + " of 16384 pixels)");
                    check(helper, sawReceiving.get(), "the receiving terminal showed the picture coming in");
                    check(helper, receiver.items.getItem(SstvTerminalBlockEntity.SLOT_BLANK).getCount() == 1, "one blank map was used");
                    check(helper, (sender.dataAccess.get(SstvMenu.DATA_FLAGS) & SstvMenu.FLAG_SENDING) == 0, "the sender finished");
                    b.removeDataListener(counter);
                    helper.succeed();
                }
                default -> {}
            }
        });
    }

    static void robot36Encoding(GameTestHelper helper) {
        byte[] colors = new byte[128 * 128];
        Robot36.Encoded encoded = Robot36.encode(colors, 0.5);

        // VIS header (910 ms) + 240 lines of 150 ms
        double expectedMs = 910 + Robot36.LINES * Robot36.LINE_MS;
        int expectedFrames = (int) Math.ceil(expectedMs / 20.0);
        check(helper, Math.abs(encoded.frames().size() - expectedFrames) <= 1, "Robot 36 lasts 36.9 s (" + encoded.frames().size() + " frames)");
        check(helper, Math.abs(encoded.lineStartMs()[0] - 910) < 0.01, "the first line starts after the VIS code");
        check(helper, Math.abs(encoded.lineStartMs()[239] - (910 + 239 * 150)) < 0.01, "lines are 150 ms apart");

        // the leader tone is 1900 Hz: count zero crossings in its first 100 ms
        short[] samples = join(encoded.frames(), 5);
        int crossings = 0;
        for (int i = 1; i < 4800; i++) if ((samples[i - 1] < 0) != (samples[i] < 0)) crossings++;
        check(helper, Math.abs(crossings / 2.0 / 0.1 - 1900) < 30, "leader tone at 1900 Hz (" + crossings / 2.0 / 0.1 + ")");

        for (int line = 0; line < Robot36.LINES; line++) {
            int row = Robot36.mapRow(line);
            check(helper, row >= 0 && row < 128, "map row in range");
        }
        check(helper, Robot36.mapRow(0) == 0 && Robot36.mapRow(239) == 127, "the lines cover the whole map");
        helper.succeed();
    }

    private static short[] join(List<short[]> frames, int count) {
        short[] out = new short[count * CwGenerator.FRAME_SIZE];
        for (int f = 0; f < count && f < frames.size(); f++) System.arraycopy(frames.get(f), 0, out, f * CwGenerator.FRAME_SIZE, CwGenerator.FRAME_SIZE);
        return out;
    }

    // ---- APRS ---- \\

    static void afskPacket(GameTestHelper helper) {
        check(helper, Afsk.crc("123456789".getBytes(StandardCharsets.US_ASCII)) == 0x906E, "AX.25 frame check (CRC-16/X.25)");

        AprsPacket packet = new AprsPacket("TA1AB-7", "minecraft:overworld", 0, 64, 0, 0, 0, "test", 0);
        check(helper, "!3900.00N/03500.00E[test".equals(packet.information()), "position report at the origin: " + packet.information());
        AprsPacket moving = new AprsPacket("TA1AB-7", "minecraft:overworld", 1852, 64, -1852, 18.52, 90, "", 0);
        check(helper, moving.information().endsWith("[090/010"), "course and speed in knots: " + moving.information());

        // demodulate the audio and compare the frame
        byte[] frame = Afsk.frame("TA1AB", 7, packet.information());
        List<short[]> audio = Afsk.render(frame, 0.5);
        short[] samples = join(audio, audio.size());
        int perBit = CwGenerator.SAMPLE_RATE / 1200;

        List<Boolean> bits = new ArrayList<>();
        boolean previousMark = true;
        for (int start = 0; start + perBit <= samples.length; start += perBit) {
            boolean mark = tonePower(samples, start, perBit, 1200) > tonePower(samples, start, perBit, 2200);
            bits.add(mark == previousMark); // NRZI: no change is a 1
            previousMark = mark;
        }

        // skip the 40 opening flags, then undo the bit stuffing up to the closing flag
        List<Boolean> data = new ArrayList<>();
        int ones = 0;
        boolean closed = false;
        for (int i = 40 * 8; i < bits.size(); i++) {
            boolean bit = bits.get(i);
            if (ones == 5) {
                if (!bit) {
                    ones = 0;
                    continue;
                }
                for (int k = 0; k < 6; k++) data.remove(data.size() - 1);
                closed = true;
                break;
            }
            data.add(bit);
            ones = bit ? ones + 1 : 0;
        }
        check(helper, closed && data.size() % 8 == 0, "the frame ends with a flag on a byte boundary");

        byte[] decoded = new byte[data.size() / 8];
        for (int i = 0; i < decoded.length; i++) {
            int value = 0;
            for (int b = 0; b < 8; b++) if (data.get(i * 8 + b)) value |= 1 << b;
            decoded[i] = (byte) value;
        }
        check(helper, java.util.Arrays.equals(decoded, frame), "the audio decodes back to the same frame");

        byte[] body = java.util.Arrays.copyOf(decoded, decoded.length - 2);
        int fcs = (decoded[decoded.length - 2] & 255) | (decoded[decoded.length - 1] & 255) << 8;
        check(helper, Afsk.crc(body) == fcs, "frame check sequence matches");
        // three 7 byte addresses, control and protocol id come before the information field
        String information = new String(body, 23, body.length - 23, StandardCharsets.US_ASCII);
        check(helper, information.equals(packet.information()), "information field: " + information);
        helper.succeed();
    }

    private static double tonePower(short[] samples, int start, int length, double hz) {
        double re = 0, im = 0;
        for (int i = 0; i < length; i++) {
            double angle = 2 * Math.PI * hz * i / CwGenerator.SAMPLE_RATE;
            re += samples[start + i] * Math.cos(angle);
            im += samples[start + i] * Math.sin(angle);
        }
        return re * re + im * im;
    }

    // ---- Licences ---- \\

    static void licenceAndCallsigns(GameTestHelper helper) {
        int count = LicenceExam.questionCount();
        int[] questions = new int[count], right = new int[count], wrong = new int[count];
        for (int i = 0; i < count; i++) {
            questions[i] = i;
            right[i] = LicenceExam.answer(i);
            wrong[i] = (LicenceExam.answer(i) + 1) % 4;
        }
        check(helper, LicenceExam.score(questions, right) == count, "all right answers score full marks");
        check(helper, LicenceExam.score(questions, wrong) == 0, "wrong answers score nothing");
        int[] skipped = right.clone();
        skipped[0] = -1;
        check(helper, LicenceExam.score(questions, skipped) == count - 1, "a skipped question scores nothing");

        CallsignRegistry registry = CallsignRegistry.get(helper.getLevel().getServer());
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        try {
            String callsign = registry.assign(first);
            String prefix = CallsignRegistry.clean(SimpleRadioLibrary.SERVER_CONFIG.band_plan.callsignPrefix);
            check(helper, callsign.matches(prefix + "[1-9][A-Z]{2,3}"), "call sign like TA3KQ: " + callsign);
            check(helper, callsign.equals(registry.assign(first)), "a player keeps their call sign");
            check(helper, !registry.set(second, callsign.toLowerCase(java.util.Locale.ROOT)), "a call sign belongs to one player");
            check(helper, registry.set(second, "ta2 xyz"), "a free call sign can be chosen");
            check(helper, "TA2XYZ".equals(registry.callsignOf(second)) && second.equals(registry.ownerOf("TA2XYZ")), "call signs are cleaned up");
        } finally {
            registry.remove(first);
            registry.remove(second);
        }
        check(helper, registry.callsignOf(first) == null && registry.ownerOf("TA2XYZ") == null, "removed call signs are free again");
        helper.succeed();
    }

    // ---- Memories ---- \\

    static void handheldMemories(GameTestHelper helper) {
        ItemStack stack = new ItemStack(SimpleRadioItems.TRANSCEIVER);
        TransceiverItem item = (TransceiverItem) stack.getItem();
        CompoundTag tag = stack.getOrCreateTag();
        TransceiverItem.setMemories(tag, List.of(
                new TransceiverItem.Memory("RPT", "439.100", Frequency.Modulation.FREQUENCY, -7600, 88.5f, 88.5f),
                new TransceiverItem.Memory("S20", "145.500", Frequency.Modulation.FREQUENCY, 0, 0, 0),
                new TransceiverItem.Memory("HF", "14.200", Frequency.Modulation.SINGLE_SIDEBAND, 0, 0, 0)
        ));
        check(helper, TransceiverItem.getMemories(tag).size() == 3, "three channels stored");

        item.stepChannel(stack, helper.getLevel(), 1);
        check(helper, TransceiverItem.getChannel(tag) == 0 && "439.100".equals(tag.getString("frequency")), "channel up starts at the first channel");
        check(helper, tag.getInt(TransceiverItem.SHIFT) == -7600 && Math.abs(tag.getFloat(TransceiverItem.CTCSS_TX) - 88.5f) < 0.01, "the repeater channel brings its shift and tone");

        item.stepChannel(stack, helper.getLevel(), -1);
        check(helper, TransceiverItem.getChannel(tag) == 2 && "SB".equals(tag.getString("modulation")), "channel down wraps to the last channel");
        item.stepChannel(stack, helper.getLevel(), 1);
        check(helper, TransceiverItem.getChannel(tag) == 0, "channel up wraps to the first channel");

        // a broken entry (bad frequency) is left out
        CompoundTag broken = new TransceiverItem.Memory("X", "abc", Frequency.Modulation.FREQUENCY, 0, 0, 0).save();
        tag.getList(TransceiverItem.MEMORIES, net.minecraft.nbt.Tag.TAG_COMPOUND).add(broken);
        check(helper, TransceiverItem.getMemories(tag).size() == 3, "invalid channels are skipped");
        helper.succeed();
    }

    // ---- Recipes ---- \\

    static void blockSMeter(GameTestHelper helper) {
        BlockPos radioPos = new BlockPos(1, 2, 1), receiverPos = new BlockPos(1, 2, 4);
        helper.setBlock(radioPos, com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.RADIO.defaultBlockState());
        helper.setBlock(receiverPos, com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.RECEIVER.defaultBlockState());
        Frequency frequency = RadioBehaviourTests.frequency("145.500", Frequency.Modulation.FREQUENCY);
        com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity radio = be(helper, radioPos);
        com.codinglitch.simpleradio.core.registry.blocks.ReceiverBlockEntity receiver = be(helper, receiverPos);
        radio.frequency = frequency;
        receiver.frequency = frequency;
        ServerLevel level = helper.getLevel();

        helper.runAtTickTime(5, () -> {
            var reading = com.codinglitch.simpleradio.ham.radio.BlockSignal.read(level, helper.absolutePos(radioPos));
            check(helper, reading != null && reading.status() == com.codinglitch.simpleradio.ham.radio.BlockSignal.STATUS_NO_POWER, "a radio without electricity shows no power");
            check(helper, "145.500".equals(reading.frequency()) && "FM".equals(reading.modulation()), "the meter shows the frequency: " + reading.frequency() + " " + reading.modulation());
            var noCatalyst = com.codinglitch.simpleradio.ham.radio.BlockSignal.read(level, helper.absolutePos(receiverPos));
            check(helper, noCatalyst != null && noCatalyst.status() == com.codinglitch.simpleradio.ham.radio.BlockSignal.STATUS_NO_CATALYST, "a receiver without a catalyst says so");
            check(helper, com.codinglitch.simpleradio.ham.radio.BlockSignal.read(level, helper.absolutePos(new BlockPos(1, 1, 1))) == null, "other blocks have no meter");
        });
        // electricity from tick 8 on, after the unpowered reading
        AtomicInteger ticks = new AtomicInteger();
        helper.onEachTick(() -> {
            if (ticks.incrementAndGet() >= 8) power(radio, 100);
        });
        helper.runAtTickTime(15, () -> {
            var quiet = com.codinglitch.simpleradio.ham.radio.BlockSignal.read(level, helper.absolutePos(radioPos));
            check(helper, quiet.status() == com.codinglitch.simpleradio.ham.radio.BlockSignal.STATUS_ON && quiet.signal() == Short.MIN_VALUE, "a powered radio with nothing on the air reads S0");

            UUID sender = UUID.randomUUID();
            com.codinglitch.simpleradio.radio.RadioTransmitter transmitter = RadioBehaviourTests.transmitter(helper, frequency, new BlockPos(4, 2, 1), sender);
            RadioBehaviourTests.send(transmitter, sender);
            var heard = com.codinglitch.simpleradio.ham.radio.BlockSignal.read(level, helper.absolutePos(radioPos));
            RadioBehaviourTests.remove(transmitter);
            check(helper, heard.signal() != Short.MIN_VALUE && heard.signal() > 0, "a nearby transmitter moves the needle (" + heard.signal() / 10.0 + " dB)");
            helper.succeed();
        });
    }

    static void recipesLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String name : List.of("station", "repeater", "scanner", "charger", "cw_key", "battery", "yagi_antenna", "dual_band_antenna",
                "power_supply", "antenna_tuner", "desk_microphone", "hand_microphone", "fox_transmitter", "direction_finder",
                "aprs_radio", "mobile_radio", "sstv_terminal", "licence_exam", "radio_manual")) {
            check(helper, recipes.byKey(com.codinglitch.simpleradio.CommonSimpleRadio.id(name)).isPresent(), "recipe " + name + " is loaded (its config switch is on)");
        }
        helper.succeed();
    }

    // ---- Breaking and placing ---- \\

    static void settingsKeptWhenBroken(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        station.applySettings("014.200", Frequency.Modulation.SINGLE_SIDEBAND, 0, 40, 0, 0, 120, true);
        UUID oldId = station.id;

        // broken like a player would (the helper's destroyBlock drops nothing)
        helper.runAtTickTime(5, () -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true));
        helper.runAtTickTime(10, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(2), item -> item.getItem().is(HamContent.STATION_ITEM.get()));
            check(helper, drops.size() == 1, "the station drops itself");
            ItemStack drop = drops.get(0).getItem();
            CompoundTag stored = drop.getTagElement("BlockEntityTag");
            check(helper, stored != null && "014.200".equals(stored.getString("frequency")) && stored.getInt("watts") == 40, "the drop keeps the settings");
            check(helper, !stored.contains("reference") && !stored.contains("energy") && !stored.contains("temperature"), "but not its identity, energy or heat");

            // placing it again
            helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
            net.minecraft.world.item.BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, helper.absolutePos(pos), drop);
            StationBlockEntity placed = be(helper, pos);
            check(helper, "014.200".equals(placed.frequency) && placed.modulation == Frequency.Modulation.SINGLE_SIDEBAND && placed.watts == 40
                    && placed.clarifierHz == 120 && placed.vox, "the placed station has the old settings");
            check(helper, placed.id != null && !placed.id.equals(oldId), "with a new identity");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    // ---- Config sync ---- \\

    static void configSync(GameTestHelper helper) {
        var config = SimpleRadioLibrary.SERVER_CONFIG;
        java.util.Map<String, String> original = com.codinglitch.simpleradio.radio.ConfigSnapshot.capture(config);
        try {
            check(helper, original.containsKey("power.requirePower") && original.containsKey("band_plan.callsignPrefix"), "top level pages are captured");
            check(helper, original.containsKey("compatibilities.vibrative_voice.enabled"), "nested pages are captured");
            check(helper, String.valueOf(config.mobile.vehicleWatts).equals(original.get("mobile.vehicleWatts")), "values are written as text");

            java.util.Map<String, String> server = new java.util.LinkedHashMap<>(original);
            server.put("mobile.vehicleWatts", "40.0");
            server.put("handheld.requireBattery", String.valueOf(!config.handheld.requireBattery));
            server.put("band_plan.callsignPrefix", "YM");
            server.put("no_such_page.value", "1");
            server.put("mobile.vehicleCharging", "not a number");
            int applied = com.codinglitch.simpleradio.radio.ConfigSnapshot.apply(config, server);
            check(helper, applied == server.size() - 2, "unknown keys and bad values are skipped (" + applied + " of " + server.size() + ")");
            check(helper, config.mobile.vehicleWatts == 40.0 && "YM".equals(config.band_plan.callsignPrefix), "the server values are used");
            check(helper, config.handheld.requireBattery != Boolean.parseBoolean(original.get("handheld.requireBattery")), "booleans are applied");
        } finally {
            com.codinglitch.simpleradio.radio.ConfigSnapshot.apply(config, original);
        }
        check(helper, com.codinglitch.simpleradio.radio.ConfigSnapshot.capture(config).equals(original), "the own config comes back after leaving");
        helper.succeed();
    }

    // ---- Mobile radio ---- \\

    static void mobileRadio(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = new ItemStack(HamContent.MOBILE_RADIO.get());
        com.codinglitch.simpleradio.ham.item.MobileRadioItem item = (com.codinglitch.simpleradio.ham.item.MobileRadioItem) stack.getItem();
        item.applySettings(stack, level, "145.500", Frequency.Modulation.FREQUENCY, 0, true, 0, 0);
        TransceiverItem.setBattery(stack, TransceiverItem.maxBattery() / 2);

        net.minecraft.world.entity.monster.Zombie driver = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        driver.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        net.minecraft.world.entity.vehicle.Boat boat = helper.spawn(net.minecraft.world.entity.EntityType.BOAT, new BlockPos(1, 2, 1));
        var config = SimpleRadioLibrary.SERVER_CONFIG.mobile;
        int startBattery = TransceiverItem.getBattery(stack);
        AtomicInteger ticks = new AtomicInteger();

        helper.onEachTick(() -> {
            ItemStack held = driver.getMainHandItem();
            item.entityTick(held, driver);
            int tick = ticks.incrementAndGet();
            // (a boat also picks up mobs that bump into it)
            if (tick == 5) check(helper, driver.getVehicle() == boat || driver.startRiding(boat, true), "the driver gets into the boat");

            if (tick == 100) {
                com.codinglitch.simpleradio.radio.RadioTransmitter transmitter = transmitterOf(held, level);
                check(helper, transmitter != null, "the mobile radio has a transmitter");
                check(helper, Math.abs(transmitter.powerMultiplier - com.codinglitch.simpleradio.ham.item.MobileRadioItem.rangeFactor(config.vehicleWatts)) < 0.01,
                        "full power in the vehicle (" + transmitter.powerMultiplier + ")");
                check(helper, transmitter.antennaPosition().y() > boat.getBoundingBox().maxY, "the antenna is on the roof");
                check(helper, TransceiverItem.getBattery(held) > startBattery, "the vehicle charges the battery");
                check(helper, held.getTag().getBoolean(com.codinglitch.simpleradio.ham.item.MobileRadioItem.MOBILE), "it knows it is in a vehicle");
                // out of the boat, and the boat gone so it does not pick the driver up again
                boat.discard();
            }
            if (tick == 160) {
                check(helper, driver.getVehicle() == null, "the driver is on foot");
                com.codinglitch.simpleradio.radio.RadioTransmitter transmitter = transmitterOf(held, level);
                float portable = com.codinglitch.simpleradio.ham.item.MobileRadioItem.rangeFactor(config.portableWatts) * TransceiverItem.batteryRangeFactor(held);
                check(helper, Math.abs(transmitter.powerMultiplier - portable) < 0.01, "handheld power on foot (" + transmitter.powerMultiplier + " vs " + portable + ")");
                check(helper, !held.getTag().getBoolean(com.codinglitch.simpleradio.ham.item.MobileRadioItem.MOBILE), "it knows it is carried");
                helper.succeed();
            }
        });
    }

    @org.jetbrains.annotations.Nullable
    private static com.codinglitch.simpleradio.radio.RadioTransmitter transmitterOf(ItemStack stack, ServerLevel level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("reference")) return null;
        Frequency frequency = RadioManager.getInstance().frequencies().get(tag.getString("frequency"), Frequency.Modulation.FREQUENCY);
        if (frequency == null) return null;
        for (var transmitter : frequency.getTransmitters()) {
            if (transmitter instanceof com.codinglitch.simpleradio.radio.RadioTransmitter radio && tag.getUUID("reference").equals(radio.getReference())) return radio;
        }
        return null;
    }

    // ---- Fox hunting ---- \\

    static void foxHunt(GameTestHelper helper) {
        // exactly one of the five foxes is on the air at any time
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.FOX_TRANSMITTER.get().defaultBlockState());
        FoxTransmitterBlockEntity fox = be(helper, pos);
        var foxConfig = SimpleRadioLibrary.SERVER_CONFIG.fox;
        int previousCycle = foxConfig.cycleSeconds;
        try {
            // a hunt with a 60 second cycle
            foxConfig.cycleSeconds = 60;
            for (long tick = 0; tick < 60 * 20 * 2; tick += 7) {
                int onAir = 0;
                for (int n = 1; n <= 5; n++) {
                    fox.setFox(n);
                    if (fox.isOnAir(tick)) onAir++;
                }
                check(helper, onAir == 1, "one fox transmits at a time (tick " + tick + ": " + onAir + ")");
            }
            // a single fox (cycle 0) transmits all the time
            foxConfig.cycleSeconds = 0;
            check(helper, fox.isOnAir(0) && fox.isOnAir(12345), "a lone fox never stops");
        } finally {
            foxConfig.cycleSeconds = previousCycle;
        }
        fox.setFox(1);
        fox.setFrequency("144.500");

        // the direction finder's antenna pattern
        var config = SimpleRadioLibrary.SERVER_CONFIG.direction_finder;
        check(helper, Math.abs(DirectionalReceiver.pattern(0) - 1) < 1e-9, "full gain straight ahead");
        check(helper, Math.abs(DirectionalReceiver.pattern(config.beamWidth / 2.0) - 0.5) < 0.01, "half power at the edge of the beam");
        check(helper, Math.abs(10 * Math.log10(DirectionalReceiver.pattern(180)) + config.frontToBackDb) < 0.01, "front to back ratio behind");

        // the fox is heard on its frequency while on the air
        PacedAudio.speed = 10;
        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate("144.500", Frequency.Modulation.FREQUENCY);
        AtomicInteger tones = new AtomicInteger();
        RadioReceiver[] listener = new RadioReceiver[1];
        listener[0] = listener(helper, frequency, new BlockPos(6, 2, 1), message -> {
            if (message.getActivity() > 100) tones.incrementAndGet();
            return true;
        });

        helper.onEachTick(() -> {
            power(fox, 200);
            if (tones.get() >= 5) {
                remove(listener[0]);
                helper.succeed();
            }
        });
    }
}
