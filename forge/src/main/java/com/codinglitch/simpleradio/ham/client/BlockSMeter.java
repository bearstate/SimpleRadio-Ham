package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.ham.radio.BlockSignal;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * S-meter next to the crosshair while looking at an original radio or receiver block. The server sends the
 * reading a few times a second for as long as the player looks at the block.
 */
public final class BlockSMeter {
    private static final int WIDTH = 110;
    private static final int HEIGHT = 30;
    private static final int REQUEST_INTERVAL = 5;
    private static final int STALE_TICKS = 20;

    @Nullable private static BlockPos target;
    @Nullable private static HamNetwork.SignalReading reading;
    private static long readingTime = 0;
    private static long lastRequest = Long.MIN_VALUE;

    private BlockSMeter() {}

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos looked = lookedAt(minecraft);
        boolean changed = looked != null && !looked.equals(target);
        target = looked;
        if (target == null || minecraft.level == null) return;

        long now = minecraft.level.getGameTime();
        if (changed || now - lastRequest >= REQUEST_INTERVAL) {
            lastRequest = now;
            HamNetwork.toServer(new HamNetwork.SignalRequest(target));
        }
    }

    @Nullable
    private static BlockPos lookedAt(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || !SimpleRadioLibrary.CLIENT_CONFIG.hud.blockSMeter) return null;
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        double reach = BlockSignal.MAX_DISTANCE;
        if (minecraft.player.distanceToSqr(pos.getCenter()) > reach * reach) return null;
        return BlockSignal.hasMeter(minecraft.level.getBlockEntity(pos)) ? pos : null;
    }

    public static void update(HamNetwork.SignalReading update) {
        Minecraft minecraft = Minecraft.getInstance();
        reading = update;
        readingTime = minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.level == null) return;
        if (target == null || reading == null || !reading.pos().equals(target)) return;
        if (minecraft.level.getGameTime() - readingTime > STALE_TICKS) return;

        Font font = minecraft.font;
        // beside the crosshair: below it, or above it when a handheld's display is shown below
        int x = Math.min(width / 2 + 10, width - 2 - WIDTH);
        int y = minecraft.player != null && HandheldHud.isShown(minecraft.player) ? height / 2 - 6 - HEIGHT : height / 2 + 6;
        HamGui.panel(graphics, x, y, WIDTH, HEIGHT);
        HamGui.display(graphics, x + 3, y + 3, WIDTH - 6, HEIGHT - 6);

        String frequency = reading.frequency().isEmpty() ? "---"
                : HamFrequency.display(reading.frequency()).replace(" MHz", "") + " " + reading.modulation();
        HamGui.text(graphics, font, frequency, x + 7, y + 6, reading.status() == BlockSignal.STATUS_ON ? HamGui.DISPLAY_TEXT : HamGui.DISPLAY_DIM);

        if (reading.status() == BlockSignal.STATUS_ON) {
            double units = reading.signal() == Short.MIN_VALUE ? 0 : RadioLink.sUnits(reading.signal() / 10.0);
            SMeter.draw(graphics, font, x + 7, y + 17, units);
        } else {
            String key = switch (reading.status()) {
                case BlockSignal.STATUS_NO_POWER -> "hud.simpleradio.smeter.no_power";
                case BlockSignal.STATUS_NO_CATALYST -> "hud.simpleradio.smeter.no_catalyst";
                default -> "hud.simpleradio.smeter.off";
            };
            HamGui.text(graphics, font, font.plainSubstrByWidth(Component.translatable(key).getString(), WIDTH - 14), x + 7, y + 17, HamGui.WARNING);
        }
    }
}
