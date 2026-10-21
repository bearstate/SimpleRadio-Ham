package com.codinglitch.simpleradio.ham.gametest;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioMessage;
import com.codinglitch.simpleradio.radio.RadioReceiver;
import com.codinglitch.simpleradio.radio.RadioTransmitter;
import com.codinglitch.simpleradio.radio.propagation.Environment;
import com.codinglitch.simpleradio.radio.propagation.Ionosphere;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import com.codinglitch.simpleradio.radio.propagation.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.codinglitch.simpleradio.ham.gametest.HamTests.check;
import static com.codinglitch.simpleradio.ham.gametest.HamTests.test;

/**
 * Terrain, radio horizon, buildings/caves and the ionosphere.
 */
public class PropagationTests {
    static List<TestFunction> generate() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("terrain_math", 20, true, PropagationTests::terrainMath));
        tests.add(test("ionosphere_math", 20, true, PropagationTests::ionosphereMath));
        tests.add(test("terrain_wall_blocks", 100, true, PropagationTests::terrainWallBlocks));
        tests.add(test("environment_underground", 100, true, PropagationTests::environmentUnderground));
        tests.add(test("signal_strength_indoors", 100, true, PropagationTests::signalStrengthIndoors));
        tests.add(test("lightning_strikes_antenna", 100, true, PropagationTests::lightningStrikesAntenna));
        return tests;
    }

    static void lightningStrikesAntenna(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos station = sky(helper, 5, 5);
        List<BlockPos> placed = new ArrayList<>();
        level.setBlockAndUpdate(station, com.codinglitch.simpleradio.ham.HamContent.STATION.get().defaultBlockState());
        placed.add(station);
        placed.addAll(fill(level, station.above(), station.above(8), com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.ANTENNA.defaultBlockState()));
        BlockPos top = station.above(8);

        var device = (com.codinglitch.simpleradio.ham.block.StationBlockEntity) level.getBlockEntity(station);
        check(helper, com.codinglitch.simpleradio.ham.radio.Lightning.heightAboveSupport(level, top) == 8, "the tip of an 8 block mast is 8 blocks above the station");

        check(helper, com.codinglitch.simpleradio.ham.radio.Lightning.strike(level, top, station) == com.codinglitch.simpleradio.ham.radio.Lightning.Outcome.BURNED, "an unprotected station burns out");

        BlockPos ground = station.north();
        level.setBlockAndUpdate(ground, Blocks.LIGHTNING_ROD.defaultBlockState());
        placed.add(ground);
        check(helper, com.codinglitch.simpleradio.ham.radio.Lightning.strike(level, top, station) == com.codinglitch.simpleradio.ham.radio.Lightning.Outcome.GROUNDED, "a grounded station survives");

        BlockPos rod = top.east().above();
        level.setBlockAndUpdate(rod, Blocks.LIGHTNING_ROD.defaultBlockState());
        placed.add(rod);
        check(helper, com.codinglitch.simpleradio.ham.radio.Lightning.strike(level, top, station) == com.codinglitch.simpleradio.ham.radio.Lightning.Outcome.DIVERTED, "a lightning rod next to the antenna takes the strike");

        // repair with redstone
        device.health.setBurned(true);
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 2));
        device.health.tryRepair(player, net.minecraft.world.InteractionHand.MAIN_HAND, station);
        check(helper, device.health.isBurned(), "not enough redstone does not repair");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 5));
        device.health.tryRepair(player, net.minecraft.world.InteractionHand.MAIN_HAND, station);
        check(helper, !device.health.isBurned(), "4 redstone repairs it");
        check(helper, player.getMainHandItem().getCount() == 1, "the redstone is used up");

        for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    static double[] flat(int samples, double height) {
        double[] terrain = new double[samples + 1];
        java.util.Arrays.fill(terrain, height);
        return terrain;
    }

    // ---- Pure models ---- \\

    static void terrainMath(GameTestHelper helper) {
        double[] flat = flat(80, 64);
        double open = Terrain.pathLossDb(2000, flat, 65.4, 65.4, 145);
        check(helper, open < 0.5, "open flat land costs nothing (" + open + " dB)");

        double[] hill = flat(80, 64);
        hill[40] = 114; // a 50 m hill half way
        double vhf = Terrain.pathLossDb(2000, hill, 65.4, 65.4, 145);
        double uhf = Terrain.pathLossDb(2000, hill, 65.4, 65.4, 435);
        double hf = Terrain.pathLossDb(2000, hill, 65.4, 65.4, 7.1);
        check(helper, vhf > 10 && vhf < 18, "a 50 m hill costs VHF 10-18 dB (" + vhf + ")");
        check(helper, uhf > vhf + 3, "UHF suffers more behind a hill (" + uhf + " vs " + vhf + ")");
        check(helper, hf < vhf - 5, "HF bends over hills (" + hf + " vs " + vhf + ")");

        double over = Terrain.pathLossDb(2000, hill, 140, 140, 145);
        check(helper, over < 0.5, "antennas higher than the hill see over it (" + over + ")");

        double[] ridges = flat(80, 64);
        ridges[25] = 104;
        ridges[55] = 104;
        double two = Terrain.pathLossDb(2000, ridges, 65.4, 65.4, 145);
        double[] single = flat(80, 64);
        single[25] = 104;
        double one = Terrain.pathLossDb(2000, single, 65.4, 65.4, 145);
        check(helper, two > one + 3, "two ridges cost more than one (" + two + " vs " + one + ")");

        double low = Terrain.pathLossDb(40_000, flat(80, 64), 66, 66, 145);
        double high = Terrain.pathLossDb(40_000, flat(80, 64), 104, 104, 145);
        check(helper, low > 10, "40 km with 2 m antennas is far beyond the radio horizon (" + low + ")");
        check(helper, high < 1, "40 m masts see 40 km (" + high + ")");
        check(helper, Math.abs(Terrain.horizonDistance(2, 2) - 11_658) < 100, "radio horizon of two 2 m antennas is ~11.7 km (" + Terrain.horizonDistance(2, 2) + ")");
        helper.succeed();
    }

    static void ionosphereMath(GameTestHelper helper) {
        Ionosphere.Conditions quiet = new Ionosphere.Conditions(0, 100, 2, false);
        long noon = 6000, midnight = 18000;

        double noonFoF2 = Ionosphere.criticalFrequency(quiet, noon);
        double nightFoF2 = Ionosphere.criticalFrequency(quiet, midnight);
        check(helper, noonFoF2 > 5 && noonFoF2 < 7, "noon critical frequency ~6 MHz (" + noonFoF2 + ")");
        check(helper, nightFoF2 < 3.5, "night critical frequency below 3.5 MHz (" + nightFoF2 + ")");

        check(helper, !Double.isNaN(Ionosphere.skyWaveLossDb(quiet, noon, 0, 6000, 14.2, 0.3)), "20 m is open by day for distant stations");
        check(helper, Double.isNaN(Ionosphere.skyWaveLossDb(quiet, noon, 0, 1000, 14.2, 0.3)), "20 m skips over near stations");
        check(helper, Double.isNaN(Ionosphere.skyWaveLossDb(quiet, noon, 0, 6000, 28.5, 0.3)), "10 m is closed on a quiet day");
        check(helper, Double.isNaN(Ionosphere.skyWaveLossDb(quiet, midnight, 0, 6000, 14.2, 0.3)), "20 m closes at night");

        double fortyNight = Ionosphere.skyWaveLossDb(quiet, midnight, 0, 5000, 7.1, 0.3);
        check(helper, !Double.isNaN(fortyNight) && fortyNight < 16, "40 m is open at night (" + fortyNight + ")");
        check(helper, Double.isNaN(Ionosphere.skyWaveLossDb(quiet, midnight, 0, 2000, 7.1, 0.3)), "40 m has a skip zone at night");

        double eightyDay = Ionosphere.skyWaveLossDb(quiet, noon, 0, 1000, 3.6, 0.3);
        double eightyNight = Ionosphere.skyWaveLossDb(quiet, midnight, 0, 3000, 3.6, 0.3);
        check(helper, eightyDay > 20, "80 m is absorbed by day (" + eightyDay + ")");
        check(helper, eightyNight < 12, "80 m works at night (" + eightyNight + ")");

        Ionosphere.Conditions active = new Ionosphere.Conditions(0, 200, 1, false);
        check(helper, !Double.isNaN(Ionosphere.skyWaveLossDb(active, noon, 0, 8000, 28.5, 0.3)), "10 m opens with high solar flux");

        Ionosphere.Conditions storm = new Ionosphere.Conditions(0, 100, 7, false);
        double calm = Ionosphere.skyWaveLossDb(quiet, noon, 0, 6000, 14.2, 0.3);
        double stormy = Ionosphere.skyWaveLossDb(storm, noon, 0, 6000, 14.2, 0.3);
        check(helper, Double.isNaN(stormy) || stormy > calm + 6, "a geomagnetic storm weakens HF (" + stormy + " vs " + calm + ")");

        Ionosphere.Conditions es = new Ionosphere.Conditions(0, 100, 2, true);
        check(helper, !Double.isNaN(Ionosphere.skyWaveLossDb(es, noon, 0, 6000, 50.2, 0.3)), "sporadic E opens 6 m");
        check(helper, Double.isNaN(Ionosphere.skyWaveLossDb(es, noon, 0, 6000, 145, 0.3)), "2 m has no sky wave");

        Ionosphere.Conditions a = Ionosphere.conditions(1234, 10), b = Ionosphere.conditions(1234, 10);
        check(helper, a.equals(b), "solar conditions are the same all day");
        double minFlux = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.ionosphere.minSolarFlux;
        double maxFlux = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.ionosphere.maxSolarFlux;
        for (int day = 0; day < 60; day++) {
            double flux = Ionosphere.conditions(99, day).solarFlux();
            check(helper, flux >= minFlux && flux <= maxFlux, "solar flux within its range (" + flux + ")");
        }
        helper.succeed();
    }

    // ---- In the world ---- \\
    // The game test structures sit deep underground in a normal world with hills around, so these tests
    // build their own scenery high in the sky where nothing else is in the way.

    static final int SKY = 250;

    static BlockPos sky(GameTestHelper helper, int relativeX, int relativeZ) {
        BlockPos absolute = helper.absolutePos(new BlockPos(relativeX, 0, relativeZ));
        return new BlockPos(absolute.getX(), SKY, absolute.getZ());
    }

    static Vector3f center(BlockPos pos, double height) {
        return new Vector3f(pos.getX() + 0.5f, (float) (pos.getY() + height), pos.getZ() + 0.5f);
    }

    static List<BlockPos> fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, state);
            placed.add(pos.immutable());
        }
        return placed;
    }

    static void clear(ServerLevel level, List<BlockPos> placed) {
        for (BlockPos pos : placed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    static void terrainWallBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = sky(helper, 1, 1), b = sky(helper, 1, 61);
        Vector3f antennaA = center(a, 0.5), antennaB = center(b, 0.5);

        Terrain.clear();
        double open = Terrain.lossDb(level, antennaA, antennaB, 145);
        check(helper, open < 0.5, "nothing in the way (" + open + " dB)");

        // a wall half way, reaching well above the line between the antennas
        List<BlockPos> wall = fill(level, new BlockPos(a.getX() - 4, SKY - 10, a.getZ() + 30), new BlockPos(a.getX() + 4, SKY + 14, a.getZ() + 30), Blocks.STONE.defaultBlockState());

        Terrain.clear();
        double vhf = Terrain.lossDb(level, antennaA, antennaB, 145);
        double uhf = Terrain.lossDb(level, antennaA, antennaB, 435);
        double hf = Terrain.lossDb(level, antennaA, antennaB, 7.1);
        clear(level, wall);
        Terrain.clear();

        check(helper, vhf > 15, "a wall 14 blocks above the path blocks VHF (" + vhf + " dB)");
        check(helper, uhf > vhf, "and UHF even more (" + uhf + " dB)");
        check(helper, hf < vhf, "HF bends around it better (" + hf + " dB)");
        helper.succeed();
    }

    /**
     * A closed stone room (floor, walls and a roof of the given thickness) around a 1x2 air column.
     */
    static List<BlockPos> buildBunker(ServerLevel level, BlockPos inside, int roofThickness) {
        List<BlockPos> placed = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy < 2 + roofThickness; dy++) {
                    if (dx == 0 && dz == 0 && (dy == 0 || dy == 1)) continue;
                    BlockPos pos = inside.offset(dx, dy, dz);
                    level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    placed.add(pos);
                }
            }
        }
        return placed;
    }

    static void environmentUnderground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos inside = sky(helper, 20, 20);
        Vector3f antenna = center(inside, 0.4);

        Environment.clear();
        check(helper, Environment.lossDb(level, antenna, HamFrequency.Band.VHF) == 0, "no loss in the open");

        List<BlockPos> bunker = buildBunker(level, inside, 5);
        helper.runAfterDelay(5, () -> {
            Environment.clear();
            double vhf = Environment.lossDb(level, antenna, HamFrequency.Band.VHF);
            double uhf = Environment.lossDb(level, antenna, HamFrequency.Band.UHF);
            double hf = Environment.lossDb(level, antenna, HamFrequency.Band.HF);

            // a hole in the wall lets daylight (and part of the signal) in
            level.setBlockAndUpdate(inside.east(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(inside.east().above(), Blocks.AIR.defaultBlockState());
            helper.runAfterDelay(5, () -> {
                Environment.clear();
                double open = Environment.lossDb(level, antenna, HamFrequency.Band.VHF);
                clear(level, bunker);
                Environment.clear();

                check(helper, Math.abs(vhf - 11) < 0.5, "5 blocks of stone overhead cost VHF ~11 dB (" + vhf + ")");
                check(helper, uhf > vhf && hf < vhf, "UHF suffers more, HF less (" + uhf + ", " + hf + ")");
                check(helper, open < vhf / 2, "an opening to the sky lets most of the signal out (" + open + ")");
                helper.succeed();
            });
        });
    }

    static void signalStrengthIndoors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos txBlock = sky(helper, 1, 1), rxBlock = sky(helper, 1, 40);

        // real blocks under the test routers keep the router garbage collector from removing them
        level.setBlockAndUpdate(txBlock, com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.TRANSMITTER.defaultBlockState());
        level.setBlockAndUpdate(rxBlock, com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks.RECEIVER.defaultBlockState());

        Frequency frequency = RadioManager.getInstance().frequencies().getOrCreate("145.230", Frequency.Modulation.FREQUENCY);
        WorldlyPosition txAt = WorldlyPosition.of(center(txBlock, 0.5), level);
        WorldlyPosition rxAt = WorldlyPosition.of(center(rxBlock, 0.5), level);

        UUID sender = UUID.randomUUID();
        RadioTransmitter transmitter = new RadioTransmitter(frequency, txAt, sender);
        transmitter.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        frequency.addTransmitter(transmitter);

        AtomicReference<Float> heard = new AtomicReference<>(Float.NaN);
        RadioReceiver receiver = new RadioReceiver(frequency, rxAt, UUID.randomUUID());
        receiver.frequencingType(SimpleRadioFrequencing.WALKIE_TALKIE);
        receiver.setAcceptingCriteria(message -> {
            heard.set(((RadioMessage) message).signalDb);
            return false;
        });
        frequency.addReceiver(receiver);

        Runnable send = () -> transmitter.accept(new RadioMessage(sender, txAt, HamTests.tone(), 1f));

        Environment.clear();
        Terrain.clear();
        send.run();
        float outside = heard.get();
        double expected = RadioLink.marginDb(transmitter.getPower(Frequency.Modulation.FREQUENCY) + receiver.getPower(), txAt.distance(rxAt));
        check(helper, Math.abs(outside - expected) < 0.5, "free line of sight gives the calibrated signal (" + outside + " vs " + expected + " dB)");

        List<BlockPos> bunker = buildBunker(level, rxBlock, 5);
        helper.runAfterDelay(5, () -> {
            Environment.clear();
            Terrain.clear();
            heard.set(Float.NaN);
            send.run();
            float inside = heard.get();

            clear(level, bunker);
            frequency.removeTransmitter(transmitter);
            frequency.removeReceiver(receiver);
            RadioManager.getInstance().removeRouter(transmitter);
            RadioManager.getInstance().removeRouter(receiver);
            level.setBlockAndUpdate(txBlock, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(rxBlock, Blocks.AIR.defaultBlockState());
            Environment.clear();
            Terrain.clear();

            check(helper, !Float.isNaN(inside), "still heard through a bit of stone");
            check(helper, Math.abs(outside - inside - 11) < 1, "the bunker costs the signal ~11 dB (" + outside + " -> " + inside + " dB)");
            check(helper, RadioLink.sUnits(inside) < RadioLink.sUnits(outside), "the S-meter drops");
            check(helper, receiver.lastSignalDb == inside, "the receiver remembers the last signal for the S-meter");
            helper.succeed();
        });
    }
}
