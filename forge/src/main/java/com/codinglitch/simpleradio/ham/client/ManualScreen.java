package com.codinglitch.simpleradio.ham.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The operator's manual: chapters on the left, the text of the chosen chapter on the right.
 * The text comes from the language files ("manual.simpleradio.[chapter].text"): a line starting with
 * "# " is a heading, "* " a bullet point.
 */
public class ManualScreen extends Screen {
    public static final String[] CHAPTERS = {
            "start", "licence", "handheld", "station", "antennas", "power", "repeater",
            "propagation", "weather", "fox", "aprs", "sstv", "mobile", "commands"
    };
    private static final int WIDTH = 340;
    private static final int HEIGHT = 222;
    private static final int LIST_WIDTH = 96;
    private static final int LINE = 10;

    private static int lastChapter = 0;

    private int chapter;
    private final List<Line> lines = new ArrayList<>();
    private final List<Button> chapterButtons = new ArrayList<>();
    private double scroll = 0;
    private int left, top;

    private record Line(FormattedCharSequence text, int color, int indent) {}

    public ManualScreen() {
        super(Component.translatable("screen.simpleradio.manual"));
        this.chapter = lastChapter;
    }

    public ManualScreen(int chapter) {
        this();
        this.chapter = chapter;
    }

    private int textX() {
        return left + LIST_WIDTH + 14;
    }

    // the text leaves room for the scroll bar at the right edge of the display
    private int textWidth() {
        return WIDTH - LIST_WIDTH - 32;
    }

    private int textTop() {
        return top + 24;
    }

    private int textHeight() {
        return HEIGHT - 32;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        chapterButtons.clear();
        int buttonHeight = 13;
        for (int i = 0; i < CHAPTERS.length; i++) {
            int index = i;
            Button button = Button.builder(Component.translatable("manual.simpleradio." + CHAPTERS[i] + ".title"), b -> select(index))
                    .bounds(left + 6, top + 22 + i * (buttonHeight + 1), LIST_WIDTH, buttonHeight).build();
            chapterButtons.add(addRenderableWidget(button));
        }
        select(chapter);
    }

    private void select(int index) {
        chapter = Mth.clamp(index, 0, CHAPTERS.length - 1);
        lastChapter = chapter;
        scroll = 0;
        for (int i = 0; i < chapterButtons.size(); i++) chapterButtons.get(i).active = i != chapter;

        lines.clear();
        String text = Component.translatable("manual.simpleradio." + CHAPTERS[chapter] + ".text").getString();
        for (String paragraph : text.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0));
                continue;
            }
            int color = 0xFFD8DEE6;
            int indent = 0;
            String body = paragraph;
            if (paragraph.startsWith("# ")) {
                body = paragraph.substring(2);
                color = HamGui.AMBER;
                if (!lines.isEmpty()) lines.add(new Line(FormattedCharSequence.EMPTY, 0, 0));
            } else if (paragraph.startsWith("* ")) {
                body = paragraph.substring(2);
                String bullet = "• ";
                indent = font.width(bullet);
                List<FormattedCharSequence> wrapped = font.split(Component.literal(body), textWidth() - indent);
                for (int i = 0; i < wrapped.size(); i++) {
                    lines.add(new Line(i == 0 ? FormattedCharSequence.composite(FormattedCharSequence.forward(bullet, Style.EMPTY.withColor(HamGui.DISPLAY_TEXT)), wrapped.get(i)) : wrapped.get(i),
                            color, i == 0 ? 0 : indent));
                }
                continue;
            }
            for (FormattedCharSequence line : font.split(Component.literal(body), textWidth())) lines.add(new Line(line, color, indent));
        }
    }

    private double maxScroll() {
        return Math.max(0, lines.size() * LINE - textHeight() + 4);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - delta * LINE * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Mth.clamp(scroll + (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? LINE : textHeight() - LINE), 0, maxScroll());
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Mth.clamp(scroll - (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? LINE : textHeight() - LINE), 0, maxScroll());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        HamGui.panel(graphics, left, top, WIDTH, HEIGHT);
        HamGui.text(graphics, font, title, left + 8, top + 8, HamGui.LABEL);
        Component chapterTitle = Component.translatable("manual.simpleradio." + CHAPTERS[chapter] + ".title");
        HamGui.text(graphics, font, chapterTitle, left + WIDTH - 10 - font.width(chapterTitle), top + 8, HamGui.AMBER);

        int x = textX() - 4, y = textTop() - 4, displayWidth = textWidth() + 16;
        HamGui.display(graphics, x, y, displayWidth, textHeight() + 4);
        graphics.enableScissor(x + 1, y + 1, x + displayWidth - 1, y + textHeight() + 3);
        int lineY = textTop() - (int) scroll;
        for (Line line : lines) {
            if (lineY > textTop() - LINE && lineY < textTop() + textHeight()) {
                graphics.drawString(font, line.text(), textX() + line.indent(), lineY, line.color(), false);
            }
            lineY += LINE;
        }
        graphics.disableScissor();

        // scroll bar
        double max = maxScroll();
        if (max > 0) {
            int barX = x + displayWidth - 4, barHeight = Math.max(12, (int) (textHeight() * textHeight() / (double) (lines.size() * LINE)));
            int barY = y + 2 + (int) ((textHeight() - barHeight) * (scroll / max));
            graphics.fill(barX, barY, barX + 2, barY + barHeight, HamGui.DISPLAY_DIM);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
