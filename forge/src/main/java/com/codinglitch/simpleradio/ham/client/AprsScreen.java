package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.ham.aprs.AprsRadioItem;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.ham.radio.Propagation;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The APRS radio's map: stations whose position reports it decoded, around the player.
 */
public class AprsScreen extends Screen {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 222;
    private static final int MAP = 170;
    private static final int[] RADII = {250, 1000, 4000, 16000};

    private final InteractionHand hand;
    private List<HamNetwork.AprsEntry> entries = new ArrayList<>();
    private int radiusIndex = 1;
    private boolean beacon = true;
    private EditBox commentBox;
    private int left, top;
    private int refresh = 0;

    public AprsScreen(InteractionHand hand) {
        super(Component.translatable("screen.simpleradio.aprs"));
        this.hand = hand;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    public void update(List<HamNetwork.AprsEntry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    private Component beaconLabel() {
        return Component.translatable(beacon ? "screen.simpleradio.aprs.beacon_on" : "screen.simpleradio.aprs.beacon_off");
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        boolean firstInit = commentBox == null;
        String comment = commentBox != null ? commentBox.getValue() : AprsRadioItem.comment(stack());
        if (firstInit) beacon = AprsRadioItem.isBeaconing(stack());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> radiusIndex = Math.max(0, radiusIndex - 1)).bounds(left + 8, top + HEIGHT - 24, 18, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> radiusIndex = Math.min(RADII.length - 1, radiusIndex + 1)).bounds(left + 28, top + HEIGHT - 24, 18, 16).build());

        addRenderableWidget(Button.builder(beaconLabel(), button -> {
            beacon = !beacon;
            button.setMessage(beaconLabel());
            save();
        }).bounds(left + 186, top + HEIGHT - 44, 136, 16).build());

        commentBox = new EditBox(font, left + 186, top + HEIGHT - 23, 96, 14, Component.translatable("screen.simpleradio.aprs.comment"));
        commentBox.setMaxLength(AprsRadioItem.MAX_COMMENT);
        commentBox.setValue(comment);
        addRenderableWidget(commentBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.apply"), b -> save()).bounds(left + 284, top + HEIGHT - 24, 38, 16).build());

        HamNetwork.toServer(new HamNetwork.AprsRequest(hand));
    }

    private void save() {
        HamNetwork.toServer(new HamNetwork.AprsSettings(hand, beacon, commentBox.getValue()));
    }

    @Override
    public void tick() {
        super.tick();
        commentBox.tick();
        if (++refresh % 40 == 0) HamNetwork.toServer(new HamNetwork.AprsRequest(hand));
        if (!(stack().getItem() instanceof AprsRadioItem)) onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && focused instanceof EditBox box && box.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                save();
                return true;
            }
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static String distance(double blocks) {
        return blocks < 1000 ? Math.round(blocks) + " m" : String.format(Locale.ROOT, "%.1f km", blocks / 1000);
    }

    static String age(int seconds) {
        if (seconds < 60) return seconds + " s";
        return seconds / 60 + " dk";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        HamGui.panel(graphics, left, top, WIDTH, HEIGHT);
        HamGui.text(graphics, font, title, left + 8, top + 7, HamGui.LABEL);

        if (minecraft == null || minecraft.player == null) return;
        double px = minecraft.player.getX(), pz = minecraft.player.getZ();
        String dimension = minecraft.player.level().dimension().location().toString();
        int radius = RADII[radiusIndex];

        // ---- map ---- \\
        int mapX = left + 8, mapY = top + 20, half = MAP / 2;
        int cx = mapX + half, cy = mapY + half;
        HamGui.display(graphics, mapX, mapY, MAP, MAP);
        for (int ring = 1; ring <= 3; ring++) {
            int r = half * ring / 4;
            graphics.fill(cx - r, cy, cx + r, cy + 1, 0xFF1D3524);
        }
        graphics.fill(cx, mapY + 2, cx + 1, mapY + MAP - 2, 0xFF1D3524);
        graphics.fill(mapX + 2, cy, mapX + MAP - 2, cy + 1, 0xFF1D3524);
        HamGui.text(graphics, font, "N", cx - 2, mapY + 3, HamGui.AMBER);
        HamGui.text(graphics, font, distance(radius), mapX + 4, mapY + MAP - 11, HamGui.DISPLAY_DIM);

        // the player in the middle, pointing where they look
        double yaw = Math.toRadians(minecraft.player.getYRot());
        int tipX = cx + (int) Math.round(-Math.sin(yaw) * 5), tipZ = cy + (int) Math.round(Math.cos(yaw) * 5);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        graphics.fill(tipX, tipZ, tipX + 1, tipZ + 1, 0xFFFFFFFF);

        for (HamNetwork.AprsEntry entry : entries) {
            if (!entry.dimension().equals(dimension)) continue;
            double dx = (entry.x() - px) / radius * half, dz = (entry.z() - pz) / radius * half;
            double length = Math.sqrt(dx * dx + dz * dz);
            boolean outside = length > half - 3;
            if (outside) {
                dx = dx / length * (half - 3);
                dz = dz / length * (half - 3);
            }
            int x = cx + (int) Math.round(dx), y = cy + (int) Math.round(dz);
            int color = entry.ageSeconds() < 300 ? HamGui.RX : entry.ageSeconds() < 900 ? HamGui.AMBER : HamGui.DISPLAY_DIM;
            graphics.fill(x - 1, y - 1, x + 2, y + 2, outside ? HamGui.DISPLAY_DIM : color);
            if (!outside) graphics.drawString(font, entry.callsign(), x + 3, y - 3, color, false);
        }

        // ---- list ---- \\
        int listX = left + 186, listY = top + 20;
        HamGui.display(graphics, listX, listY, 136, 132);
        int line = listY + 4;
        if (entries.isEmpty()) {
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.aprs.empty"), listX + 4, line, HamGui.DISPLAY_DIM);
        }
        for (HamNetwork.AprsEntry entry : entries.subList(0, Math.min(10, entries.size()))) {
            boolean here = entry.dimension().equals(dimension);
            double distance = Math.sqrt((entry.x() - px) * (entry.x() - px) + (entry.z() - pz) * (entry.z() - pz));
            String where = here ? distance(distance) + " " + Propagation.compassPoint(Propagation.azimuth(px, pz, entry.x(), entry.z())) : "---";
            graphics.drawString(font, entry.callsign(), listX + 4, line, HamGui.DISPLAY_TEXT, false);
            graphics.drawString(font, where, listX + 132 - font.width(where), line, HamGui.DISPLAY_DIM, false);
            line += 9;
            String detail = age(entry.ageSeconds()) + "  S" + Math.min(9, (int) RadioLink.sUnits(entry.signalDb()))
                    + (entry.speedKmh() > 1 ? "  " + Math.round(entry.speedKmh()) + " km/h" : "");
            graphics.drawString(font, detail, listX + 8, line, HamGui.DISPLAY_DIM, false);
            line += 4 + 9;
            if (line > listY + 124) break;
        }

        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.aprs.comment"), left + 186, top + HEIGHT - 58, HamGui.LABEL);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
