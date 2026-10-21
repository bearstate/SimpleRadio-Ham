package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.ham.block.RepeaterBlockEntity;
import com.codinglitch.simpleradio.ham.menu.RepeaterMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.radio.Ctcss;
import com.codinglitch.simpleradio.radio.HamFrequency;
import com.codinglitch.simpleradio.radio.propagation.RadioLink;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class RepeaterScreen extends AbstractContainerScreen<RepeaterMenu> {
    private static final int[] SHIFT_PRESETS = {-600, 600, -7600, 7600};

    private EditBox frequencyBox;
    private EditBox shiftBox;
    private EditBox callsignBox;
    private EditBox linkBox;
    private Button modulationButton;
    private Button enabledButton;
    private Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    private boolean enabled = true;
    private float accessTone = 0, outputTone = 0;
    private int savedFlash = 0;

    public RepeaterScreen(RepeaterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 236;
    }

    private RepeaterBlockEntity repeater() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getBlockEntity(menu.pos) instanceof RepeaterBlockEntity repeater ? repeater : null;
    }

    private Component enabledLabel() {
        return Component.translatable(enabled ? "screen.simpleradio.on" : "screen.simpleradio.off");
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        boolean firstInit = frequencyBox == null;

        String frequency = frequencyBox != null ? frequencyBox.getValue() : "438.650";
        String shift = shiftBox != null ? shiftBox.getValue() : HamFrequency.editableShift(-7600);
        String callsign = callsignBox != null ? callsignBox.getValue() : "";
        String link = linkBox != null ? linkBox.getValue() : "";
        RepeaterBlockEntity repeater = repeater();
        if (firstInit && repeater != null) {
            frequency = HamFrequency.editable(repeater.frequency);
            shift = HamFrequency.editableShift(repeater.shift);
            modulation = repeater.modulation;
            enabled = repeater.enabled;
            accessTone = repeater.accessTone;
            outputTone = repeater.outputTone;
            callsign = repeater.callsign;
            link = repeater.linkId > 0 ? String.valueOf(repeater.linkId) : "";
        }

        frequencyBox = new EditBox(font, x + 90, y + 20, 72, 14, Component.translatable("screen.simpleradio.frequency"));
        frequencyBox.setMaxLength(10);
        frequencyBox.setFilter(text -> text.matches("[0-9.,]*"));
        frequencyBox.setValue(frequency);
        addRenderableWidget(frequencyBox);

        modulationButton = addRenderableWidget(Button.builder(Component.literal(modulation.display()), button -> {
            modulation = modulation == Frequency.Modulation.FREQUENCY ? Frequency.Modulation.AMPLITUDE : Frequency.Modulation.FREQUENCY;
            button.setMessage(Component.literal(modulation.display()));
        }).bounds(x + 168, y + 19, 36, 16).build());

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            enabled = !enabled;
            button.setMessage(enabledLabel());
        }).bounds(x + 208, y + 19, 40, 16).build());

        shiftBox = new EditBox(font, x + 90, y + 40, 52, 14, Component.translatable("screen.simpleradio.shift"));
        shiftBox.setMaxLength(9);
        shiftBox.setFilter(text -> text.matches("[-+]?[0-9.,]*"));
        shiftBox.setValue(shift);
        addRenderableWidget(shiftBox);

        int presetX = x + 146;
        for (int preset : SHIFT_PRESETS) {
            addRenderableWidget(Button.builder(Component.literal((preset > 0 ? "+" : "-") + Math.abs(preset) / 1000.0), button -> shiftBox.setValue(HamFrequency.editableShift(preset)))
                    .bounds(presetX, y + 39, 25, 16).build());
            presetX += 26;
        }

        // access / output tone
        addRenderableWidget(Button.builder(Component.literal("<"), b -> accessTone = Ctcss.step(accessTone, -1)).bounds(x + 90, y + 58, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> accessTone = Ctcss.step(accessTone, 1)).bounds(x + 148, y + 58, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> outputTone = Ctcss.step(outputTone, -1)).bounds(x + 90, y + 77, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> outputTone = Ctcss.step(outputTone, 1)).bounds(x + 148, y + 77, 14, 16).build());

        // call sign (morse ID) and link node
        callsignBox = new EditBox(font, x + 90, y + 97, 72, 14, Component.translatable("screen.simpleradio.repeater.callsign"));
        callsignBox.setMaxLength(RepeaterBlockEntity.MAX_CALLSIGN);
        callsignBox.setFilter(text -> text.matches("[A-Za-z0-9/]*"));
        callsignBox.setValue(callsign);
        addRenderableWidget(callsignBox);

        linkBox = new EditBox(font, x + 208, y + 97, 40, 14, Component.translatable("screen.simpleradio.repeater.link"));
        linkBox.setMaxLength(4);
        linkBox.setFilter(text -> text.matches("[0-9]*"));
        linkBox.setValue(link);
        addRenderableWidget(linkBox);

        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.apply"), b -> apply())
                .bounds(x + 188, y + 115, 60, 16).build());
    }

    private void apply() {
        Integer shift = HamFrequency.parseShiftKHz(shiftBox.getValue());
        String frequency = HamFrequency.normalize(frequencyBox.getValue());
        if (shift == null || shift == 0 || frequency == null || HamFrequency.shift(frequency, shift) == null) {
            savedFlash = -40;
            return;
        }
        int link = linkBox.getValue().isEmpty() ? 0 : Integer.parseInt(linkBox.getValue());
        HamNetwork.toServer(new HamNetwork.RepeaterSettings(menu.pos, frequency, modulation.shorthand, shift, enabled,
                accessTone, outputTone, callsignBox.getValue(), link));
        savedFlash = 40;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        frequencyBox.tick();
        shiftBox.tick();
        callsignBox.tick();
        linkBox.tick();
        if (savedFlash > 0) savedFlash--;
        if (savedFlash < 0) savedFlash++;
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
        HamGui.display(graphics, leftPos + 8, topPos + 136, imageWidth - 16, imageHeight - 144);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        HamGui.text(graphics, font, title, 8, 7, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.output_mhz"), 10, 23, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.input_shift"), 10, 43, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.access_tone"), 10, 62, HamGui.LABEL);
        graphics.drawString(font, Ctcss.display(accessTone), 126 - font.width(Ctcss.display(accessTone)) / 2, 62, accessTone > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM, false);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.output_tone"), 10, 81, HamGui.LABEL);
        graphics.drawString(font, Ctcss.display(outputTone), 126 - font.width(Ctcss.display(outputTone)) / 2, 81, outputTone > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM, false);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.callsign"), 10, 100, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.link"), 168, 100, HamGui.LABEL);

        if (savedFlash != 0) {
            Component flash = Component.translatable(savedFlash > 0 ? "screen.simpleradio.saved" : "message.simpleradio.invalid_frequency");
            HamGui.text(graphics, font, flash, 10, 119, savedFlash > 0 ? HamGui.RX : HamGui.WARNING);
        }

        int left = 14;
        int line = 142;
        boolean powered = menu.hasFlag(RepeaterMenu.FLAG_POWERED);
        boolean relaying = menu.hasFlag(RepeaterMenu.FLAG_RELAYING);
        boolean active = menu.hasFlag(RepeaterMenu.FLAG_ENABLED);

        HamGui.lamp(graphics, left, line + 1, relaying, HamGui.TX);
        HamGui.text(graphics, font, "TX", left + 9, line, relaying ? HamGui.TX : HamGui.DISPLAY_DIM);

        Component status;
        int color = HamGui.DISPLAY_TEXT;
        if (!active) {
            status = Component.translatable("screen.simpleradio.status.disabled");
            color = HamGui.DISPLAY_DIM;
        } else if (menu.hasFlag(RepeaterMenu.FLAG_BURNED)) {
            status = Component.translatable("screen.simpleradio.status.burned");
            color = HamGui.WARNING;
        } else if (!powered) {
            status = Component.translatable("screen.simpleradio.status.no_power");
            color = HamGui.WARNING;
        } else if (menu.hasFlag(RepeaterMenu.FLAG_TIMED_OUT)) {
            status = Component.translatable("screen.simpleradio.status.timed_out");
            color = HamGui.WARNING;
        } else if (relaying) {
            status = Component.translatable("screen.simpleradio.status.relaying");
            color = HamGui.TX;
        } else {
            status = Component.translatable("screen.simpleradio.status.listening");
        }
        HamGui.text(graphics, font, status, left + 26, line, color);

        int signal = menu.get(RepeaterMenu.DATA_SIGNAL);
        SMeter.draw(graphics, font, imageWidth - 92, line, signal == Short.MIN_VALUE ? 0 : RadioLink.sUnits(signal / 10.0));

        line += 13;
        RepeaterBlockEntity repeater = repeater();
        if (repeater != null) {
            String input = repeater.getInputFrequency();
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.output", HamFrequency.display(repeater.frequency), repeater.modulation.display()), left, line, HamGui.DISPLAY_TEXT);
            line += 11;
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater.input", input == null ? "---" : HamFrequency.display(input), HamFrequency.displayShift(repeater.shift)), left, line, HamGui.DISPLAY_TEXT);
            line += 11;
            Component id = repeater.callsign.isEmpty() ? Component.translatable("screen.simpleradio.repeater.no_id") : Component.translatable("screen.simpleradio.repeater.id", repeater.callsign);
            HamGui.text(graphics, font, id, left, line, repeater.callsign.isEmpty() ? HamGui.DISPLAY_DIM : HamGui.AMBER);
            if (repeater.linkId > 0) {
                Component linked = Component.translatable("screen.simpleradio.repeater.linked", repeater.linkId, menu.get(RepeaterMenu.DATA_LINKED));
                HamGui.text(graphics, font, linked, imageWidth - 14 - font.width(linked), line, HamGui.AMBER);
            }
        } else {
            line += 22;
        }

        line += 13;
        int energy = menu.get(RepeaterMenu.DATA_ENERGY);
        int capacity = Math.max(1, menu.get(RepeaterMenu.DATA_CAPACITY));
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.energy"), left, line, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, left + 48, line, 120, 8, (float) energy / capacity, powered ? HamGui.RX : HamGui.WARNING);
        HamGui.text(graphics, font, energy + " J", left + 174, line, HamGui.DISPLAY_DIM);

        line += 12;
        int range = menu.get(RepeaterMenu.DATA_RANGE) * 10;
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.range", String.format(Locale.ROOT, "%,d", range)), left, line, HamGui.DISPLAY_TEXT);

        line += 12;
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.repeater_hint"), left, line, HamGui.DISPLAY_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
