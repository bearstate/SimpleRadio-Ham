package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.SimpleRadioBlocks;
import com.codinglitch.simpleradio.core.registry.SimpleRadioItems;
import com.codinglitch.simpleradio.ham.HamContent;
import com.codinglitch.simpleradio.ham.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Development aid (only with -Dsimpleradio.guiShots=true): in a single player world it builds a small shack,
 * opens every radio screen and saves a screenshot of each into screenshots/, then quits.
 */
public final class GuiShots {
    public static final boolean ENABLED = Boolean.getBoolean("simpleradio.guiShots");

    private record Step(int delay, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = 0;
    private static int wait = 0;
    private static boolean started = false;
    private static BlockPos origin = BlockPos.ZERO;
    private static com.codinglitch.simpleradio.radio.RadioTransmitter transmitter;

    private GuiShots() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) return;

        if (!started) {
            started = true;
            plan(minecraft);
            wait = 100; // let the world load
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= STEPS.size()) return;

        Step step = STEPS.get(index++);
        try {
            step.action().run();
        } catch (Exception exception) {
            CommonSimpleRadio.warn("GUI shot step {} failed: {}", index, exception.toString());
        }
        wait = step.delay();
    }

    private static void then(int delay, Runnable action) {
        STEPS.add(new Step(delay, action));
    }

    private static void onServer(Minecraft minecraft, Consumer<ServerPlayer> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        UUID id = minecraft.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) action.accept(player);
        });
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, "radio_" + name + ".png", minecraft.getMainRenderTarget(),
                message -> CommonSimpleRadio.info("GUI shot {}: {}", name, message.getString()));
    }

    private static void open(Minecraft minecraft, BlockPos relative) {
        onServer(minecraft, player -> {
            BlockEntity blockEntity = player.level().getBlockEntity(origin.offset(relative));
            if (blockEntity instanceof MenuProvider provider) NetworkHooks.openScreen(player, provider, blockEntity.getBlockPos());
        });
    }

    private static void plan(Minecraft minecraft) {
        // ---- the shack: a row of radios south of the player ----
        then(60, () -> onServer(minecraft, player -> {
            ServerLevel level = player.serverLevel();
            origin = player.blockPosition();
            player.setGameMode(GameType.CREATIVE);
            level.setDayTime(6000);
            level.setWeatherParameters(12000, 0, false, false);

            // a glass room, so water or sand around the spawn cannot get in
            for (int x = -6; x <= 10; x++) for (int z = -3; z <= 9; z++) {
                boolean wall = x == -6 || x == 10 || z == -3 || z == 9;
                level.setBlock(origin.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 6; y++) {
                    level.setBlock(origin.offset(x, y, z), (wall || y == 6 ? Blocks.GLASS : Blocks.AIR).defaultBlockState(), 3);
                }
            }

            BlockPos station = origin.offset(0, 0, 4);
            place(level, station, HamContent.STATION.get().defaultBlockState().setValue(StationBlock.FACING, Direction.NORTH));
            place(level, station.above(), SimpleRadioBlocks.ANTENNA.defaultBlockState());
            place(level, station.above(2), SimpleRadioBlocks.ANTENNA.defaultBlockState());
            place(level, station.above(3), HamContent.YAGI_ANTENNA.get().defaultBlockState());
            place(level, origin.offset(-2, 0, 4), HamContent.SSTV_TERMINAL.get().defaultBlockState().setValue(SstvTerminalBlock.FACING, Direction.NORTH));
            place(level, origin.offset(1, 0, 4), HamContent.ANTENNA_TUNER.get().defaultBlockState().setValue(AntennaTunerBlock.FACING, Direction.NORTH));
            place(level, origin.offset(-1, 0, 3), HamContent.DESK_MICROPHONE.get().defaultBlockState().setValue(DeskMicrophoneBlock.FACING, Direction.NORTH));
            place(level, origin.offset(1, 0, 2), HamContent.CW_KEY.get().defaultBlockState());
            place(level, origin.offset(3, 0, 4), HamContent.REPEATER.get().defaultBlockState());
            place(level, origin.offset(5, 0, 4), HamContent.SCANNER.get().defaultBlockState());
            place(level, origin.offset(4, 0, 2), HamContent.FOX_TRANSMITTER.get().defaultBlockState());
            place(level, origin.offset(7, 0, 4), HamContent.POWER_SUPPLY.get().defaultBlockState().setValue(PowerSupplyBlock.FACING, Direction.SOUTH));
            place(level, origin.offset(-4, 0, 4), HamContent.CHARGER.get().defaultBlockState());

            for (BlockPos pos : List.of(station, origin.offset(3, 0, 4), origin.offset(5, 0, 4), origin.offset(4, 0, 2))) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity != null) blockEntity.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).ifPresent(energy -> energy.receiveEnergy(1_000_000, false));
            }
            if (level.getBlockEntity(station) instanceof StationBlockEntity stationEntity) {
                stationEntity.applySettings("145.500", Frequency.Modulation.FREQUENCY, 0, 25);
            }

            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(SimpleRadioItems.TRANSCEIVER));
            player.getInventory().setItem(1, new ItemStack(HamContent.APRS_RADIO.get()));
            player.getInventory().setItem(2, new ItemStack(HamContent.DIRECTION_FINDER.get()));
            player.getInventory().setItem(3, new ItemStack(HamContent.MOBILE_RADIO.get()));
            player.getInventory().setItem(4, new ItemStack(HamContent.HAND_MICROPHONE.get()));
            player.getInventory().setItem(5, new ItemStack(HamContent.LICENCE_EXAM.get()));
            player.getInventory().setItem(6, new ItemStack(HamContent.RADIO_MANUAL.get()));
            player.getInventory().setItem(7, new ItemStack(HamContent.BATTERY.get()));
            player.getInventory().setItem(8, new ItemStack(HamContent.COAX_CABLE.get()));
            player.getInventory().selected = 0;
            player.teleportTo(level, origin.getX() + 0.5, origin.getY(), origin.getZ() - 0.5, 0, 15);
        }));

        // ---- the world, the hotbar and the handheld display ----
        then(20, () -> {});
        then(10, () -> shot(minecraft, "world"));

        // ---- the S-meter of an original radio block, with a transmitter nearby ----
        then(10, () -> onServer(minecraft, player -> {
            ServerLevel level = player.serverLevel();
            BlockPos radioPos = origin.offset(0, 0, 2);
            place(level, radioPos, SimpleRadioBlocks.RADIO.defaultBlockState());
            if (level.getBlockEntity(radioPos) instanceof com.codinglitch.simpleradio.core.registry.blocks.RadioBlockEntity radio) {
                radio.frequency = com.codinglitch.simpleradio.radio.RadioManager.getInstance().frequencies().getOrCreate("145.500", Frequency.Modulation.FREQUENCY);
                radio.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN).ifPresent(energy -> energy.receiveEnergy(1_000_000, false));
            }
            Frequency frequency = com.codinglitch.simpleradio.radio.RadioManager.getInstance().frequencies().getOrCreate("145.500", Frequency.Modulation.FREQUENCY);
            transmitter = new com.codinglitch.simpleradio.radio.RadioTransmitter(frequency,
                    com.codinglitch.simpleradio.central.WorldlyPosition.of(origin.offset(-4, 1, 6), level), UUID.randomUUID());
            transmitter.frequencingType(com.codinglitch.simpleradio.core.registry.SimpleRadioFrequencing.WALKIE_TALKIE);
            frequency.addTransmitter(transmitter);
            player.teleportTo(level, origin.getX() + 0.5, origin.getY(), origin.getZ() - 0.5, 0, 22);
        }));
        for (int i = 0; i < 25; i++) {
            then(1, () -> onServer(minecraft, player -> {
                if (transmitter != null) {
                    transmitter.accept(new com.codinglitch.simpleradio.radio.RadioMessage(transmitter.getReference(), transmitter.getLocation(),
                            tone(), 1f));
                }
            }));
        }
        then(2, () -> shot(minecraft, "block_smeter"));
        then(5, () -> onServer(minecraft, player -> {
            if (transmitter != null && transmitter.frequency != null) transmitter.frequency.removeTransmitter(transmitter);
            transmitter = null;
            player.teleportTo(player.serverLevel(), origin.getX() + 0.5, origin.getY(), origin.getZ() - 0.5, 0, 15);
        }));

        // ---- screens ----
        then(15, () -> minecraft.setScreen(new ManualScreen(0)));
        then(5, () -> shot(minecraft, "manual_start"));
        then(15, () -> minecraft.setScreen(new ManualScreen(7)));
        then(5, () -> shot(minecraft, "manual_propagation"));
        then(15, () -> minecraft.setScreen(new HandheldScreen(InteractionHand.MAIN_HAND)));
        then(5, () -> shot(minecraft, "handheld"));
        then(15, () -> {
            minecraft.setScreen(null);
            HamClientPackets.openExam(new int[]{0, 7, 13}, new int[][]{{0, 1, 2, 3}, {3, 2, 1, 0}, {1, 0, 3, 2}});
        });
        then(5, () -> shot(minecraft, "exam"));
        then(10, () -> {
            minecraft.setScreen(null);
            minecraft.player.getInventory().selected = 1; // the APRS handheld
        });
        then(15, () -> minecraft.setScreen(new AprsScreen(InteractionHand.MAIN_HAND)));
        then(5, () -> shot(minecraft, "aprs"));
        then(5, () -> {
            minecraft.setScreen(null);
            minecraft.player.getInventory().selected = 0;
        });

        then(20, () -> open(minecraft, new BlockPos(0, 0, 4)));
        then(5, () -> shot(minecraft, "station"));
        then(5, () -> minecraft.player.closeContainer());
        then(20, () -> open(minecraft, new BlockPos(3, 0, 4)));
        then(5, () -> shot(minecraft, "repeater"));
        then(5, () -> minecraft.player.closeContainer());
        then(20, () -> open(minecraft, new BlockPos(5, 0, 4)));
        then(5, () -> shot(minecraft, "scanner"));
        then(5, () -> minecraft.player.closeContainer());
        then(20, () -> open(minecraft, new BlockPos(-2, 0, 4)));
        then(5, () -> shot(minecraft, "sstv"));
        then(5, () -> minecraft.player.closeContainer());

        // ---- the inventory with the new items ----
        then(10, () -> minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player)));
        then(5, () -> shot(minecraft, "inventory"));
        then(5, () -> minecraft.setScreen(null));
        then(20, () -> {
            CommonSimpleRadio.info("GUI shots done");
            minecraft.stop();
        });
    }

    private static short[] tone() {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 700 / 48000) * 12000);
        return pcm;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
