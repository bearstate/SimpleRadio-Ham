package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.ham.block.ScannerBlockEntity;
import com.codinglitch.simpleradio.ham.menu.ScannerMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class ScannerScreen extends AbstractContainerScreen<ScannerMenu> {
    private EditBox startBox;
    private EditBox endBox;
    private Button stepButton;
    private Button modulationButton;
    private Button scanButton;
    private Button skipButton;
    private Button downButton;
    private Button upButton;

    private int stepKHz = 25;
    private Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    private int savedFlash = 0;

    public ScannerScreen(ScannerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 176;
    }

    private ScannerBlockEntity scanner() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(menu.pos) instanceof ScannerBlockEntity scanner ? scanner : null;
    }

    private Component stepLabel() {
        return Component.literal(stepKHz + " kHz");
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;

        String start = "144.000", end = "146.000";
        ScannerBlockEntity scanner = scanner();
        if (scanner != null) {
            start = HamFrequency.editable(scanner.startFrequency);
            end = HamFrequency.editable(scanner.endFrequency);
            stepKHz = scanner.stepKHz;
            modulation = scanner.modulation;
        }

        startBox = new EditBox(font, x + 66, y + 20, 64, 16, Component.translatable("screen.simpleradio.scanner.start"));
        startBox.setMaxLength(10);
        startBox.setFilter(text -> text.matches("[0-9.,]*"));
        startBox.setValue(start);
        addRenderableWidget(startBox);

        endBox = new EditBox(font, x + 182, y + 20, 64, 16, Component.translatable("screen.simpleradio.scanner.end"));
        endBox.setMaxLength(10);
        endBox.setFilter(text -> text.matches("[0-9.,]*"));
        endBox.setValue(end);
        addRenderableWidget(endBox);

        stepButton = addRenderableWidget(Button.builder(stepLabel(), button -> {
            int index = 0;
            for (int i = 0; i < ScannerBlockEntity.STEPS.length; i++) if (ScannerBlockEntity.STEPS[i] == stepKHz) index = i;
            stepKHz = ScannerBlockEntity.STEPS[(index + 1) % ScannerBlockEntity.STEPS.length];
            button.setMessage(stepLabel());
        }).bounds(x + 66, y + 42, 64, 16).build());

        modulationButton = addRenderableWidget(Button.builder(Component.literal(modulation.display()), button -> {
            modulation = switch (modulation) {
                case FREQUENCY -> Frequency.Modulation.AMPLITUDE;
                case AMPLITUDE -> Frequency.Modulation.SINGLE_SIDEBAND;
                case SINGLE_SIDEBAND -> Frequency.Modulation.FREQUENCY;
            };
            button.setMessage(Component.literal(modulation.display()));
        }).bounds(x + 134, y + 42, 36, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.apply"), b -> apply())
                .bounds(x + 182, y + 42, 64, 16).build());

        scanButton = addRenderableWidget(Button.builder(Component.empty(), b -> control(ScannerBlockEntity.ACTION_TOGGLE))
                .bounds(x + 8, y + 150, 80, 18).build());
        skipButton = addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.scanner.skip"), b -> control(ScannerBlockEntity.ACTION_SKIP))
                .bounds(x + 92, y + 150, 64, 18).build());
        downButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> control(ScannerBlockEntity.ACTION_DOWN))
                .bounds(x + 206, y + 150, 20, 18).build());
        upButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> control(ScannerBlockEntity.ACTION_UP))
                .bounds(x + 228, y + 150, 20, 18).build());
        updateButtons();
    }

    private void control(int action) {
        HamNetwork.toServer(new HamNetwork.ScannerControl(menu.pos, action));
    }

    private void apply() {
        String start = HamFrequency.normalize(startBox.getValue());
        String end = HamFrequency.normalize(endBox.getValue());
        if (start == null || end == null) {
            savedFlash = -40;
            return;
        }
        HamNetwork.toServer(new HamNetwork.ScannerSettings(menu.pos, start, end, stepKHz, modulation.shorthand));
        savedFlash = 40;
    }

    private void updateButtons() {
        boolean scanning = menu.hasFlag(ScannerMenu.FLAG_SCANNING);
        scanButton.setMessage(Component.translatable(scanning ? "screen.simpleradio.scanner.stop" : "screen.simpleradio.scanner.scan"));
        skipButton.active = scanning && menu.hasFlag(ScannerMenu.FLAG_HOLDING);
        downButton.active = !scanning;
        upButton.active = !scanning;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        startBox.tick();
        endBox.tick();
        if (savedFlash > 0) savedFlash--;
        if (savedFlash < 0) savedFlash++;
        updateButtons();
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
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        HamGui.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        HamGui.display(graphics, leftPos + 8, topPos + 66, imageWidth - 16, 78);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        HamGui.text(graphics, font, title, 8, 7, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.scanner.start"), 10, 24, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.scanner.end"), 138, 24, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.scanner.step"), 10, 46, HamGui.LABEL);

        if (savedFlash != 0) {
            Component flash = Component.translatable(savedFlash > 0 ? "screen.simpleradio.saved" : "message.simpleradio.invalid_frequency");
            HamGui.text(graphics, font, flash, imageWidth - 8 - font.width(flash), 7, savedFlash > 0 ? HamGui.RX : HamGui.WARNING);
        }

        boolean powered = menu.hasFlag(ScannerMenu.FLAG_POWERED);
        boolean scanning = menu.hasFlag(ScannerMenu.FLAG_SCANNING);
        boolean holding = menu.hasFlag(ScannerMenu.FLAG_HOLDING);
        boolean audio = menu.hasFlag(ScannerMenu.FLAG_AUDIO);

        // big channel read-out
        long kHz = menu.cursorKHz();
        String channel = powered ? String.format(Locale.ROOT, "%d.%03d", kHz / 1000, kHz % 1000) : "---.---";
        graphics.pose().pushPose();
        graphics.pose().translate(16, 74, 0);
        graphics.pose().scale(2.5f, 2.5f, 1f);
        graphics.drawString(font, channel, 0, 0, powered ? HamGui.DISPLAY_TEXT : HamGui.DISPLAY_DIM, false);
        graphics.pose().popPose();
        HamGui.text(graphics, font, "MHz " + modulation.display(), 16 + (int) (font.width(channel) * 2.5f) + 6, 86, HamGui.DISPLAY_DIM);

        Component status;
        int color = HamGui.DISPLAY_TEXT;
        if (menu.hasFlag(ScannerMenu.FLAG_BURNED)) {
            status = Component.translatable("screen.simpleradio.status.burned");
            color = HamGui.WARNING;
        } else if (!powered) {
            status = Component.translatable("screen.simpleradio.status.no_power");
            color = HamGui.WARNING;
        } else if (holding) {
            status = Component.translatable("screen.simpleradio.scanner.signal");
            color = HamGui.AMBER;
        } else if (scanning) {
            status = Component.translatable("screen.simpleradio.scanner.scanning");
        } else {
            status = Component.translatable("screen.simpleradio.scanner.stopped");
        }
        HamGui.lamp(graphics, 16, 105, audio, HamGui.RX);
        HamGui.text(graphics, font, status, 26, 104, color);
        int signal = menu.get(ScannerMenu.DATA_SIGNAL);
        SMeter.draw(graphics, font, imageWidth - 92, 104, signal == Short.MIN_VALUE ? 0 : com.codinglitch.simpleradio.radio.propagation.RadioLink.sUnits(signal / 10.0));

        int energy = menu.get(ScannerMenu.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(ScannerMenu.DATA_CAPACITY));
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.energy"), 16, 120, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, 64, 120, 120, 8, (float) energy / capacity, powered ? HamGui.RX : HamGui.WARNING);
        HamGui.text(graphics, font, energy + " J", 190, 120, HamGui.DISPLAY_DIM);

        HamGui.text(graphics, font, font.plainSubstrByWidth(Component.translatable("screen.simpleradio.scanner.hint").getString(), imageWidth - 32), 16, 132, HamGui.DISPLAY_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
