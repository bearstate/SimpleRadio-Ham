package com.codinglitch.simpleradio.ham.client;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.core.registry.items.TransceiverItem;
import com.codinglitch.simpleradio.ham.HamContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = CommonSimpleRadio.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HamClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(HamContent.STATION_MENU.get(), StationScreen::new);
            MenuScreens.register(HamContent.REPEATER_MENU.get(), RepeaterScreen::new);
            MenuScreens.register(HamContent.SCANNER_MENU.get(), ScannerScreen::new);
            MenuScreens.register(HamContent.SSTV_MENU.get(), SstvScreen::new);
        });

        TransceiverItem.settingsOpener = hand -> Minecraft.getInstance().setScreen(new HandheldScreen(hand));
        com.codinglitch.simpleradio.ham.item.RadioManualItem.opener = hand -> Minecraft.getInstance().setScreen(new ManualScreen());

        MinecraftForge.EVENT_BUS.addListener(HamKeys::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(HamKeys::onMouseButton);
        MinecraftForge.EVENT_BUS.addListener(HamKeys::onKey);
        MinecraftForge.EVENT_BUS.addListener(HamClient::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(BlockSMeter::onClientTick);
        if (GuiShots.ENABLED) MinecraftForge.EVENT_BUS.addListener(GuiShots::onClientTick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut loggingOut) -> HamClientPackets.restoreOwnServerConfig());
    }

    /**
     * Radios keep their settings when broken: show them on the item.
     */
    private static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        net.minecraft.nbt.CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag == null || !(stack.getItem() instanceof net.minecraft.world.item.BlockItem)) return;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !CommonSimpleRadio.ID.equals(id.getNamespace())) return;

        var tooltip = event.getToolTip();
        var gray = net.minecraft.ChatFormatting.GRAY;
        if (tag.contains("frequency")) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.simpleradio.stored.frequency",
                    com.codinglitch.simpleradio.radio.HamFrequency.display(tag.getString("frequency")), tag.getString("modulation")).withStyle(gray));
        }
        if (tag.contains("start") && tag.contains("end")) {
            tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.simpleradio.stored.range",
                    com.codinglitch.simpleradio.radio.HamFrequency.display(tag.getString("start")), com.codinglitch.simpleradio.radio.HamFrequency.display(tag.getString("end"))).withStyle(gray));
        }
        if (tag.contains("watts")) tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.simpleradio.stored.watts", tag.getInt("watts")).withStyle(gray));
        if (!tag.getString("callsign").isEmpty()) tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.simpleradio.stored.callsign", tag.getString("callsign")).withStyle(gray));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(HamContent.YAGI_ANTENNA_BE.get(), YagiRenderer::new);
        event.registerBlockEntityRenderer(HamContent.CHARGER_BE.get(), ChargerRenderer::new);
        event.registerBlockEntityRenderer(HamContent.STATION_BE.get(), CoaxRenderer::new);
        event.registerBlockEntityRenderer(HamContent.REPEATER_BE.get(), CoaxRenderer::new);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(YagiRenderer.BOOM_MODEL);
    }

    @SubscribeEvent
    public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(), "handheld", HandheldHud::render);
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id(), "block_s_meter", BlockSMeter::render);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(HamKeys.STATION_PTT);
        event.register(HamKeys.CHANNEL_UP);
        event.register(HamKeys.CHANNEL_DOWN);
    }
}
