package com.codinglitch.simpleradio.ham.gametest;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.CwKeyBlock;
import com.codinglitch.simpleradio.ham.block.StationBlock;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.compat.ElectrodynamicsInterference;
import com.codinglitch.simpleradio.radio.RadioManager;
import electrodynamics.common.tile.electricitygrid.GenericTileWire;
import electrodynamics.common.tile.electricitygrid.generators.TileCreativePowerSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import voltaic.registers.VoltaicCapabilities;

import java.util.List;

import static com.codinglitch.simpleradio.ham.gametest.HamTests.be;
import static com.codinglitch.simpleradio.ham.gametest.HamTests.check;
import static com.codinglitch.simpleradio.ham.gametest.HamTests.test;

/**
 * Tests that need Electrodynamics; only class-loaded when it is installed.
 */
public class ElectrodynamicsTests {
    static List<TestFunction> generate() {
        return List.of(
                test("ed_capability", 40, ElectrodynamicsTests::capability),
                test("ed_powered_through_wires", 200, ElectrodynamicsTests::poweredThroughWires),
                test("ed_over_voltage", 200, ElectrodynamicsTests::overVoltage),
                test("ed_dc_line_feeds_everything", 300, ElectrodynamicsTests::dcLineFeedsEverything),
                test("ed_interference_cuts_bare_wires", 300, ElectrodynamicsTests::interferenceCutsBareWires)
        );
    }

