package com.codinglitch.simpleradio.ham.licence;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Every call sign on the server belongs to exactly one player (also while they are offline).
 */
public class CallsignRegistry extends SavedData {
    private static final String NAME = "simpleradio_callsigns";

    private final Map<String, UUID> owners = new HashMap<>();
    private final Map<UUID, String> callsigns = new HashMap<>();

    public static CallsignRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CallsignRegistry::load, CallsignRegistry::new, NAME);
    }

    public static String clean(String callsign) {
        return callsign == null ? "" : callsign.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9/]", "");
    }

    @Nullable
    public String callsignOf(UUID player) {
        return callsigns.get(player);
    }

    @Nullable
    public UUID ownerOf(String callsign) {
        return owners.get(clean(callsign));
    }

    /**
     * @return false when another player already has this call sign
     */
    public boolean set(UUID player, String callsign) {
        String clean = clean(callsign);
        if (clean.isEmpty() || clean.length() > 12) return false;

        UUID owner = owners.get(clean);
        if (owner != null && !owner.equals(player)) return false;

        String previous = callsigns.put(player, clean);
        if (previous != null) owners.remove(previous);
        owners.put(clean, player);
        setDirty();
        return true;
    }

    /**
     * A new call sign like TA3KQ: prefix, region digit and a two or three letter suffix.
     */
    public String assign(UUID player) {
        String existing = callsigns.get(player);
        if (existing != null) return existing;

        String prefix = clean(SimpleRadioLibrary.SERVER_CONFIG.band_plan.callsignPrefix);
        if (prefix.isEmpty()) prefix = "TA";
        Random random = new Random(player.getMostSignificantBits() ^ System.nanoTime());

        for (int attempt = 0; attempt < 10_000; attempt++) {
            StringBuilder callsign = new StringBuilder(prefix).append(1 + random.nextInt(9));
            int letters = attempt < 5_000 ? 2 : 3;
            for (int i = 0; i < letters; i++) callsign.append((char) ('A' + random.nextInt(26)));

            if (set(player, callsign.toString())) return callsign.toString();
        }
        String fallback = prefix + "0" + player.toString().substring(0, 4).toUpperCase(Locale.ROOT);
        set(player, fallback);
        return fallback;
    }

    public void remove(UUID player) {
        String previous = callsigns.remove(player);
        if (previous != null) owners.remove(previous);
        setDirty();
    }

    public static CallsignRegistry load(CompoundTag tag) {
        CallsignRegistry registry = new CallsignRegistry();
        for (Tag entry : tag.getList("callsigns", Tag.TAG_COMPOUND)) {
            CompoundTag compound = (CompoundTag) entry;
            if (!compound.hasUUID("player")) continue;
            UUID player = compound.getUUID("player");
            String callsign = clean(compound.getString("callsign"));
            if (callsign.isEmpty()) continue;
            registry.callsigns.put(player, callsign);
            registry.owners.put(callsign, player);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        callsigns.forEach((player, callsign) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putString("callsign", callsign);
            list.add(entry);
        });
        tag.put("callsigns", list);
        return tag;
    }
}
