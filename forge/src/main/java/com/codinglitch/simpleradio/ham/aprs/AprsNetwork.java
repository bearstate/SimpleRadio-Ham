package com.codinglitch.simpleradio.ham.aprs;

import com.codinglitch.simpleradio.SimpleRadioLibrary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every APRS radio has decoded: the last position of each station it heard (server side).
 */
public final class AprsNetwork {
    /**
     * A station as last heard by one receiver.
     * @param signalDb how strong the packet arrived
     */
    public record Heard(AprsPacket packet, float signalDb, long heardAt) {}

    private static final Map<UUID, Map<String, Heard>> LOGS = new ConcurrentHashMap<>();

    private AprsNetwork() {}

    public static void heard(UUID listener, AprsPacket packet, float signalDb, long gameTime) {
        LOGS.computeIfAbsent(listener, key -> new ConcurrentHashMap<>()).put(packet.callsign(), new Heard(packet, signalDb, gameTime));
    }

    /**
     * Stations heard by a player's APRS radio, newest first, without the ones heard too long ago.
     */
    public static List<Heard> log(UUID listener, long gameTime) {
        Map<String, Heard> log = LOGS.get(listener);
        if (log == null) return List.of();

        long maxAge = SimpleRadioLibrary.SERVER_CONFIG.aprs.retentionMinutes * 1200L;
        log.values().removeIf(heard -> gameTime - heard.heardAt() > maxAge);

        List<Heard> list = new ArrayList<>(log.values());
        list.sort(Comparator.comparingLong(Heard::heardAt).reversed());
        return list.subList(0, Math.min(64, list.size()));
    }

    public static void clear() {
        LOGS.clear();
    }
}
