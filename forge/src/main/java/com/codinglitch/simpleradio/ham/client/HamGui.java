package com.codinglitch.simpleradio.ham.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared drawing helpers so the radio screens look like one family of front panels.
 */
public final class HamGui {
    public static final int PANEL = 0xFF23262B;
    public static final int PANEL_EDGE = 0xFF454B55;
    public static final int PANEL_SHADOW = 0xFF121417;
    public static final int DISPLAY = 0xFF0E1A12;
    public static final int DISPLAY_EDGE = 0xFF2E4A36;
    public static final int DISPLAY_TEXT = 0xFF8CF5A6;
    public static final int DISPLAY_DIM = 0xFF4E8A5E;
    public static final int LABEL = 0xFFB9C0CA;
    public static final int WARNING = 0xFFFF6A5E;
    public static final int TX = 0xFFFF4A3D;
    public static final int RX = 0xFF52E07A;
    public static final int AMBER = 0xFFFFB347;

    private HamGui() {}

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, PANEL_SHADOW);
        graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
    }

    public static void display(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, DISPLAY_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, DISPLAY);
    }

    public static void lamp(GuiGraphics graphics, int x, int y, boolean on, int color) {
        graphics.fill(x, y, x + 6, y + 6, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 5, y + 5, on ? color : 0xFF3A3A3A);
    }

    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, float ratio, int color) {
        graphics.fill(x, y, x + width, y + height, PANEL_SHADOW);
        int filled = Math.round((width - 2) * Math.max(0, Math.min(1, ratio)));
        graphics.fill(x + 1, y + 1, x + 1 + filled, y + height - 1, color);
    }

    public static void text(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    public static void text(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }
}
