package com.codinglitch.simpleradio.gametest.tests;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.ServerSimpleRadioApi;
import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.WorldlyPosition;
import com.codinglitch.simpleradio.core.registry.SimpleRadioEntities;
import com.codinglitch.simpleradio.gametest.SimpleRadioTestHelper;
import com.codinglitch.simpleradio.gametest.TestHolder;
import com.codinglitch.simpleradio.radio.RadioSpeaker;
import com.codinglitch.simpleradio.radio.propagation.Environment;
import com.codinglitch.simpleradio.radio.propagation.Terrain;
import com.codinglitch.simpleradio.routers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.phys.AABB;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@TestHolder(
        namespace = CommonSimpleRadio.ID,
        dir = "system",

        name = "systems"
)
public class SystemTest {
    /**
     * These tests check the routing core, not the realism added on top of it: the test structures sit deep
     * underground, the original blocks would need electricity and "000.00" is outside every amateur band.
     */
    private static void prepare() {
        var config = SimpleRadioLibrary.SERVER_CONFIG;
        config.power.requirePower = false;
        config.propagation.terrainEnabled = false;
        config.propagation.environmentEnabled = false;
        config.band_plan.enforce = false;
        Terrain.clear();
        Environment.clear();
    }

    /**
     * 20 ms of a 700 Hz tone. (Empty audio is never routed: it only resets the sender's decoder.)
     */
    private static short[] tone() {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 700 / 48000) * 12000);
        return pcm;
    }

    /**
     * Keeps talking into the microphone at {@code from} until the speaker at {@code to} plays something.
     * Audio is relayed along wires with real-time delays, so the test waits for the audio instead of a tick count.
     */
    private static void talkUntilHeard(SimpleRadioTestHelper helper, BlockPos from, BlockPos to, int firstTick) {
        AtomicBoolean received = new AtomicBoolean(false);
        AtomicBoolean hooked = new AtomicBoolean(false);
        AtomicInteger ticks = new AtomicInteger();
        UUID sender = UUID.randomUUID();

        helper.onEachTick(() -> {
            int tick = ticks.incrementAndGet();
            if (received.get()) {
                helper.succeed();
                return;
            }
            if (tick < firstTick) return;

            if (!hooked.get()) {
                // handhelds on the ground build their routers on their first ticks
                try {
                    helper.assertRouter(Listener.class, from);
                    RadioSpeaker speaker = (RadioSpeaker) helper.assertRouter(Speaker.class, to);
                    speaker.acceptCriteria = source -> {
                        received.set(true);
                        return true;
                    };
                    hooked.set(true);
                } catch (net.minecraft.gametest.framework.GameTestAssertException exception) {
                    if (tick > firstTick + 200) throw exception;
                    return;
                }
            }
            if (tick % 5 == 0) {
                ServerSimpleRadioApi.getInstance().sendAudio(WorldlyPosition.of(helper.absolutePos(from), helper.getLevel()), sender, tone());
            }
        });
    }

    private static void killEntities(SimpleRadioTestHelper helper) {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(3, 4, 3)));
        helper.getLevel().getEntities((net.minecraft.world.entity.Entity) null, area, entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::kill);
    }

    @GameTest(template = "all_routers")
    public static void routerRegistry(SimpleRadioTestHelper helper) {
        prepare();
        helper.runAtTickTime(5, () -> {
            helper.assertRouter(Speaker.class, new BlockPos(2, 2, 2));
            helper.assertRouter(Listener.class, new BlockPos(1, 2, 2));
            helper.assertRouter(Router.class, new BlockPos(0, 2, 2));

            helper.assertRouter(Receiver.class, new BlockPos(2, 2, 1));
            helper.assertRouter(Speaker.class, new BlockPos(2, 2, 1));

            helper.assertRouter(Transmitter.class, new BlockPos(1, 2, 1));
            helper.assertRouter(Receiver.class, new BlockPos(0, 2, 1));

            helper.assertRouter(Listener.class, new BlockPos(2, 2, 0));
            helper.assertRouter(Speaker.class, new BlockPos(2, 2, 0));
            helper.assertRouter(Receiver.class, new BlockPos(2, 2, 0));
            helper.assertRouter(Transmitter.class, new BlockPos(2, 2, 0));

            helper.succeed();
        });
    }

    @GameTest(template = "all_routers", timeoutTicks = 400)
    public static void garbageCollection(SimpleRadioTestHelper helper) {
        prepare();
        helper.runAtTickTime(20, () -> {
            helper.destroyBlock(new BlockPos(2, 2, 2));
            helper.destroyBlock(new BlockPos(1, 2, 2));
            helper.destroyBlock(new BlockPos(0, 2, 2));
            helper.destroyBlock(new BlockPos(2, 2, 1));
            helper.destroyBlock(new BlockPos(1, 2, 1));
            helper.destroyBlock(new BlockPos(0, 2, 1));

            // (killAllEntities misses entities standing on the top layer of the structure)
            killEntities(helper);
        });

        // the collector runs once a second; everything has to be gone after a couple of runs
        helper.runAtTickTime(80, () -> {
            helper.assertNoRouter(Speaker.class, new BlockPos(2, 2, 2));
            helper.assertNoRouter(Listener.class, new BlockPos(1, 2, 2));
            helper.assertNoRouter(Router.class, new BlockPos(0, 2, 2));

            helper.assertNoRouter(Receiver.class, new BlockPos(2, 2, 1));
            helper.assertNoRouter(Speaker.class, new BlockPos(2, 2, 1));

            helper.assertNoRouter(Transmitter.class, new BlockPos(1, 2, 1));
            helper.assertNoRouter(Receiver.class, new BlockPos(0, 2, 1));

            helper.assertNoRouter(Listener.class, new BlockPos(2, 2, 0));
            helper.assertNoRouter(Speaker.class, new BlockPos(2, 2, 0));
            helper.assertNoRouter(Receiver.class, new BlockPos(2, 2, 0));
            helper.assertNoRouter(Transmitter.class, new BlockPos(2, 2, 0));

            helper.succeed();
        });
    }

    @GameTest(template = "closed_system", timeoutTicks = 4000)
    public static void closedSystem(SimpleRadioTestHelper helper) {
        prepare();
        talkUntilHeard(helper, new BlockPos(0, 2, 0), new BlockPos(2, 2, 2), 10);
    }

    @GameTest(template = "wire_survival")
    public static void wireSurvival(SimpleRadioTestHelper helper) {
        prepare();
        helper.runAfterDelay(10, () -> {
            helper.assertEntityPresent(SimpleRadioEntities.WIRE, new BlockPos(1, 1, 1), 4);
            helper.succeed();
        });
    }

    @GameTest(template = "open_system", timeoutTicks = 4000)
    public static void openSystem(SimpleRadioTestHelper helper) {
        prepare();
        talkUntilHeard(helper, new BlockPos(0, 2, 0), new BlockPos(2, 2, 0), 10);
    }

    @GameTest(template = "short_circuit", timeoutTicks = 4000)
    public static void shortCircuit(SimpleRadioTestHelper helper) {
        prepare();
        AtomicInteger ticks = new AtomicInteger();
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(3, 4, 3)));

        // audio sent into a loop of wires meets itself (or comes back to where it started): the loop burns through.
        // Which wire goes depends on how fast the audio travels along the wires.
        helper.onEachTick(() -> {
            int tick = ticks.incrementAndGet();
            int wires = helper.getLevel().getEntities(SimpleRadioEntities.WIRE, area, net.minecraft.world.entity.Entity::isAlive).size();
            if (tick < 10) {
                if (tick == 9 && wires != 4) helper.fail("the loop of four wires is intact before sending (" + wires + ")");
                return;
            }
            if (wires < 4) {
                helper.succeed();
                return;
            }
            if (tick % 20 == 10) {
                Router router = helper.assertRouter(Router.class, new BlockPos(0, 2, 1));
                router.send(tone(), 1f);
            }
        });
    }

    @GameTest(template = "transceiver_ground_to_ground", timeoutTicks = 4000)
    public static void transceiverGroundToGround(SimpleRadioTestHelper helper) {
        prepare();
        // the handhelds lie at the two ends of the structure
        talkUntilHeard(helper, new BlockPos(0, 2, 0), new BlockPos(4, 2, 0), 5);
    }
}
