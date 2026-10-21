package com.codinglitch.simpleradio.ham.licence;

import com.codinglitch.simpleradio.ham.network.HamNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Amateur radio licences. Stored with the player (kept through death) and mirrored to the client
 * so screens can show the operator's call sign.
 */
public final class Licences {
    private static final String ROOT = "simpleradio";

    /** The client's own licence, as last sent by the server. */
    public static boolean clientLicensed = false;
    public static String clientCallsign = "";

    private Licences() {}

    private static CompoundTag data(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(Player.PERSISTED_NBT_TAG)) persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT)) persisted.put(ROOT, new CompoundTag());
        return persisted.getCompound(ROOT);
    }

    public static boolean isLicensed(Player player) {
        if (player.level().isClientSide) return clientLicensed;
        return data(player).getBoolean("licensed");
    }

    public static String callsign(Player player) {
        if (player.level().isClientSide) return clientCallsign;
        if (player instanceof ServerPlayer serverPlayer) {
            String registered = CallsignRegistry.get(serverPlayer.server).callsignOf(player.getUUID());
            if (registered != null) return registered;
        }
        return data(player).getString("callsign");
    }

    /**
     * The call sign if the player has one, otherwise their name (for identification over the air).
     */
    public static String identity(Player player) {
        String callsign = callsign(player);
        return callsign.isEmpty() ? player.getGameProfile().getName() : callsign;
    }

    public static String grant(ServerPlayer player) {
        String callsign = CallsignRegistry.get(player.server).assign(player.getUUID());
        CompoundTag data = data(player);
        data.putBoolean("licensed", true);
        data.putString("callsign", callsign);
        sync(player);
        return callsign;
    }

    public static void revoke(ServerPlayer player) {
        data(player).putBoolean("licensed", false);
        sync(player);
    }

    /**
     * @return false when another player has that call sign already
     */
    public static boolean setCallsign(ServerPlayer player, String callsign) {
        if (!CallsignRegistry.get(player.server).set(player.getUUID(), callsign)) return false;
        data(player).putString("callsign", CallsignRegistry.clean(callsign));
        sync(player);
        return true;
    }

    // ---- Exam ---- \\

    public static long examRetryAt(Player player) {
        return data(player).getLong("exam_retry");
    }

    public static void setExamRetryAt(Player player, long gameTime) {
        data(player).putLong("exam_retry", gameTime);
    }

    // ---- Sync ---- \\

    public static void sync(ServerPlayer player) {
        HamNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HamNetwork.LicenceSync(isLicensed(player), callsign(player)));
    }

    public static void onLogin(ServerPlayer player) {
        // keep the registry and the player's copy in step (e.g. after an operator changed it while offline)
        String stored = data(player).getString("callsign");
        CallsignRegistry registry = CallsignRegistry.get(player.server);
        if (registry.callsignOf(player.getUUID()) == null && !stored.isEmpty()) registry.set(player.getUUID(), stored);
        sync(player);
    }

    @Nullable
    public static String callsignOf(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
        return CallsignRegistry.get(server).callsignOf(player);
    }
}
