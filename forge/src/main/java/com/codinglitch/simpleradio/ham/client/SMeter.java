package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A bar graph S-meter: S1-S9 in green, then +10/+20/+30 dB in red.
 */
public final class SMeter {
    public static final int WIDTH = 84;

    private SMeter() {}

    public static void draw(GuiGraphics graphics, Font font, int x, int y, double units) {
        double perUnit = Math.max(1, SimpleRadioLibrary.SERVER_CONFIG.propagation.sMeterDbPerUnit);
        for (int segment = 0; segment < 12; segment++) {
            boolean lit = segment < 9 ? units >= segment + 1 : units >= 9 + (segment - 8) * 10.0 / perUnit;
            int color = segment < 9 ? (lit ? HamGui.RX : 0xFF1C3322) : (lit ? HamGui.TX : 0xFF3A1C1C);
            graphics.fill(x + segment * 5, y + 1, x + segment * 5 + 4, y + 7, color);
        }
        graphics.drawString(font, HandheldHud.sText(units), x + 62, y, units >= 1 ? HamGui.DISPLAY_TEXT : HamGui.DISPLAY_DIM, false);
    }
}
