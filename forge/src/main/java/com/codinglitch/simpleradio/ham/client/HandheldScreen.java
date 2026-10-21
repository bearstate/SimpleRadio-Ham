package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.SimpleRadioLibrary;
import com.codinglitch.simpleradio.central.Frequency;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.radio.BandPlan;
import com.codinglitch.simpleradio.radio.Ctcss;
import com.codinglitch.simpleradio.radio.HamFrequency;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings of a handheld radio (sneak + use): frequency, shift, modulation, CTCSS, power switch,
 * dual watch, memory scan and the stored channels. PMR446 walkie talkies pick one of their 16 channels.
 */
public class HandheldScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 240;
    private static final int[] SHIFT_PRESETS = {0, -600, 600, -7600, 7600};
    private static final int LIST_TOP = 112;
    private static final int LIST_ROWS = 6;
    private static final int ROW_HEIGHT = 11;

    private final InteractionHand hand;

    private EditBox frequencyBox;
    private EditBox shiftBox;
    private EditBox watchBox;
    private EditBox nameBox;
    private Button modulationButton;
    private Frequency.Modulation modulation = Frequency.Modulation.FREQUENCY;
    private boolean switchedOn = true;
    private float toneTx = 0, toneRx = 0;
    private boolean dualWatch = false;
    private boolean scan = false;
    private int pmrChannel = 1;
    private final List<TransceiverItem.Memory> memories = new ArrayList<>();
    private int selected = -1;
    private int channel = -1;
    private int scroll = 0;
    private int left, top;
    private int invalidFlash = 0;

    public HandheldScreen(InteractionHand hand) {
        super(Component.translatable("screen.simpleradio.handheld"));
        this.hand = hand;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private boolean canTransmit() {
        return !(stack().getItem() instanceof TransceiverItem transceiver) || transceiver.canTransmit();
    }

    private boolean isPmr() {
        return stack().getItem() instanceof TransceiverItem transceiver && transceiver.equipment() == BandPlan.Equipment.PMR
                && SimpleRadioLibrary.SERVER_CONFIG.band_plan.pmrWalkies;
    }

    private Component onOff(boolean on) {
        return Component.translatable(on ? "screen.simpleradio.on" : "screen.simpleradio.off");
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        boolean firstInit = frequencyBox == null;

        String frequency = frequencyBox != null ? frequencyBox.getValue() : "145.500";
        String shift = shiftBox != null ? shiftBox.getValue() : "0.000";
        String watch = watchBox != null ? watchBox.getValue() : "";
        String name = nameBox != null ? nameBox.getValue() : "";

        if (firstInit) {
            ItemStack stack = stack();
            CompoundTag tag = stack.getOrCreateTag();
            if (tag.contains("frequency")) frequency = HamFrequency.editable(tag.getString("frequency"));
            Frequency.Modulation stored = Frequency.modulationOf(tag.getString("modulation"));
            if (stored != null) modulation = stored;
            shift = HamFrequency.editableShift(TransceiverItem.getShift(tag));
            switchedOn = TransceiverItem.isSwitchedOn(stack);
            toneTx = TransceiverItem.getToneTx(tag);
            toneRx = TransceiverItem.getToneRx(tag);
            dualWatch = tag.getBoolean(TransceiverItem.DUAL_WATCH);
            watch = tag.getString(TransceiverItem.WATCH_FREQUENCY).isEmpty() ? "" : HamFrequency.editable(tag.getString(TransceiverItem.WATCH_FREQUENCY));
            scan = tag.getBoolean(TransceiverItem.SCAN);
            memories.addAll(TransceiverItem.getMemories(tag));
            channel = TransceiverItem.getChannel(tag);
            selected = channel;
            pmrChannel = Math.max(1, BandPlan.pmrChannelOf(tag.getString("frequency")));
        }

        boolean pmr = isPmr();
        int x = left, y = top;

        // ---- Frequency ---- \\
        frequencyBox = new EditBox(font, x + 70, y + 22, 64, 14, Component.translatable("screen.simpleradio.frequency"));
        frequencyBox.setMaxLength(10);
        frequencyBox.setFilter(text -> text.matches("[0-9.,]*"));
        frequencyBox.setValue(frequency);
        if (pmr) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> pmrChannel = pmrChannel <= 1 ? BandPlan.PMR_CHANNELS : pmrChannel - 1).bounds(x + 70, y + 21, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> pmrChannel = pmrChannel >= BandPlan.PMR_CHANNELS ? 1 : pmrChannel + 1).bounds(x + 118, y + 21, 16, 16).build());
        } else {
            addRenderableWidget(frequencyBox);
        }

        modulationButton = addRenderableWidget(Button.builder(Component.literal(modulation.display()), button -> {
            modulation = modulation == Frequency.Modulation.FREQUENCY ? Frequency.Modulation.AMPLITUDE : Frequency.Modulation.FREQUENCY;
            button.setMessage(Component.literal(modulation.display()));
        }).bounds(x + 140, y + 21, 32, 16).build());
        modulationButton.active = !pmr;

        addRenderableWidget(Button.builder(onOff(switchedOn), button -> {
            switchedOn = !switchedOn;
            button.setMessage(onOff(switchedOn));
        }).bounds(x + 176, y + 21, 44, 16).build());

        // ---- Shift ---- \\
        shiftBox = new EditBox(font, x + 70, y + 42, 64, 14, Component.translatable("screen.simpleradio.shift"));
        shiftBox.setMaxLength(9);
        shiftBox.setFilter(text -> text.matches("[-+]?[0-9.,]*"));
        shiftBox.setValue(shift);
        if (!pmr && canTransmit()) {
            addRenderableWidget(shiftBox);
            int presetX = x + 140;
            for (int preset : SHIFT_PRESETS) {
                Component label = preset == 0 ? Component.translatable("screen.simpleradio.simplex_short") : Component.literal((preset > 0 ? "+" : "-") + Math.abs(preset) / 1000.0);
                addRenderableWidget(Button.builder(label, b -> shiftBox.setValue(HamFrequency.editableShift(preset))).bounds(presetX, y + 41, 30, 16).build());
                presetX += 31;
            }
        }

        // ---- CTCSS ---- \\
        if (canTransmit()) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> toneTx = Ctcss.step(toneTx, -1)).bounds(x + 70, y + 61, 14, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> toneTx = Ctcss.step(toneTx, 1)).bounds(x + 136, y + 61, 14, 16).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> toneRx = Ctcss.step(toneRx, -1)).bounds(x + 206, y + 61, 14, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> toneRx = Ctcss.step(toneRx, 1)).bounds(x + 272, y + 61, 14, 16).build());

        // ---- Dual watch / scan ---- \\
        addRenderableWidget(Button.builder(onOff(dualWatch), button -> {
            dualWatch = !dualWatch;
            button.setMessage(onOff(dualWatch));
        }).bounds(x + 70, y + 81, 40, 16).build());
        watchBox = new EditBox(font, x + 114, y + 82, 60, 14, Component.translatable("screen.simpleradio.dual_watch"));
        watchBox.setMaxLength(10);
        watchBox.setFilter(text -> text.matches("[0-9.,]*"));
        watchBox.setValue(watch);
        addRenderableWidget(watchBox);
        addRenderableWidget(Button.builder(onOff(scan), button -> {
            scan = !scan;
            button.setMessage(onOff(scan));
        }).bounds(x + 246, y + 81, 40, 16).build());

        // ---- Memories ---- \\
        nameBox = new EditBox(font, x + 206, y + LIST_TOP, 86, 14, Component.translatable("screen.simpleradio.memory.name"));
        nameBox.setMaxLength(TransceiverItem.MAX_MEMORY_NAME);
        nameBox.setValue(name);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.memory.store"), b -> store()).bounds(x + 206, y + LIST_TOP + 18, 86, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.memory.recall"), b -> recall()).bounds(x + 206, y + LIST_TOP + 36, 86, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.memory.delete"), b -> delete()).bounds(x + 206, y + LIST_TOP + 54, 86, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.apply"), b -> apply())
                .bounds(left + WIDTH / 2 - 60, top + HEIGHT - 24, 58, 18).build());
        if (stack().getItem() instanceof com.codinglitch.simpleradio.ham.aprs.AprsRadioItem) {
            addRenderableWidget(Button.builder(Component.literal("APRS"), b -> {
                if (minecraft != null) minecraft.setScreen(new AprsScreen(hand));
            }).bounds(left + WIDTH - 50, top + HEIGHT - 24, 42, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left + WIDTH / 2 + 2, top + HEIGHT - 24, 58, 18).build());

        if (firstInit && !pmr) setInitialFocus(frequencyBox);
    }

    // ---- Current values ---- \\

    private String currentFrequency() {
        return isPmr() ? BandPlan.pmrChannelFrequency(pmrChannel) : HamFrequency.normalize(frequencyBox.getValue());
    }

    private Integer currentShift() {
        return isPmr() ? Integer.valueOf(0) : HamFrequency.parseShiftKHz(shiftBox.getValue());
    }

    private Frequency.Modulation currentModulation() {
        return isPmr() ? Frequency.Modulation.FREQUENCY : modulation;
    }

    // ---- Memories ---- \\

    private void store() {
        String frequency = currentFrequency();
        Integer shift = currentShift();
        if (frequency == null || shift == null) {
            invalidFlash = 40;
            return;
        }

        TransceiverItem.Memory memory = new TransceiverItem.Memory(nameBox.getValue().trim(), frequency, currentModulation(), shift, toneTx, toneRx);
        if (selected >= 0 && selected < memories.size()) {
            memories.set(selected, memory);
        } else if (memories.size() < TransceiverItem.MAX_MEMORIES) {
            memories.add(memory);
            selected = memories.size() - 1;
        } else {
            invalidFlash = 40;
            return;
        }
        channel = selected;
    }

    private void recall() {
        if (selected < 0 || selected >= memories.size()) return;
        TransceiverItem.Memory memory = memories.get(selected);
        frequencyBox.setValue(HamFrequency.editable(memory.frequency()));
        shiftBox.setValue(HamFrequency.editableShift(memory.shift()));
        modulation = memory.modulation();
        modulationButton.setMessage(Component.literal(modulation.display()));
        toneTx = memory.toneTx();
        toneRx = memory.toneRx();
        nameBox.setValue(memory.name());
        int pmr = BandPlan.pmrChannelOf(memory.frequency());
        if (pmr > 0) pmrChannel = pmr;
        channel = selected;
    }

    private void delete() {
        if (selected < 0 || selected >= memories.size()) return;
        memories.remove(selected);
        if (channel == selected) channel = -1;
        else if (channel > selected) channel--;
        selected = Math.min(selected, memories.size() - 1);
    }

    private void apply() {
        Integer shift = currentShift();
        String frequency = currentFrequency();
        if (shift == null || frequency == null || (shift != 0 && HamFrequency.shift(frequency, shift) == null)) {
            invalidFlash = 40;
            return;
        }

        // the channel number only stays if the settings still match that stored channel
        int keptChannel = -1;
        if (channel >= 0 && channel < memories.size()) {
            TransceiverItem.Memory memory = memories.get(channel);
            if (memory.frequency().equals(frequency) && memory.shift() == shift && memory.modulation() == currentModulation()) keptChannel = channel;
        }

        String watch = HamFrequency.normalize(watchBox.getValue());
        HamNetwork.toServer(new HamNetwork.HandheldSettings(hand, frequency, currentModulation().shorthand, shift, switchedOn, toneTx, toneRx,
                List.copyOf(memories), keptChannel, dualWatch && watch != null, watch == null ? "" : watch, scan));
        onClose();
    }

    @Override
    public void tick() {
        super.tick();
        frequencyBox.tick();
        shiftBox.tick();
        watchBox.tick();
        nameBox.tick();
        if (invalidFlash > 0) invalidFlash--;

        if (!(stack().getItem() instanceof TransceiverItem)) onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listLeft = left + 8, listTop = top + LIST_TOP;
        if (button == 0 && mouseX >= listLeft && mouseX < listLeft + 192 && mouseY >= listTop && mouseY < listTop + LIST_ROWS * ROW_HEIGHT) {
            int row = scroll + (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (row < memories.size()) {
                selected = row;
                nameBox.setValue(memories.get(row).name());
            } else {
                selected = -1; // an empty row: the next "store" adds a new channel
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, memories.size() + 1 - LIST_ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = getFocused();
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && focused != nameBox) {
            apply();
            return true;
        }
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && focused instanceof EditBox box && box.isFocused()) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        HamGui.panel(graphics, left, top, WIDTH, HEIGHT);
        int x = left, y = top;
        boolean pmr = isPmr();

        HamGui.text(graphics, font, stack().getHoverName(), x + 8, y + 7, HamGui.LABEL);
        if (SimpleRadioLibrary.SERVER_CONFIG.handheld.requireBattery) {
            float ratio = (float) TransceiverItem.getBattery(stack()) / TransceiverItem.maxBattery();
            HamGui.bar(graphics, x + WIDTH - 70, y + 7, 40, 7, ratio, ratio > 0.2f ? HamGui.RX : HamGui.WARNING);
            HamGui.text(graphics, font, Math.round(ratio * 100) + "%", x + WIDTH - 27, y + 6, HamGui.DISPLAY_DIM);
        }

        HamGui.text(graphics, font, Component.translatable(pmr ? "screen.simpleradio.pmr_channel" : "screen.simpleradio.rx_mhz"), x + 8, y + 25, HamGui.LABEL);
        if (pmr) {
            HamGui.text(graphics, font, String.valueOf(pmrChannel), x + 102 - font.width(String.valueOf(pmrChannel)) / 2, y + 25, HamGui.AMBER);
            HamGui.text(graphics, font, HamFrequency.editable(BandPlan.pmrChannelFrequency(pmrChannel)), x + 222, y + 25, HamGui.DISPLAY_DIM);
        } else {
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.shift"), x + 8, y + 45, HamGui.LABEL);
        }

        if (canTransmit()) {
            HamGui.text(graphics, font, Component.translatable(pmr ? "screen.simpleradio.privacy_code" : "screen.simpleradio.ctcss_tx"), x + 8, y + 65, HamGui.LABEL);
            HamGui.text(graphics, font, Ctcss.display(toneTx), x + 110 - font.width(Ctcss.display(toneTx)) / 2, y + 65, toneTx > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM);
        }
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.ctcss_rx"), x + 156, y + 65, HamGui.LABEL);
        HamGui.text(graphics, font, Ctcss.display(toneRx), x + 246 - font.width(Ctcss.display(toneRx)) / 2, y + 65, toneRx > 0 ? HamGui.AMBER : HamGui.DISPLAY_DIM);

        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.dual_watch"), x + 8, y + 85, HamGui.LABEL);
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.scan"), x + 180, y + 85, HamGui.LABEL);

        renderMemories(graphics, mouseX, mouseY);

        // transmit preview and whether it is allowed
        HamGui.display(graphics, x + 8, y + 186, WIDTH - 16, 26);
        Integer shift = currentShift();
        String frequency = currentFrequency();
        String transmit = frequency == null || shift == null ? null : (shift == 0 ? frequency : HamFrequency.shift(frequency, shift));
        if (!canTransmit()) {
            HamGui.text(graphics, font, "RX " + (frequency == null ? "---" : HamFrequency.display(frequency)) + " " + currentModulation().display(), x + 14, y + 190, HamGui.DISPLAY_TEXT);
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.receive_only"), x + 14, y + 200, HamGui.DISPLAY_DIM);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        String preview = "TX " + (transmit == null ? "---" : HamFrequency.display(transmit)) + " " + currentModulation().display()
                + (toneTx > 0 ? "  " + Ctcss.display(toneTx) : "");
        HamGui.text(graphics, font, preview, x + 14, y + 190, transmit == null ? HamGui.WARNING : HamGui.DISPLAY_TEXT);

        String denial = transmit == null || minecraft == null || minecraft.player == null ? null
                : BandPlan.denial(minecraft.player, transmit, stack().getItem() instanceof TransceiverItem transceiver ? transceiver.equipment() : BandPlan.Equipment.AMATEUR);
        if (denial != null) {
            HamGui.text(graphics, font, font.plainSubstrByWidth(Component.translatable(denial).getString(), WIDTH - 28), x + 14, y + 200, HamGui.WARNING);
        } else if (transmit != null) {
            long kHz = HamFrequency.parseKHz(transmit);
            HamGui.text(graphics, font, Component.translatable("screen.simpleradio.band_ok", BandPlan.describe(kHz)), x + 14, y + 200, HamGui.DISPLAY_DIM);
        }

        if (invalidFlash > 0) {
            Component message = Component.translatable("message.simpleradio.invalid_frequency");
            HamGui.text(graphics, font, message, x + 8, y + HEIGHT - 19, HamGui.WARNING);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMemories(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8, y = top + LIST_TOP;
        HamGui.display(graphics, x - 1, y - 1, 194, LIST_ROWS * ROW_HEIGHT + 2);

        for (int i = 0; i < LIST_ROWS; i++) {
            int index = scroll + i;
            int rowY = y + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + 192 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (index == selected) graphics.fill(x, rowY, x + 192, rowY + ROW_HEIGHT, 0xFF24452E);
            else if (hovered) graphics.fill(x, rowY, x + 192, rowY + ROW_HEIGHT, 0xFF1A2A1F);

            if (index < memories.size()) {
                TransceiverItem.Memory memory = memories.get(index);
                String text = String.format(Locale.ROOT, "%02d %-8s %s %s", index + 1, font.plainSubstrByWidth(memory.name(), 48),
                        HamFrequency.editable(memory.frequency()), memory.shift() == 0 ? "" : HamFrequency.editableShift(memory.shift()));
                graphics.drawString(font, text, x + 3, rowY + 2, index == channel ? HamGui.AMBER : HamGui.DISPLAY_TEXT, false);
                if (memory.toneTx() > 0) {
                    String tone = String.format(Locale.ROOT, "%.1f", memory.toneTx());
                    graphics.drawString(font, tone, x + 189 - font.width(tone), rowY + 2, HamGui.DISPLAY_DIM, false);
                }
            } else if (index == memories.size() && index < TransceiverItem.MAX_MEMORIES) {
                graphics.drawString(font, Component.translatable("screen.simpleradio.memory.empty"), x + 3, rowY + 2, HamGui.DISPLAY_DIM, false);
            }
        }
        HamGui.text(graphics, font, Component.translatable("screen.simpleradio.memory.title", memories.size(), TransceiverItem.MAX_MEMORIES), x, y - 10, HamGui.LABEL);
    }
}
