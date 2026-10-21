package com.codinglitch.simpleradio.ham.client;

import net.minecraft.client.Minecraft;

/**
 * Client side handling of server-to-client packets (only class-loaded on the client).
 */
public final class HamClientPackets {
    private HamClientPackets() {}

    public static void openExam(int[] questions, int[][] orders) {
        if (questions.length == 0 || orders.length != questions.length) return;
        Minecraft.getInstance().setScreen(new ExamScreen(questions, orders));
    }

    public static void aprsLog(java.util.List<com.codinglitch.simpleradio.ham.network.HamNetwork.AprsEntry> entries) {
        if (Minecraft.getInstance().screen instanceof AprsScreen screen) screen.update(entries);
    }

    // the preview can arrive before the screen is ready
    private static net.minecraft.core.BlockPos previewPos;
    private static byte[] previewColors;

    public static void sstvPreview(net.minecraft.core.BlockPos pos, byte[] colors) {
        previewPos = pos;
        previewColors = colors;
        if (Minecraft.getInstance().screen instanceof SstvScreen screen && screen.getMenu().pos.equals(pos)) screen.setPicture(colors);
    }

    public static byte[] takePreview(net.minecraft.core.BlockPos pos) {
        return pos.equals(previewPos) ? previewColors : null;
    }

    // the client's own values of the server config, put back when leaving the server
    private static java.util.Map<String, String> ownServerConfig;

    public static void applyServerConfig(java.util.Map<String, String> values) {
        // in single player the integrated server uses the very same config object
        if (Minecraft.getInstance().hasSingleplayerServer()) return;
        var config = com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG;
        if (ownServerConfig == null) ownServerConfig = com.codinglitch.simpleradio.radio.ConfigSnapshot.capture(config);
        int applied = com.codinglitch.simpleradio.radio.ConfigSnapshot.apply(config, values);
        com.codinglitch.simpleradio.CommonSimpleRadio.info("Using the server's radio config ({} values)", applied);
    }

    public static void restoreOwnServerConfig() {
        if (ownServerConfig == null) return;
        com.codinglitch.simpleradio.radio.ConfigSnapshot.apply(com.codinglitch.simpleradio.SimpleRadioLibrary.SERVER_CONFIG, ownServerConfig);
        ownServerConfig = null;
    }

    public static void examResult(boolean passed, int score, int total, String callsign) {
        if (Minecraft.getInstance().screen instanceof ExamScreen exam) exam.showResult(passed, score, total, callsign);
    }
}