    static BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation("electrodynamics", id));
        return block.defaultBlockState();
    }

    static void source(GameTestHelper helper, BlockPos pos, int voltage) {
        helper.setBlock(pos, block("creativepowersource"));
        TileCreativePowerSource source = be(helper, pos);
        source.voltage.setValue(voltage);
        source.power.setValue(0.001); // 50 J per tick
    }

    static void capability(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, HamContent.STATION.get().defaultBlockState());
        StationBlockEntity station = be(helper, pos);
        check(helper, station.getCapability(VoltaicCapabilities.CAPABILITY_ELECTRODYNAMIC_BLOCK, Direction.NORTH).isPresent(), "station exposes the Electrodynamics capability");
        helper.succeed();
    }

    static void poweredThroughWires(GameTestHelper helper) {
        // mains straight into a station: its protection refuses it
        BlockPos direct = new BlockPos(3, 2, 2);
        source(helper, new BlockPos(0, 2, 2), 120);
        helper.setBlock(new BlockPos(1, 2, 2), block("wirecopper"));
        helper.setBlock(new BlockPos(2, 2, 2), block("wirecopper"));
        helper.setBlock(direct, HamContent.STATION.get().defaultBlockState());

        // mains -> 13.8 V power supply -> station
        BlockPos supplied = new BlockPos(3, 2, 0);
        source(helper, new BlockPos(0, 2, 0), 120);
        helper.setBlock(new BlockPos(1, 2, 0), block("wirecopper"));
        helper.setBlock(new BlockPos(2, 2, 0), HamContent.POWER_SUPPLY.get().defaultBlockState().setValue(com.codinglitch.simpleradio.ham.block.PowerSupplyBlock.FACING, Direction.EAST));
        helper.setBlock(supplied, HamContent.STATION.get().defaultBlockState());

        helper.succeedWhen(() -> {
            StationBlockEntity station = be(helper, supplied);
            StationBlockEntity refused = be(helper, direct);
            check(helper, station.power.getStored() > 100, "station charged through the power supply (" + station.power.getStored() + " J)");
            check(helper, StationBlock.isOn(helper.getBlockState(supplied)), "station on");
            check(helper, refused.power.getStored() == 0, "120 V straight into the station is refused (" + refused.power.getStored() + " J)");
            check(helper, refused.power.hasWrongVoltage(helper.getLevel().getGameTime()), "the station reports the wrong voltage");
            check(helper, helper.getBlockState(new BlockPos(2, 2, 0)).getValue(com.codinglitch.simpleradio.ham.block.PowerSupplyBlock.ON), "the power supply shows it is delivering");
        });
    }

    /**
     * One power supply puts 13.8 V on a line of Electrodynamics wire; every radio on the line runs from it.
     */
    static void dcLineFeedsEverything(GameTestHelper helper) {
        source(helper, new BlockPos(0, 2, 0), 120);
        helper.setBlock(new BlockPos(1, 2, 0), block("wirecopper"));
        helper.setBlock(new BlockPos(2, 2, 0), HamContent.POWER_SUPPLY.get().defaultBlockState().setValue(com.codinglitch.simpleradio.ham.block.PowerSupplyBlock.FACING, Direction.EAST));
        for (BlockPos wire : List.of(new BlockPos(3, 2, 0), new BlockPos(4, 2, 0), new BlockPos(5, 2, 0), new BlockPos(4, 2, 1), new BlockPos(4, 2, 2))) {
            helper.setBlock(wire, block("wirecopper"));
        }
        BlockPos stationPos = new BlockPos(6, 2, 0), repeaterPos = new BlockPos(4, 2, 3), scannerPos = new BlockPos(3, 2, 2);
        BlockPos chargerPos = new BlockPos(3, 2, 1), radioPos = new BlockPos(5, 2, 1);
        helper.setBlock(stationPos, HamContent.STATION.get().defaultBlockState());
        helper.setBlock(repeaterPos, HamContent.REPEATER.get().defaultBlockState());
        helper.setBlock(scannerPos, HamContent.SCANNER.get().defaultBlockState());
        helper.setBlock(chargerPos, HamContent.CHARGER.get().defaultBlockState());
        helper.setBlock(radioPos, SimpleRadioBlocks.RADIO.defaultBlockState());

        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            for (BlockPos pos : List.of(stationPos, repeaterPos, scannerPos, chargerPos, radioPos)) {
                BlockEntity blockEntity = helper.getBlockEntity(pos);
                check(helper, blockEntity instanceof com.codinglitch.simpleradio.core.central.Powered, "the radio at " + pos.toShortString() + " is still there");
                var power = ((com.codinglitch.simpleradio.core.central.Powered) blockEntity).getPowerBuffer();
                check(helper, power.getStored() > 50, blockEntity.getClass().getSimpleName() + " is fed by the 13.8 V line (" + power.getStored() + " J)");
                check(helper, !power.hasWrongVoltage(now), blockEntity.getClass().getSimpleName() + " accepts 13.8 V");
            }
        });
    }

    static void overVoltage(GameTestHelper helper) {
        source(helper, new BlockPos(0, 2, 0), 240);
        helper.setBlock(new BlockPos(1, 2, 0), block("wirecopper"));
        helper.setBlock(new BlockPos(2, 2, 0), SimpleRadioBlocks.RADIO.defaultBlockState());

        helper.succeedWhen(() -> check(helper, !helper.getBlockState(new BlockPos(2, 2, 0)).is(SimpleRadioBlocks.RADIO), "radio destroyed by 240 V"));
    }

    static void interferenceCutsBareWires(GameTestHelper helper) {
        // bare network: source -> bare copper -> radio
        BlockPos bareRadio = new BlockPos(3, 2, 0);
        source(helper, new BlockPos(0, 2, 0), 120);
        helper.setBlock(new BlockPos(1, 2, 0), block("wirecopper"));
        helper.setBlock(new BlockPos(2, 2, 0), block("wirecopper"));
        helper.setBlock(bareRadio, SimpleRadioBlocks.RADIO.defaultBlockState());

        // insulated network: source -> insulated copper -> radio
        BlockPos insulatedRadio = new BlockPos(3, 2, 2);
        source(helper, new BlockPos(0, 2, 2), 120);
        helper.setBlock(new BlockPos(1, 2, 2), block("wireinsulatedcopperblack"));
        helper.setBlock(new BlockPos(2, 2, 2), block("wireinsulatedcopperblack"));
        helper.setBlock(insulatedRadio, SimpleRadioBlocks.RADIO.defaultBlockState());

        // a 100 W station powered separately, keyed with a CW key
        BlockPos stationPos = new BlockPos(1, 2, 4);
        BlockPos keyPos = new BlockPos(2, 2, 4);
        helper.setBlock(stationPos, HamContent.STATION.get().defaultBlockState());
        // a vertical for 40 m (9 blocks, as high as antenna blocks stand): without an antenna nothing would be radiated
        for (int i = 1; i <= 9; i++) helper.setBlock(stationPos.above(i), SimpleRadioBlocks.ANTENNA.defaultBlockState());
        helper.setBlock(keyPos, HamContent.CW_KEY.get().defaultBlockState());
        StationBlockEntity station = be(helper, stationPos);
        station.applySettings("007.030", Frequency.Modulation.AMPLITUDE, 0, 100);
        HamTests.power(station, 10000);

        for (BlockPos radio : List.of(bareRadio, insulatedRadio)) {
            RadioBlockEntity entity = be(helper, radio);
            entity.frequency = RadioManager.getInstance().frequencies().getOrCreate("144.900", Frequency.Modulation.FREQUENCY);
        }

        double[] before = new double[2];
        helper.runAtTickTime(60, () -> {
            RadioBlockEntity bare = be(helper, bareRadio);
            RadioBlockEntity insulated = be(helper, insulatedRadio);
            check(helper, bare.power.getStored() > 500, "bare wired radio charged before transmitting (" + bare.power.getStored() + ")");
            check(helper, insulated.power.getStored() > 500, "insulated wired radio charged before transmitting");
            CwKeyBlock.setPressed(helper.getLevel(), helper.absolutePos(keyPos), true);
        });

        helper.runAtTickTime(75, () -> {
            GenericTileWire bareWire = be(helper, new BlockPos(1, 2, 0));
            GenericTileWire insulatedWire = be(helper, new BlockPos(1, 2, 2));
            check(helper, ElectrodynamicsInterference.isDisrupted(bareWire.getNetwork()), "bare wire network disrupted by the transmission");
            check(helper, !ElectrodynamicsInterference.isDisrupted(insulatedWire.getNetwork()), "insulated wire network unaffected");

            before[0] = ((RadioBlockEntity) be(helper, bareRadio)).power.getStored();
            before[1] = ((RadioBlockEntity) be(helper, insulatedRadio)).power.getStored();
        });

        helper.runAtTickTime(105, () -> {
            double bareNow = ((RadioBlockEntity) be(helper, bareRadio)).power.getStored();
            double insulatedNow = ((RadioBlockEntity) be(helper, insulatedRadio)).power.getStored();
            check(helper, bareNow < before[0] - 10, "bare wired radio no longer receives power (" + before[0] + " -> " + bareNow + ")");
            check(helper, insulatedNow >= before[1] - 1, "insulated wired radio still receives power (" + before[1] + " -> " + insulatedNow + ")");

            CwKeyBlock.setPressed(helper.getLevel(), helper.absolutePos(keyPos), false);
            helper.succeed();
        });
    }
}
