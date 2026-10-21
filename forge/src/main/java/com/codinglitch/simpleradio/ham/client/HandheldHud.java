package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.radio.BandPlan;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;
import java.util.Locale;

/**
 * A small display next to the hotbar while a handheld is in hand: frequency, channel, S-meter,
 * battery, transmit and scan state.
 */
public final class HandheldHud {
    private static final int WIDTH = 124;
    private static final int HEIGHT = 36;

    private static HamNetwork.HandheldStatus status;
    private static long statusTime = 0;

    private HandheldHud() {}

    public static void update(HamNetwork.HandheldStatus newStatus) {
        status = newStatus;
        statusTime = Util.getMillis();
    }

    /**
     * Whether the handheld display is on screen (other overlays keep clear of it).
     */
    static boolean isShown(LocalPlayer player) {
        return SimpleRadioLibrary.CLIENT_CONFIG.hud.handheldHud && !heldHandheld(player).isEmpty();
    }

    private static ItemStack heldHandheld(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof TransceiverItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof TransceiverItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !SimpleRadioLibrary.CLIENT_CONFIG.hud.handheldHud) return;

        ItemStack stack = heldHandheld(player);
        if (stack.isEmpty()) return;

        CompoundTag tag = stack.getOrCreateTag();
        Font font = minecraft.font;

        int x = width / 2 + 96;
        int y = height - HEIGHT - 2;
        if (x + WIDTH > width - 2) { // narrow window: above the hotbar
            x = width / 2 - WIDTH / 2;
            y = height - 60 - HEIGHT;
        }

        HamGui.panel(graphics, x, y, WIDTH, HEIGHT);
        HamGui.display(graphics, x + 3, y + 3, WIDTH - 6, HEIGHT - 6);

        boolean on = TransceiverItem.isOperational(stack);
        boolean transmitting = player.isUsingItem() && player.getUseItem() == stack;

        // line 1: frequency / channel
        String frequency = tag.getString("frequency");
        Frequency.Modulation modulation = Frequency.modulationOf(tag.getString("modulation"));
        String line = HamFrequency.display(frequency).replace(" MHz", "") + " " + (modulation == null ? "" : modulation.display());
        int pmr = BandPlan.pmrChannelOf(frequency);
        List<TransceiverItem.Memory> memories = TransceiverItem.getMemories(tag);
        int channel = TransceiverItem.getChannel(tag);
        String label = "";
        if (channel >= 0 && channel < memories.size()) {
            TransceiverItem.Memory memory = memories.get(channel);
            label = String.format(Locale.ROOT, "CH%02d %s", channel + 1, memory.name());
        } else if (pmr > 0) {
            label = "PMR " + pmr;
        }
        graphics.drawString(font, line, x + 6, y + 6, on ? HamGui.DISPLAY_TEXT : HamGui.DISPLAY_DIM, false);
        if (!label.isEmpty()) {
            String clipped = font.plainSubstrByWidth(label, WIDTH - 12 - font.width(line) - 4);
            graphics.drawString(font, clipped, x + WIDTH - 6 - font.width(clipped), y + 6, HamGui.AMBER, false);
        }

        // line 2: S-meter and TX
        boolean fresh = status != null && Util.getMillis() - statusTime < 1000;
        float signal = fresh ? status.signalDb() : Float.NaN;
        double units = on && !transmitting ? RadioLink.sUnits(signal) : 0;
        int meterX = x + 6, meterY = y + 17;
        for (int segment = 0; segment < 12; segment++) {
            boolean lit = segment < 9 ? units >= segment + 1 : units >= 9 + (segment - 8) * 10.0 / SimpleRadioLibrary.SERVER_CONFIG.propagation.sMeterDbPerUnit;
            int color = segment < 9 ? (lit ? HamGui.RX : 0xFF1C3322) : (lit ? HamGui.TX : 0xFF3A1C1C);
            graphics.fill(meterX + segment * 6, meterY, meterX + segment * 6 + 4, meterY + 5, color);
        }
        String reading = transmitting ? "TX" : sText(units);
        graphics.drawString(font, reading, x + WIDTH - 6 - font.width(reading), y + 15, transmitting ? HamGui.TX : HamGui.DISPLAY_TEXT, false);

        // line 3: battery, scan / dual watch
        int battery = Math.round(100f * TransceiverItem.getBattery(stack) / TransceiverItem.maxBattery());
        String status3 = SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery ? battery + "%" : "";
        graphics.drawString(font, status3, x + 6, y + 25, battery > 20 ? HamGui.DISPLAY_DIM : HamGui.WARNING, false);

        String mode = "";
        if (fresh && status.scanning()) {
            mode = status.holding() && !status.heardOn().isEmpty() ? "SCAN " + HamFrequency.editable(status.heardOn()) : "SCAN";
        } else if (tag.getBoolean(TransceiverItem.DUAL_WATCH)) {
            mode = "DW " + HamFrequency.editable(tag.getString(TransceiverItem.WATCH_FREQUENCY));
        }
        if (stack.getItem() instanceof com.codinglitch.simpleradio.ham.item.DirectionFinderItem) {
            int attenuation = com.codinglitch.simpleradio.ham.item.DirectionFinderItem.attenuation(stack);
            mode = "ATT " + (attenuation > 0 ? "-" + attenuation + "dB" : "0");
        }
        if (!on) mode = tag.contains(TransceiverItem.SWITCHED_ON) && !tag.getBoolean(TransceiverItem.SWITCHED_ON) ? "OFF" : "BAT!";
        graphics.drawString(font, mode, x + WIDTH - 6 - font.width(mode), y + 25, on ? HamGui.AMBER : HamGui.WARNING, false);
    }

    /**
     * The reading as text; like the bar segments it only shows a step once it is reached (S9+10 from 10 dB over S9).
     */
    static String sText(double units) {
        if (units < 1) return "S0";
        if (units < 9) return "S" + (int) Math.floor(units);
        double over = (units - 9) * SimpleRadioLibrary.SERVER_CONFIG.propagation.sMeterDbPerUnit;
        int tens = (int) Math.floor(over / 10.0 + 1e-9) * 10;
        return tens <= 0 ? "S9" : "S9+" + tens;
    }
}
