package com.codinglitch.simpleradio.ham;

import com.codinglitch.simpleradio.CommonSimpleRadio;
import com.codinglitch.simpleradio.core.registry.SimpleRadioMenus;
import com.codinglitch.simpleradio.ham.block.*;
import com.codinglitch.simpleradio.ham.compat.Interference;
import com.codinglitch.simpleradio.ham.gametest.HamTests;
import net.minecraftforge.event.RegisterGameTestsEvent;
import com.codinglitch.simpleradio.ham.item.BatteryItem;
import com.codinglitch.simpleradio.ham.item.BatterySwapRecipe;
import com.codinglitch.simpleradio.ham.item.CoaxCableItem;
import com.codinglitch.simpleradio.ham.menu.RepeaterMenu;
import com.codinglitch.simpleradio.ham.menu.ScannerMenu;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import com.codinglitch.simpleradio.ham.menu.StationMenu;
import com.codinglitch.simpleradio.ham.network.HamNetwork;
import com.codinglitch.simpleradio.ham.power.PowerCapabilities;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/**
 * Registrations for the amateur radio additions: station, repeater, yagi antenna,
 * CW key and the handheld charging station.
 */
public class HamContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CommonSimpleRadio.ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CommonSimpleRadio.ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CommonSimpleRadio.ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, CommonSimpleRadio.ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CommonSimpleRadio.ID);

    // ---- Blocks ---- \\
    public static final RegistryObject<StationBlock> STATION = BLOCKS.register("station", () -> new StationBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.METAL)
    ));
    public static final RegistryObject<RepeaterBlock> REPEATER = BLOCKS.register("repeater", () -> new RepeaterBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F, 6.0F).sound(SoundType.METAL)
    ));
    public static final RegistryObject<YagiAntennaBlock> YAGI_ANTENNA = BLOCKS.register("yagi_antenna", () -> new YagiAntennaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 4.0F).sound(SoundType.METAL).noOcclusion()
    ));
    public static final RegistryObject<CwKeyBlock> CW_KEY = BLOCKS.register("cw_key", () -> new CwKeyBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F, 2.0F).sound(SoundType.WOOD).noOcclusion()
    ));
    public static final RegistryObject<ChargerBlock> CHARGER = BLOCKS.register("charger", () -> new ChargerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion()
    ));
    public static final RegistryObject<ScannerBlock> SCANNER = BLOCKS.register("scanner", () -> new ScannerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.METAL)
    ));
    public static final RegistryObject<DualBandAntennaBlock> DUAL_BAND_ANTENNA = BLOCKS.register("dual_band_antenna", () -> new DualBandAntennaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0F, 4.0F).sound(SoundType.METAL).noOcclusion()
    ));
    public static final RegistryObject<PowerSupplyBlock> POWER_SUPPLY = BLOCKS.register("power_supply", () -> new PowerSupplyBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.METAL)
    ));
    public static final RegistryObject<AntennaTunerBlock> ANTENNA_TUNER = BLOCKS.register("antenna_tuner", () -> new AntennaTunerBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion()
    ));
    public static final RegistryObject<FoxTransmitterBlock> FOX_TRANSMITTER = BLOCKS.register("fox_transmitter", () -> new FoxTransmitterBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.0F, 4.0F).sound(SoundType.METAL).noOcclusion()
    ));
    public static final RegistryObject<DeskMicrophoneBlock> DESK_MICROPHONE = BLOCKS.register("desk_microphone", () -> new DeskMicrophoneBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F, 2.0F).sound(SoundType.METAL).noOcclusion()
    ));

    public static final RegistryObject<SstvTerminalBlock> SSTV_TERMINAL = BLOCKS.register("sstv_terminal", () -> new SstvTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.0F, 6.0F).sound(SoundType.METAL)
    ));

    // Power where there are no mains: a coal generator, and building blocks with the wire laid in them.
    public static final RegistryObject<com.codinglitch.simpleradio.ham.block.CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.register("coal_generator", () -> new com.codinglitch.simpleradio.ham.block.CoalGeneratorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(com.codinglitch.simpleradio.ham.block.CoalGeneratorBlock.LIT) ? 9 : 0)
    ));
    public static final RegistryObject<com.codinglitch.simpleradio.ham.block.WiredBlocks.Pillar> WIRED_STRIPPED_SPRUCE_LOG = BLOCKS.register("wired_stripped_spruce_log",
            () -> new com.codinglitch.simpleradio.ham.block.WiredBlocks.Pillar(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_LOG)));
    public static final RegistryObject<com.codinglitch.simpleradio.ham.block.WiredBlocks.Plain> WIRED_SPRUCE_PLANKS = BLOCKS.register("wired_spruce_planks",
            () -> new com.codinglitch.simpleradio.ham.block.WiredBlocks.Plain(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)));
    public static final RegistryObject<com.codinglitch.simpleradio.ham.block.WiredBlocks.Plain> WIRED_STONE_BRICKS = BLOCKS.register("wired_stone_bricks",
            () -> new com.codinglitch.simpleradio.ham.block.WiredBlocks.Plain(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.STONE_BRICKS)));

    // ---- Items ---- \\
    public static final RegistryObject<Item> STATION_ITEM = blockItem("station", STATION);
    public static final RegistryObject<Item> REPEATER_ITEM = blockItem("repeater", REPEATER);
    public static final RegistryObject<Item> YAGI_ANTENNA_ITEM = blockItem("yagi_antenna", YAGI_ANTENNA);
    public static final RegistryObject<Item> CW_KEY_ITEM = blockItem("cw_key", CW_KEY);
    public static final RegistryObject<Item> CHARGER_ITEM = blockItem("charger", CHARGER);
    public static final RegistryObject<Item> SCANNER_ITEM = blockItem("scanner", SCANNER);
    public static final RegistryObject<Item> DUAL_BAND_ANTENNA_ITEM = blockItem("dual_band_antenna", DUAL_BAND_ANTENNA);
    public static final RegistryObject<Item> POWER_SUPPLY_ITEM = blockItem("power_supply", POWER_SUPPLY);
    public static final RegistryObject<Item> ANTENNA_TUNER_ITEM = blockItem("antenna_tuner", ANTENNA_TUNER);
    public static final RegistryObject<Item> FOX_TRANSMITTER_ITEM = blockItem("fox_transmitter", FOX_TRANSMITTER);
    public static final RegistryObject<Item> SSTV_TERMINAL_ITEM = blockItem("sstv_terminal", SSTV_TERMINAL);
    public static final RegistryObject<Item> COAL_GENERATOR_ITEM = blockItem("coal_generator", COAL_GENERATOR);
    public static final RegistryObject<Item> WIRED_STRIPPED_SPRUCE_LOG_ITEM = blockItem("wired_stripped_spruce_log", WIRED_STRIPPED_SPRUCE_LOG);
    public static final RegistryObject<Item> WIRED_SPRUCE_PLANKS_ITEM = blockItem("wired_spruce_planks", WIRED_SPRUCE_PLANKS);
    public static final RegistryObject<Item> WIRED_STONE_BRICKS_ITEM = blockItem("wired_stone_bricks", WIRED_STONE_BRICKS);
    public static final RegistryObject<Item> APRS_RADIO = ITEMS.register("aprs_radio", () -> new com.codinglitch.simpleradio.ham.aprs.AprsRadioItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DIRECTION_FINDER = ITEMS.register("direction_finder", () -> new com.codinglitch.simpleradio.ham.item.DirectionFinderItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DESK_MICROPHONE_ITEM = blockItem("desk_microphone", DESK_MICROPHONE);
    public static final RegistryObject<Item> HAND_MICROPHONE = ITEMS.register("hand_microphone", () -> new com.codinglitch.simpleradio.ham.item.HandMicrophoneItem(new Item.Properties()));
    public static final RegistryObject<Item> BATTERY = ITEMS.register("battery", () -> new BatteryItem(new Item.Properties()));
    public static final RegistryObject<Item> COAX_CABLE = ITEMS.register("coax_cable", () -> new CoaxCableItem(new Item.Properties()));
    public static final RegistryObject<Item> RADIO_MANUAL = ITEMS.register("radio_manual", () -> new com.codinglitch.simpleradio.ham.item.RadioManualItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MOBILE_RADIO = ITEMS.register("mobile_radio", () -> new com.codinglitch.simpleradio.ham.item.MobileRadioItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LICENCE_EXAM = ITEMS.register("licence_exam", () -> new com.codinglitch.simpleradio.ham.item.LicenceExamItem(new Item.Properties()));

    // ---- Block Entities ---- \\
    public static final RegistryObject<BlockEntityType<StationBlockEntity>> STATION_BE = blockEntity("station", StationBlockEntity::new, STATION);
    public static final RegistryObject<BlockEntityType<RepeaterBlockEntity>> REPEATER_BE = blockEntity("repeater", RepeaterBlockEntity::new, REPEATER);
    public static final RegistryObject<BlockEntityType<YagiAntennaBlockEntity>> YAGI_ANTENNA_BE = blockEntity("yagi_antenna", YagiAntennaBlockEntity::new, YAGI_ANTENNA);
    public static final RegistryObject<BlockEntityType<ChargerBlockEntity>> CHARGER_BE = blockEntity("charger", ChargerBlockEntity::new, CHARGER);
    public static final RegistryObject<BlockEntityType<ScannerBlockEntity>> SCANNER_BE = blockEntity("scanner", ScannerBlockEntity::new, SCANNER);
    public static final RegistryObject<BlockEntityType<FoxTransmitterBlockEntity>> FOX_TRANSMITTER_BE = blockEntity("fox_transmitter", FoxTransmitterBlockEntity::new, FOX_TRANSMITTER);
    public static final RegistryObject<BlockEntityType<SstvTerminalBlockEntity>> SSTV_TERMINAL_BE = blockEntity("sstv_terminal", SstvTerminalBlockEntity::new, SSTV_TERMINAL);
    public static final RegistryObject<BlockEntityType<PowerSupplyBlockEntity>> POWER_SUPPLY_BE = blockEntity("power_supply", PowerSupplyBlockEntity::new, POWER_SUPPLY);
    public static final RegistryObject<BlockEntityType<com.codinglitch.simpleradio.ham.block.CoalGeneratorBlockEntity>> COAL_GENERATOR_BE = blockEntity("coal_generator", com.codinglitch.simpleradio.ham.block.CoalGeneratorBlockEntity::new, COAL_GENERATOR);

    // ---- Menus ---- \\
    public static final RegistryObject<MenuType<StationMenu>> STATION_MENU = MENUS.register("station", () -> IForgeMenuType.create(StationMenu::new));
    public static final RegistryObject<MenuType<RepeaterMenu>> REPEATER_MENU = MENUS.register("repeater", () -> IForgeMenuType.create(RepeaterMenu::new));
    public static final RegistryObject<MenuType<ScannerMenu>> SCANNER_MENU = MENUS.register("scanner", () -> IForgeMenuType.create(ScannerMenu::new));
    public static final RegistryObject<MenuType<com.codinglitch.simpleradio.ham.menu.SstvMenu>> SSTV_MENU = MENUS.register("sstv_terminal", () -> IForgeMenuType.create(com.codinglitch.simpleradio.ham.menu.SstvMenu::new));

    // ---- Recipes ---- \\
    public static final RegistryObject<RecipeSerializer<BatterySwapRecipe>> BATTERY_SWAP = RECIPE_SERIALIZERS.register("battery_swap",
            () -> new SimpleCraftingRecipeSerializer<>(BatterySwapRecipe::new));

    private static RegistryObject<Item> blockItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> blockEntity(String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);

        modBus.addListener(HamContent::buildTabs);

        HamNetwork.register();
        HamChunkLoading.register();
        modBus.addListener((RegisterGameTestsEvent event) -> event.register(HamTests.class));

        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, PowerCapabilities::attach);
        MinecraftForge.EVENT_BUS.addListener(Interference::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(HamEvents::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(HamEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(HamEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(HamEvents::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(HamEvents::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) -> com.codinglitch.simpleradio.ham.licence.HamCommands.register(event.getDispatcher()));
        com.codinglitch.simpleradio.radio.BandPlan.licensed = com.codinglitch.simpleradio.ham.licence.Licences::isLicensed;
    }

    private static void buildTabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(SimpleRadioMenus.RADIO_TAB_LOCATION)) return;

        for (RegistryObject<Item> item : List.of(STATION_ITEM, REPEATER_ITEM, SCANNER_ITEM, POWER_SUPPLY_ITEM, COAL_GENERATOR_ITEM, WIRED_STRIPPED_SPRUCE_LOG_ITEM, WIRED_SPRUCE_PLANKS_ITEM, WIRED_STONE_BRICKS_ITEM, ANTENNA_TUNER_ITEM, YAGI_ANTENNA_ITEM, DUAL_BAND_ANTENNA_ITEM, COAX_CABLE, CW_KEY_ITEM, DESK_MICROPHONE_ITEM, HAND_MICROPHONE, CHARGER_ITEM, BATTERY, FOX_TRANSMITTER_ITEM, DIRECTION_FINDER, APRS_RADIO, MOBILE_RADIO, SSTV_TERMINAL_ITEM, LICENCE_EXAM, RADIO_MANUAL)) {
            event.accept(item.get());
        }
    }
}
