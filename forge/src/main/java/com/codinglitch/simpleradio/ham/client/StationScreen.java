package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.ham.block.StationBlockEntity;
import com.codinglitch.simpleradio.ham.licence.Licences;
import com.codinglitch.simpleradio.ham.menu.StationMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.ham.radio.Propagation;
import com.codinglitch.simpleradio.radio.BandPlan;
import com.codinglitch.simpleradio.radio.Ctcss;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.propagation.Ionosphere;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class StationScreen extends AbstractContainerScreen<StationMenu> {
    private EditBox frequencyBox;
    private EditBox shiftBox;
    private Button modulationButton;
    private Button voxButton;
    private Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    private int watts = 25;
    private float toneTx = 0, toneRx = 0;
    private int clarifier = 0;
    private boolean vox = false;
    private int savedFlash = 0;
    private Button rotateLeft;
    private Button rotateRight;

    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 276;
        this.imageHeight = 240; // fits a 1280x720 window at GUI scale 3
    }

    private StationBlockEntity station() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(menu.pos) instanceof StationBlockEntity station ? station : null;
    }

    private static Frequency.Modulation nextModulation(Frequency.Modulation modulation) {
        return switch (modulation) {
            case FREQUENCY -> Frequency.Modulation.AMPLITUDE;
            case AMPLITUDE -> Frequency.Modulation.SINGLE_SIDEBAND;
            case SINGLE_SIDEBAND -> Frequency.Modulation.FREQUENCY;
        };
    }

    private Component voxLabel() {
        return Component.literal("VOX").withStyle(vox ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        boolean firstInit = frequencyBox == null;

        String frequency = frequencyBox != null ? frequencyBox.getValue() : "145.500";
        String shift = shiftBox != null ? shiftBox.getValue() : "0.000";
        StationBlockEntity station = station();
        if (firstInit && station != null) {
            frequency = HamFrequency.editable(station.frequency);
            shift = HamFrequency.editableShift(station.shift);
            modulation = station.modulation;
            watts = station.watts;
            toneTx = station.toneTx;
            toneRx = station.toneRx;
            clarifier = station.clarifierHz;
            vox = station.vox;
        }

        // Band buttons
        int bandX = x + 58;
        for (HamFrequency.Band band : HamFrequency.Band.values()) {
            addRenderableWidget(Button.builder(Component.literal(band.label), button -> {
                String value = HamFrequency.format(band.defaultKHz());
                if (value != null) frequencyBox.setValue(HamFrequency.editable(value));
                shiftBox.setValue(HamFrequency.editableShift(0));
                modulation = band == HamFrequency.Band.HF ? Frequency.Modulation.SINGLE_SIDEBAND : Frequency.Modulation.FREQUENCY;
                modulationButton.setMessage(Component.literal(modulation.display()));
            }).bounds(bandX, y + 19, 36, 16).build());
            bandX += 38;
        }

        modulationButton = addRenderableWidget(Button.builder(Component.literal(modulation.display()), button -> {
            modulation = nextModulation(modulation);
            button.setMessage(Component.literal(modulation.display()));
        }).bounds(x + 176, y + 19, 36, 16).build());
        voxButton = addRenderableWidget(Button.builder(voxLabel(), button -> {
            vox = !vox;
            button.setMessage(voxLabel());
        }).bounds(x + 216, y + 19, 50, 16).build());

        frequencyBox = new EditBox(font, x + 58, y + 40, 72, 14, Component.translatable("screen.simpleradio.frequency"));
        frequencyBox.setMaxLength(10);
        frequencyBox.setFilter(text -> text.matches("[0-9.,]*"));
        frequencyBox.setValue(frequency);
        addRenderableWidget(frequencyBox);

        shiftBox = new EditBox(font, x + 176, y + 40, 72, 14, Component.translatable("screen.simpleradio.shift"));
        shiftBox.setMaxLength(9);
        shiftBox.setFilter(text -> text.matches("[-+]?[0-9.,]*"));
        shiftBox.setValue(shift);
        addRenderableWidget(shiftBox);

        // Power
        addRenderableWidget(Button.builder(Component.literal("-10"), b -> changeWatts(-10)).bounds(x + 58, y + 58, 24, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> changeWatts(-1)).bounds(x + 84, y + 58, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> changeWatts(1)).bounds(x + 152, y + 58, 20, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+10"), b -> changeWatts(10)).bounds(x + 174, y + 58, 24, 16).build());

        // CTCSS
        addRenderableWidget(Button.builder(Component.literal("<"), b -> toneTx = Ctcss.step(toneTx, -1)).bounds(x + 58, y + 77, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> toneTx = Ctcss.step(toneTx, 1)).bounds(x + 120, y + 77, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> toneRx = Ctcss.step(toneRx, -1)).bounds(x + 190, y + 77, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> toneRx = Ctcss.step(toneRx, 1)).bounds(x + 252, y + 77, 14, 16).build());

        // Clarifier (RIT): shift-click for 100 Hz steps
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeClarifier(-1)).bounds(x + 58, y + 96, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeClarifier(1)).bounds(x + 120, y + 96, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal("0"), b -> clarifier = 0).bounds(x + 138, y + 96, 16, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.apply"), b -> apply())
                .bounds(x + 206, y + 96, 60, 16).build());

        // Yagi rotator
        rotateLeft = addRenderableWidget(Button.builder(Component.literal("<"), b -> HamNetwork.toServer(new HamNetwork.RotateYagi(menu.pos, -1)))
                .bounds(x + imageWidth - 50, y + 165, 18, 11).build());
        rotateRight = addRenderableWidget(Button.builder(Component.literal(">"), b -> HamNetwork.toServer(new HamNetwork.RotateYagi(menu.pos, 1)))
                .bounds(x + imageWidth - 30, y + 165, 18, 11).build());
    }

    private void changeWatts(int delta) {
        int min = SimpleRadioLibrary.SERVER_CONFIG.station.minWatts;
        int max = SimpleRadioLibrary.SERVER_CONFIG.station.maxWatts;
        watts = Math.max(min, Math.min(max, watts + delta));
    }

    private void changeClarifier(int direction) {
        int step = Screen.hasShiftDown() ? 100 : 10;
        clarifier = Math.max(-StationBlockEntity.MAX_CLARIFIER, Math.min(StationBlockEntity.MAX_CLARIFIER, clarifier + direction * step));
    }

    private void apply() {
        Integer shift = HamFrequency.parseShiftKHz(shiftBox.getValue());
        String frequency = HamFrequency.normalize(frequencyBox.getValue());
        if (shift == null || frequency == null) {
            savedFlash = -40;
            return;
        }
        HamNetwork.toServer(new HamNetwork.StationSettings(menu.pos, frequency, modulation.shorthand, shift, watts, toneTx, toneRx, clarifier, vox));
        savedFlash = 40;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        frequencyBox.tick();
        shiftBox.tick();
        if (savedFlash > 0) savedFlash--;
        if (savedFlash < 0) savedFlash++;

        boolean hasYagi = menu.get(StationMenu.DATA_YAGI) >= 0;
        rotateLeft.visible = hasYagi;
        rotateRight.visible = hasYagi;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            apply();
            return true;
        }
        GuiEventListener focused = getFocused();
        if (focused instanceof EditBox box && box.isFocused()) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true; // never let typed letters close the screen
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        HamGui.panel(graphics, x, y, imageWidth, imageHeight);
        HamGui.display(graphics, x + 8, y + 116, imageWidth - 16, imageHeight - 122);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        HamGui.text(graphics, font, title, 8, 7, HamGui.LABEL);
        if (!Licences.clientCallsign.isEmpty()) {
            Component operator = Component.translatable("screen.simpleradio.operator", Licences.clientCallsign);
            HamGui.text(graphics, font, operator, imageWidth - 8 - font.width(operator), 7, HamGui.AMBER);
        }

        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.band"), 10, 23, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.rx_mhz"), 10, 43, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.shift"), 136, 43, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.power"), 10, 62, HamGui.LABEL);
        String wattText = watts + " W";
        graphics.drawString(font, wattText, 128 - font.width(wattText) / 2, 62, HamGui.AMBER, false);

        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.ctcss_tx"), 10, 81, HamGui.LABEL);
        graphics.drawString(font, Ctcss.display(toneTx), 96 - font.width(Ctcss.display(toneTx)) / 2, 81, toneTx > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM, false);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.ctcss_rx"), 140, 81, HamGui.LABEL);
        graphics.drawString(font, Ctcss.display(toneRx), 228 - font.width(Ctcss.display(toneRx)) / 2, 81, toneRx > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM, false);

        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.clarifier"), 10, 100, HamGui.LABEL);
        String rit = (clarifier > 0 ? "+" : "") + clarifier + " Hz";
        graphics.drawString(font, rit, 97 - font.width(rit) / 2, 100, clarifier != 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM, false);

        if (savedFlash != 0) {
            Component flash = Component.translatable(savedFlash > 0 ? "screen.simpleradio.saved" : "message.simpleradio.invalid_frequency");
            HamGui.text(graphics, font, flash, 160, 100, savedFlash > 0 ? HamGui.RX : HamGui.WARNING);
        }

        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        int left = 14;
        int line = 121;
        boolean powered = menu.hasFlag(StationMenu.FLAG_POWERED);
        boolean transmitting = menu.hasFlag(StationMenu.FLAG_TRANSMITTING);
        boolean receiving = menu.hasFlag(StationMenu.FLAG_RECEIVING);

        // ---- TX / RX / status / S-meter ---- \\
        HamGui.lamp(graphics, left, line + 1, transmitting, HamGui.TX);
        HamGui.text(graphics, font, "TX", left + 9, line, transmitting ? HamGui.TX : HamGui.DISPLAY_DIM);
        HamGui.lamp(graphics, left + 26, line + 1, powered && !transmitting && receiving, HamGui.RX);
        HamGui.text(graphics, font, "RX", left + 35, line, powered && !transmitting ? HamGui.DISPLAY_TEXT : HamGui.DISPLAY_DIM);

        Component status;
        int statusColor = HamGui.DISPLAY_TEXT;
        if (menu.hasFlag(StationMenu.FLAG_BURNED)) {
            status = Component.translatable("screen.simpleradio.status.burned");
            statusColor = HamGui.WARNING;
        } else if (menu.hasFlag(StationMenu.FLAG_WRONG_VOLTAGE)) {
            status = Component.translatable("screen.simpleradio.status.wrong_voltage");
            statusColor = HamGui.WARNING;
        } else if (!powered) {
            status = Component.translatable("screen.simpleradio.status.no_power");
            statusColor = HamGui.WARNING;
        } else if (menu.hasFlag(StationMenu.FLAG_OVERHEATED)) {
            status = Component.translatable("screen.simpleradio.status.overheated");
            statusColor = HamGui.WARNING;
        } else if (menu.hasFlag(StationMenu.FLAG_DENIED)) {
            status = Component.translatable("screen.simpleradio.status.denied");
            statusColor = HamGui.WARNING;
        } else if (menu.hasFlag(StationMenu.FLAG_CW)) {
            status = Component.translatable("screen.simpleradio.status.cw");
            statusColor = HamGui.TX;
        } else if (transmitting) {
            status = Component.translatable("screen.simpleradio.status.transmitting");
            statusColor = HamGui.TX;
        } else {
            status = Component.translatable("screen.simpleradio.status.listening");
        }
        HamGui.text(graphics, font, status, left + 54, line, statusColor);

        int signal = menu.get(StationMenu.DATA_SIGNAL);
        double units = signal == Short.MIN_VALUE || transmitting ? 0 : RadioLink.sUnits(signal / 10.0);
        SMeter.draw(graphics, font, imageWidth - 92, line, units);

        // ---- Frequencies ---- \\
        line += 12;
        StationBlockEntity station = station();
        if (station != null) {
            String transmit = station.getTransmitFrequency();
            long kHz = Math.max(0, HamFrequency.parseKHz(station.frequency));
            String band = BandPlan.describe(kHz);
            String rx = "RX " + HamFrequency.display(station.frequency) + " " + station.modulation.display() + "  [" + band + "]"
                    + (station.toneRx > 0 ? "  TSQL " + String.format(Locale.ROOT, "%.1f", station.toneRx) : "");
            HamGui.text(graphics, font, rx, left, line, HamGui.DISPLAY_TEXT);
            line += 10;
            String tx = "TX " + (transmit == null ? "---" : HamFrequency.display(transmit)) + " (" + HamFrequency.displayShift(station.shift) + ") "
                    + station.watts + " W" + (station.toneTx > 0 ? "  T " + String.format(Locale.ROOT, "%.1f", station.toneTx) : "");
            HamGui.text(graphics, font, tx, left, line, HamGui.DISPLAY_TEXT);
        } else {
            line += 10;
        }

        // ---- Energy ---- \\
        line += 12;
        int energy = menu.get(StationMenu.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(StationMenu.DATA_CAPACITY));
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.energy"), left, line, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, left + 48, line, 110, 8, (float) energy / capacity, powered ? HamGui.RX : HamGui.WARNING);
        HamGui.text(graphics, font, energy + " J", left + 164, line, HamGui.DISPLAY_DIM);

        // ---- Antenna, SWR ---- \\
        line += 12;
        int yagi = menu.get(StationMenu.DATA_YAGI);
        int mast = menu.get(StationMenu.DATA_MAST);
        int antennaType = menu.get(StationMenu.DATA_ANTENNA);
        String coax = String.format(Locale.ROOT, "%.1f", menu.get(StationMenu.DATA_COAX) / 10.0);
        String loss = String.format(Locale.ROOT, "%.1f", menu.get(StationMenu.DATA_COAX_LOSS) / 100.0);
        Component antenna;
        if (antennaType == StationMenu.ANTENNA_VERTICAL) {
            antenna = Component.translatable("screen.simpleradio.antenna.vertical", coax, loss);
        } else if (yagi >= 0) {
            double azimuth = yagi * 22.5;
            Component direction = Component.translatable("compass.simpleradio." + Propagation.compassPoint(azimuth).toLowerCase(Locale.ROOT));
            antenna = antennaType == StationMenu.ANTENNA_YAGI_COAX
                    ? Component.translatable("screen.simpleradio.antenna.yagi_coax", String.format(Locale.ROOT, "%05.1f", azimuth), direction, coax, loss)
                    : Component.translatable("screen.simpleradio.antenna.yagi", String.format(Locale.ROOT, "%05.1f", azimuth), direction, mast);
        } else if (mast > 0) {
            antenna = Component.translatable("screen.simpleradio.antenna.mast", mast);
        } else {
            antenna = Component.translatable("screen.simpleradio.antenna.none");
        }
        HamGui.text(graphics, font, font.plainSubstrByWidth(antenna.getString(), imageWidth - 80), left, line, antennaType != StationMenu.ANTENNA_MAST ? HamGui.AMBER : HamGui.DISPLAY_TEXT);

        line += 11;
        double swr = menu.get(StationMenu.DATA_SWR) / 10.0;
        int swrColor = swr < 1.6 ? HamGui.RX : swr < 3 ? HamGui.AMBER : HamGui.WARNING;
        String swrText = swr >= 19.9 ? "SWR >20" : String.format(Locale.ROOT, "SWR %.1f", swr);
        HamGui.text(graphics, font, swrText, left, line, swrColor);
        if (menu.hasFlag(StationMenu.FLAG_TUNER)) HamGui.text(graphics, font, Component.translatable("screen.simpleradio.tuner"), left + 54, line, HamGui.RX);

        // PA temperature and man-made noise
        int temperature = menu.get(StationMenu.DATA_TEMPERATURE);
        HamGui.text(graphics, font, "PA", left + 104, line, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, left + 118, line, 50, 8, temperature / 100f, temperature < 60 ? HamGui.RX : temperature < 90 ? HamGui.AMBER : HamGui.WARNING);
        int noise = menu.get(StationMenu.DATA_NOISE);
        if (noise > 0) HamGui.text(graphics, font, Component.translatable("screen.simpleradio.qrm", noise), left + 176, line, HamGui.AMBER);

        // ---- Range ---- \\
        line += 12;
        int range = menu.get(StationMenu.DATA_RANGE) * 10;
        HamGui.text(graphics, font, Component.translatable(yagi >= 0 ? "screen.simpleradio.range.forward" : "screen.simpleradio.range", String.format(Locale.ROOT, "%,d", range)), left, line, HamGui.DISPLAY_TEXT);

        // ---- HF conditions ---- \\
        line += 12;
        int flux = menu.get(StationMenu.DATA_SOLAR_FLUX), kIndex = menu.get(StationMenu.DATA_K_INDEX), bands = menu.get(StationMenu.DATA_BANDS);
        String solar = "SFI " + flux + " K" + kIndex;
        HamGui.text(graphics, font, solar, left, line, kIndex >= 5 ? HamGui.WARNING : HamGui.DISPLAY_DIM);
        int bandX = left + font.width(solar) + 8;
        for (int i = 0; i < Ionosphere.DISPLAY_BAND_NAMES.length; i++) {
            int state = (bands >> (i * 2)) & 3;
            int color = switch (state) {
                case 3 -> HamGui.RX;
                case 2 -> 0xFFD8E05A;
                case 1 -> HamGui.AMBER;
                default -> 0xFF6B3A3A;
            };
            graphics.fill(bandX, line + 1, bandX + 5, line + 6, color);
            HamGui.text(graphics, font, Ionosphere.DISPLAY_BAND_NAMES[i], bandX + 7, line, HamGui.DISPLAY_DIM);
            bandX += 9 + font.width(Ionosphere.DISPLAY_BAND_NAMES[i]) + 4;
        }

        // ---- Hint ---- \\
        line += 11;
        Component hint = Component.translatable("screen.simpleradio.station_hint", HamKeys.STATION_PTT.getTranslatedKeyMessage());
        HamGui.text(graphics, font, font.plainSubstrByWidth(hint.getString(), imageWidth - 28), left, line, HamGui.DISPLAY_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
