package com.codinglitch.simpleradio.ham;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.block.CwKeyBlock;
import com.codinglitch.simpleradio.ham.block.DeskMicrophoneBlock;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.compat.Interference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side bookkeeping of who is keying which station (push-to-talk) and which CW key.
 */
public class HamEvents {
    private static final Map<UUID, GlobalPos> PTT = new HashMap<>();
    private static final Map<UUID, GlobalPos> CW_KEYS = new HashMap<>();
    private static final Map<UUID, GlobalPos> MICROPHONES = new HashMap<>();

    public static void setPushToTalk(ServerPlayer player, boolean down) {
        if (!down) {
            releasePushToTalk(player.getUUID(), player.server);
            return;
        }

        StationBlockEntity station = findNearestStation(player);
        if (station == null) {
            player.displayClientMessage(Component.translatable("message.simpleradio.no_station"), true);
            return;
        }

        // refusals (no power, no licence, ...) are explained by the station itself
        if (!keyStation(player, station) && station.isBusyFor(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.simpleradio.station_busy"), true);
        }
    }

    /**
     * Keys a station for a player (hand microphone, desk microphone).
     * @return false when the station is busy or refuses (no power, no licence, ...)
     */
    public static boolean keyStation(ServerPlayer player, StationBlockEntity station) {
        if (!station.key(player)) return false;
        PTT.put(player.getUUID(), GlobalPos.of(player.level().dimension(), station.getBlockPos()));
        return true;
    }

    public static void releasePushToTalk(ServerPlayer player) {
        releasePushToTalk(player.getUUID(), player.server);
        GlobalPos microphone = MICROPHONES.remove(player.getUUID());
        if (microphone != null) {
            ServerLevel level = player.server.getLevel(microphone.dimension());
            if (level != null && level.isLoaded(microphone.pos())) DeskMicrophoneBlock.setPressed(level, microphone.pos(), false);
        }
    }

    /**
     * Holding the use key on a desk microphone keys the station next to it.
     */
    public static void setMicKey(ServerPlayer player, BlockPos pos, boolean down) {
        if (!down) {
            releasePushToTalk(player);
            return;
        }

        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof DeskMicrophoneBlock)) return;
        if (player.distanceToSqr(pos.getCenter()) > 36) return;

        StationBlockEntity station = DeskMicrophoneBlock.findStation(level, pos);
        if (station == null) {
            player.displayClientMessage(Component.translatable("message.simpleradio.no_station"), true);
            return;
        }
        if (keyStation(player, station)) {
            MICROPHONES.put(player.getUUID(), GlobalPos.of(level.dimension(), pos));
            DeskMicrophoneBlock.setPressed(level, pos, true);
        }
    }

    private static void releasePushToTalk(UUID player, net.minecraft.server.MinecraftServer server) {
        GlobalPos keyed = PTT.remove(player);
        if (keyed == null) return;

        ServerLevel level = server.getLevel(keyed.dimension());
        if (level != null && level.isLoaded(keyed.pos()) && level.getBlockEntity(keyed.pos()) instanceof StationBlockEntity station) {
            station.unkey(player);
        }
    }

    public static void setCwKey(ServerPlayer player, BlockPos pos, boolean down) {
        ServerLevel level = player.serverLevel();

        if (!down) {
            GlobalPos keyed = CW_KEYS.remove(player.getUUID());
            if (keyed != null && keyed.dimension() == level.dimension() && level.isLoaded(keyed.pos())) {
                CwKeyBlock.setPressed(level, keyed.pos(), false);
            }
            return;
        }

        if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof CwKeyBlock)) return;
        if (player.distanceToSqr(pos.getCenter()) > 36) return;

        GlobalPos previous = CW_KEYS.put(player.getUUID(), GlobalPos.of(level.dimension(), pos));
        if (previous != null && !previous.pos().equals(pos) && level.isLoaded(previous.pos())) {
            CwKeyBlock.setPressed(level, previous.pos(), false);
        }
        CwKeyBlock.setPressed(level, pos, true, player);
    }

    private static StationBlockEntity findNearestStation(ServerPlayer player) {
        double range = SimpleRadioLibrary.SERVER_CONFIG.station.pttRange;
        int radius = (int) Math.ceil(range);
        BlockPos origin = player.blockPosition();

        StationBlockEntity closest = null;
        double closestDistance = range * range;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius + 1, radius))) {
            BlockEntity blockEntity = player.level().getBlockEntity(pos);
            if (blockEntity instanceof StationBlockEntity station) {
                double distance = player.distanceToSqr(pos.getCenter());
                if (distance <= closestDistance) {
                    closestDistance = distance;
                    closest = station;
                }
            }
        }
        return closest;
    }

    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.codinglitch.simpleradio.ham.licence.Licences.onLogin(player);
            giveManual(player);
            // the client shows the server's rules (battery, band plan, power) in its screens and tooltips
            com.codinglitch.simpleradio.ham.network.HamNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.codinglitch.simpleradio.ham.network.HamNetwork.ConfigSync(com.codinglitch.simpleradio.radio.ConfigSnapshot.capture(com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG)));
        }
    }

    private static final String MANUAL_GIVEN = "simpleradio_manual_given";

    private static void giveManual(ServerPlayer player) {
        if (!com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG.guide.giveOnFirstJoin) return;
        net.minecraft.nbt.CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.CompoundTag persisted = data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(MANUAL_GIVEN)) return;

        persisted.putBoolean(MANUAL_GIVEN, true);
        data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        net.minecraft.world.item.ItemStack manual = new net.minecraft.world.item.ItemStack(HamContent.RADIO_MANUAL.get());
        if (!player.getInventory().add(manual)) player.drop(manual, false);
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            com.codinglitch.simpleradio.ham.licence.LicenceExam.forget(player.getUUID());
            releasePushToTalk(player);
            setCwKey(player, BlockPos.ZERO, false);
        }
    }

    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.getServer().getTickCount() % 20 != 0) return;
        com.codinglitch.simpleradio.radio.CommonRadioPlugin.removeAllGroups(event.getServer().getPlayerList().getPlayers());
    }

    // remember the surface of unloading chunks so radio paths over them still see what players built there
    public static void onChunkUnload(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.codinglitch.simpleradio.radio.propagation.Terrain.recordChunk(level, event.getChunk());
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PTT.clear();
        CW_KEYS.clear();
        MICROPHONES.clear();
        Interference.clear();
        com.codinglitch.simpleradio.radio.propagation.Terrain.clear();
        com.codinglitch.simpleradio.radio.propagation.Environment.clear();
    }
}
