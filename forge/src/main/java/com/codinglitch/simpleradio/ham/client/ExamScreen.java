package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.ham.network.HamNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Arrays;
import java.util.List;

/**
 * The amateur radio licence exam: one multiple choice question at a time.
 */
public class ExamScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int OPTION_HEIGHT = 26;

    private final int[] questions;
    private final int[][] orders;
    private final int[] answers; // chosen original option per question, -1 when unanswered
    private int current = 0;
    private int left, top;

    private Button previous, next, finish;

    // result
    private boolean finished = false;
    private boolean passed;
    private int score, total;
    private String callsign = "";

    public ExamScreen(int[] questions, int[][] orders) {
        super(Component.translatable("screen.simpleradio.exam"));
        this.questions = questions;
        this.orders = orders;
        this.answers = new int[questions.length];
        Arrays.fill(answers, -1);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;

        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> move(-1)).bounds(left + 8, top + HEIGHT - 26, 30, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> move(1)).bounds(left + 42, top + HEIGHT - 26, 30, 18).build());
        finish = addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.exam.finish"), b -> submit())
                .bounds(left + WIDTH - 98, top + HEIGHT - 26, 90, 18).build());
        updateButtons();
    }

    private void move(int delta) {
        current = Math.max(0, Math.min(questions.length - 1, current + delta));
        updateButtons();
    }

    private void updateButtons() {
        previous.active = !finished && current > 0;
        next.active = !finished && current < questions.length - 1;
        finish.active = !finished && Arrays.stream(answers).noneMatch(answer -> answer < 0);
        finish.setMessage(Component.translatable(finished ? "gui.done" : "screen.simpleradio.exam.finish"));
        if (finished) {
            finish.active = true;
            previous.visible = false;
            next.visible = false;
        }
    }

    private void submit() {
        if (finished) {
            onClose();
            return;
        }
        HamNetwork.toServer(new HamNetwork.ExamSubmit(answers.clone()));
        finish.active = false;
    }

    public void showResult(boolean passed, int score, int total, String callsign) {
        this.finished = true;
        this.passed = passed;
        this.score = score;
        this.total = total;
        this.callsign = callsign;
        updateButtons();
    }

    private int optionTop(int slot) {
        return top + 76 + slot * (OPTION_HEIGHT + 3);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!finished && button == 0) {
            for (int slot = 0; slot < 4; slot++) {
                int y = optionTop(slot);
                if (mouseX >= left + 8 && mouseX <= left + WIDTH - 8 && mouseY >= y && mouseY <= y + OPTION_HEIGHT) {
                    answers[current] = orders[current][slot];
                    if (current < questions.length - 1) current++;
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        HamGui.panel(graphics, left, top, WIDTH, HEIGHT);
        HamGui.text(graphics, font, title, left + 8, top + 7, HamGui.LABEL);

        if (finished) {
            renderResult(graphics);
        } else {
            renderQuestion(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuestion(GuiGraphics graphics, int mouseX, int mouseY) {
        int answered = (int) Arrays.stream(answers).filter(answer -> answer >= 0).count();
        Component progress = Component.translatable("screen.simpleradio.exam.progress", current + 1, questions.length, answered);
        // between the arrows and the finish button (the title takes the top row)
        HamGui.text(graphics, font, progress, left + WIDTH / 2 - 12 - font.width(progress) / 2, top + HEIGHT - 17, HamGui.DISPLAY_DIM);

        int question = questions[current];
        HamGui.display(graphics, left + 8, top + 22, WIDTH - 16, 48);
        List<FormattedCharSequence> lines = font.split(Component.translatable("exam.simpleradio.q" + question), WIDTH - 28);
        int y = top + 28;
        for (FormattedCharSequence line : lines.subList(0, Math.min(4, lines.size()))) {
            graphics.drawString(font, line, left + 14, y, HamGui.DISPLAY_TEXT, false);
            y += 10;
        }

        for (int slot = 0; slot < 4; slot++) {
            int original = orders[current][slot];
            int optionY = optionTop(slot);
            boolean chosen = answers[current] == original;
            boolean hovered = mouseX >= left + 8 && mouseX <= left + WIDTH - 8 && mouseY >= optionY && mouseY <= optionY + OPTION_HEIGHT;

            graphics.fill(left + 8, optionY, left + WIDTH - 8, optionY + OPTION_HEIGHT, chosen ? 0xFF2E5A3A : hovered ? 0xFF3A4048 : 0xFF2B2F35);
            List<FormattedCharSequence> option = font.split(Component.translatable("exam.simpleradio.q" + question + "." + original), WIDTH - 44);
            int lineY = optionY + (option.size() > 1 ? 4 : 9);
            graphics.drawString(font, (char) ('A' + slot) + ")", left + 12, lineY, chosen ? HamGui.RX : HamGui.AMBER, false);
            for (FormattedCharSequence line : option.subList(0, Math.min(2, option.size()))) {
                graphics.drawString(font, line, left + 28, lineY, HamGui.LABEL, false);
                lineY += 10;
            }
        }
    }

    private void renderResult(GuiGraphics graphics) {
        HamGui.display(graphics, left + 8, top + 30, WIDTH - 16, 110);
        Component headline = Component.translatable(passed ? "screen.simpleradio.exam.passed" : "screen.simpleradio.exam.failed");
        graphics.drawCenteredString(font, headline, left + WIDTH / 2, top + 44, passed ? HamGui.RX : HamGui.WARNING);
        graphics.drawCenteredString(font, Component.translatable("screen.simpleradio.exam.score", score, total), left + WIDTH / 2, top + 64, HamGui.DISPLAY_TEXT);
        if (passed) {
            graphics.drawCenteredString(font, Component.translatable("screen.simpleradio.exam.callsign"), left + WIDTH / 2, top + 88, HamGui.DISPLAY_DIM);
            graphics.pose().pushPose();
            graphics.pose().translate(left + WIDTH / 2f, top + 102, 0);
            graphics.pose().scale(2, 2, 1);
            graphics.drawCenteredString(font, callsign, 0, 0, HamGui.AMBER);
            graphics.pose().popPose();
        } else {
            List<FormattedCharSequence> lines = font.split(Component.translatable("screen.simpleradio.exam.retry"), WIDTH - 40);
            int y = top + 90;
            for (FormattedCharSequence line : lines) {
                graphics.drawCenteredString(font, line, left + WIDTH / 2, y, HamGui.DISPLAY_DIM);
                y += 10;
            }
        }
    }
}
