package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.ham.menu.SstvMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.MapColor;

/**
 * SSTV terminal: the last picture heard (drawn as it comes in), and sending the map in the top slot.
 */
public class SstvScreen extends AbstractContainerScreen<SstvMenu> {
    private static final ResourceLocation PREVIEW = CommonSimpleRadio.id("sstv_preview");
    private static final int PICTURE = 128;
    private static final int PREVIEW_X = 30;
    private static final int PREVIEW_Y = 18;
    private static final int PREVIEW_SIZE = 96;

    private DynamicTexture texture;
    private boolean hasPicture = false;
    private Button sendButton;
    private Button stopButton;

    public SstvScreen(SstvMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 230;
        this.imageHeight = 212;
        this.inventoryLabelX = SstvMenu.INVENTORY_X;
        this.inventoryLabelY = SstvMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        if (texture == null) {
            texture = new DynamicTexture(PICTURE, PICTURE, true);
            minecraft.getTextureManager().register(PREVIEW, texture);
        }
        byte[] pending = HamClientPackets.takePreview(menu.pos);
        if (pending != null) setPicture(pending);

        int x = leftPos + 134, y = topPos + 18;
        sendButton = addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.sstv.send"),
                b -> HamNetwork.toServer(new HamNetwork.SstvSend(menu.pos, false))).bounds(x, y, 88, 18).build());
        stopButton = addRenderableWidget(Button.builder(Component.translatable("screen.simpleradio.sstv.stop"),
                b -> HamNetwork.toServer(new HamNetwork.SstvSend(menu.pos, true))).bounds(x, y + 22, 88, 18).build());
        updateButtons();
    }

    public void setPicture(byte[] colors) {
        if (texture == null || texture.getPixels() == null) return;
        NativeImage image = texture.getPixels();
        boolean any = false;
        for (int y = 0; y < PICTURE; y++) {
            for (int x = 0; x < PICTURE; x++) {
                int packed = colors[y * PICTURE + x] & 255;
                int abgr = MapColor.getColorFromPackedId(packed);
                if (packed >= 4) any = true;
                image.setPixelRGBA(x, y, (abgr >>> 24) == 0 ? 0xFF121A14 : abgr);
            }
        }
        texture.upload();
        hasPicture = any;
    }

    private void updateButtons() {
        boolean station = menu.hasFlag(SstvMenu.FLAG_STATION);
        boolean sending = menu.hasFlag(SstvMenu.FLAG_SENDING);
        sendButton.active = station && !sending && menu.getSlot(0).hasItem();
        stopButton.active = sending;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        HamGui.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        HamGui.display(graphics, leftPos + PREVIEW_X - 2, topPos + PREVIEW_Y - 2, PREVIEW_SIZE + 4, PREVIEW_SIZE + 4);
        if (hasPicture) {
            graphics.blit(PREVIEW, leftPos + PREVIEW_X, topPos + PREVIEW_Y, PREVIEW_SIZE, PREVIEW_SIZE, 0, 0, PICTURE, PICTURE, PICTURE, PICTURE);
        }
        for (int slotY : new int[]{SstvMenu.SEND_Y, SstvMenu.BLANK_Y, SstvMenu.OUTPUT_Y}) {
            slot(graphics, leftPos + SstvMenu.SLOT_X - 1, topPos + slotY - 1);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, leftPos + SstvMenu.INVENTORY_X - 1 + column * 18, topPos + SstvMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(graphics, leftPos + SstvMenu.INVENTORY_X - 1 + column * 18, topPos + SstvMenu.INVENTORY_Y + 57);
        }
        HamGui.display(graphics, leftPos + 134, topPos + 64, 88, 58);
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, HamGui.PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF3A3F47);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        HamGui.text(graphics, font, title, 8, 6, HamGui.LABEL);
        HamGui.text(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, HamGui.LABEL);
        HamGui.text(graphics, font, "TX", SstvMenu.SLOT_X, SstvMenu.SEND_Y - 9, HamGui.DISPLAY_DIM);
        HamGui.text(graphics, font, "MAP", SstvMenu.SLOT_X - 1, SstvMenu.BLANK_Y - 9, HamGui.DISPLAY_DIM);
        HamGui.text(graphics, font, "RX", SstvMenu.SLOT_X, SstvMenu.OUTPUT_Y - 9, HamGui.DISPLAY_DIM);

        if (!hasPicture) {
            Component empty = Component.translatable("screen.simpleradio.sstv.no_picture");
            int width = font.width(empty);
            HamGui.text(graphics, font, empty, PREVIEW_X + (PREVIEW_SIZE - width) / 2, PREVIEW_Y + PREVIEW_SIZE / 2 - 4, HamGui.DISPLAY_DIM);
        }

        int x = 138, y = 68;
        boolean station = menu.hasFlag(SstvMenu.FLAG_STATION);
        boolean sending = menu.hasFlag(SstvMenu.FLAG_SENDING);
        boolean receiving = menu.hasFlag(SstvMenu.FLAG_RECEIVING);

        Component status;
        int color = HamGui.DISPLAY_TEXT;
        if (!station) {
            status = Component.translatable("screen.simpleradio.sstv.no_station");
            color = HamGui.WARNING;
        } else if (sending) {
            status = Component.translatable("screen.simpleradio.sstv.sending");
            color = HamGui.TX;
        } else if (receiving) {
            status = Component.translatable("screen.simpleradio.sstv.receiving");
            color = HamGui.RX;
        } else if (menu.hasFlag(SstvMenu.FLAG_NEEDS_MAP)) {
            status = Component.translatable("screen.simpleradio.sstv.needs_map");
            color = HamGui.AMBER;
        } else {
            status = Component.translatable("screen.simpleradio.sstv.idle");
        }
        HamGui.lamp(graphics, x, y + 1, sending || receiving, sending ? HamGui.TX : HamGui.RX);
        HamGui.text(graphics, font, font.plainSubstrByWidth(status.getString(), 70), x + 10, y, color);

        HamGui.text(graphics, font, "TX", x, y + 16, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, x + 16, y + 16, 64, 8, menu.get(SstvMenu.DATA_SEND_PROGRESS) / 1000f, HamGui.TX);
        HamGui.text(graphics, font, "RX", x, y + 30, HamGui.DISPLAY_DIM);
        HamGui.bar(graphics, x + 16, y + 30, 64, 8, menu.get(SstvMenu.DATA_RECEIVE_PROGRESS) / 1000f, HamGui.RX);
        HamGui.text(graphics, font, "Robot 36", x, y + 44, HamGui.DISPLAY_DIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void removed() {
        super.removed();
        if (texture != null) {
            minecraft.getTextureManager().release(PREVIEW);
            texture = null;
        }
    }
}
