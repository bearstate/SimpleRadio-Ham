package com.codinglitch.simpleradio.ham.radio;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.radio.RadioManager;
import com.codinglitch.simpleradio.radio.RadioMessage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repeaters linked over the "internet" (like EchoLink / IRLP): everything one of them hears is also
 * transmitted by every other repeater with the same node number, however far away, after a short delay.
 */
public final class RepeaterLinks {
    public interface Node {
        RepeaterTransmitter linkTransmitter();
    }

    private static final Map<Integer, Set<Node>> NODES = new ConcurrentHashMap<>();

    private RepeaterLinks() {}

    public static void join(int linkId, Node node) {
        if (linkId <= 0) return;
        NODES.computeIfAbsent(linkId, id -> ConcurrentHashMap.newKeySet()).add(node);
    }

    public static void leave(int linkId, Node node) {
        Set<Node> nodes = NODES.get(linkId);
        if (nodes == null) return;
        nodes.remove(node);
        if (nodes.isEmpty()) NODES.remove(linkId);
    }

    public static int members(int linkId) {
        Set<Node> nodes = NODES.get(linkId);
        return nodes == null ? 0 : nodes.size();
    }

    public static void forward(int linkId, Node from, RadioMessage message) {
        Set<Node> nodes = NODES.get(linkId);
        if (nodes == null) return;

        float delay = (float) Math.max(0, SimpleRadioLibrary.SERVER_CONFIG.repeater.linkDelay);
        for (Node node : nodes) {
            if (node == from) continue;
            RepeaterTransmitter transmitter = node.linkTransmitter();
            if (transmitter == null) continue;

            RadioMessage copy = message.copy();
            copy.linked = true;
            RadioManager.getInstance().sendMessage(copy, transmitter, delay);
        }
    }

    public static void clear() {
        NODES.clear();
    }
}
