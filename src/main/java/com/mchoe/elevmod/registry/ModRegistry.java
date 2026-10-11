package com.mchoe.elevmod.registry;

import com.mchoe.elevmod.ElevMod;
import com.mchoe.elevmod.block.ControllerBlock;
import com.mchoe.elevmod.block.DecorBlock;
import com.mchoe.elevmod.block.DeviceBlock;
import com.mchoe.elevmod.block.DeviceBlockEntity;
import com.mchoe.elevmod.block.LandingDoorBlock;
import com.mchoe.elevmod.block.LandingDoorBlockEntity;
import com.mchoe.elevmod.block.MachineBlock;
import com.mchoe.elevmod.block.MachineBlockEntity;
import com.mchoe.elevmod.elevator.ControllerBlockEntity;
import com.mchoe.elevmod.entity.ElevatorCarEntity;
import com.mchoe.elevmod.item.ElevItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ElevMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ElevMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ElevMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ElevMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElevMod.MODID);

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties device() {
        return metal().noOcclusion().noCollission().lightLevel(s -> 3);
    }

    private static <B extends Block> RegistryObject<B> block(String name, Supplier<B> sup) {
        RegistryObject<B> r = BLOCKS.register(name, sup);
        ITEMS.register(name, () -> new BlockItem(r.get(), new Item.Properties()));
        return r;
    }

    // ---------------------------------------------------------------- 블록
    public static final RegistryObject<Block> CONTROLLER = block("controller", () -> new ControllerBlock(metal()));
    public static final RegistryObject<Block> LANDING_DOOR = block("landing_door", () -> new LandingDoorBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> HALL_BUTTON = block("hall_button", () -> new DeviceBlock(device(), DeviceBlock.Kind.HALL_BUTTON));
    public static final RegistryObject<Block> HALL_PANEL = block("hall_panel", () -> new DeviceBlock(device(), DeviceBlock.Kind.HALL_PANEL));
    public static final RegistryObject<Block> INDICATOR_SEGMENT = block("indicator_segment", () -> new DeviceBlock(device(), DeviceBlock.Kind.IND_SEGMENT));
    public static final RegistryObject<Block> INDICATOR_DOT = block("indicator_dot", () -> new DeviceBlock(device(), DeviceBlock.Kind.IND_DOT));
    public static final RegistryObject<Block> INDICATOR_LCD = block("indicator_lcd", () -> new DeviceBlock(device(), DeviceBlock.Kind.IND_LCD));
    public static final RegistryObject<Block> INDICATOR_LAMP = block("indicator_lamp", () -> new DeviceBlock(device(), DeviceBlock.Kind.IND_LAMP));
    public static final RegistryObject<Block> HALL_LANTERN = block("hall_lantern", () -> new DeviceBlock(device(), DeviceBlock.Kind.LANTERN));
    public static final RegistryObject<Block> FIRE_SWITCH = block("fire_switch", () -> new DeviceBlock(device(), DeviceBlock.Kind.FIRE_SWITCH));
    public static final RegistryObject<Block> DESTINATION_PANEL = block("destination_panel", () -> new DeviceBlock(device(), DeviceBlock.Kind.DESTINATION));
    public static final RegistryObject<Block> CALL_RECEIVER = block("call_receiver", () -> new DeviceBlock(device(), DeviceBlock.Kind.CALL_RECEIVER));
    public static final RegistryObject<Block> MACHINE_GEARED = block("machine_geared", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GEARED));
    public static final RegistryObject<Block> MACHINE_GEARLESS = block("machine_gearless", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GEARLESS));
    public static final RegistryObject<Block> GOVERNOR = block("governor", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.GOVERNOR));
    public static final RegistryObject<Block> HYDRAULIC_UNIT = block("hydraulic_unit", () -> new MachineBlock(metal().noOcclusion(), MachineBlock.Kind.HYDRAULIC));
    public static final RegistryObject<Block> BUFFER = block("buffer", () -> new DecorBlock(metal().noOcclusion(), DecorBlock.Kind.BUFFER));
    public static final RegistryObject<Block> GUIDE_RAIL = block("guide_rail", () -> new DecorBlock(metal().noOcclusion(), DecorBlock.Kind.RAIL));

    // ---------------------------------------------------------------- 아이템
    public static final RegistryObject<Item> LINKER = ITEMS.register("linker", () -> new ElevItem(new Item.Properties().stacksTo(1), ElevItem.Type.LINKER));
    public static final RegistryObject<Item> DOOR_KEY = ITEMS.register("door_key", () -> new ElevItem(new Item.Properties().stacksTo(1), ElevItem.Type.DOOR_KEY));
    public static final RegistryObject<Item> FIRE_KEY = ITEMS.register("fire_key", () -> new ElevItem(new Item.Properties().stacksTo(1), ElevItem.Type.FIRE_KEY));
    public static final RegistryObject<Item> OPERATOR_KEY = ITEMS.register("operator_key", () -> new ElevItem(new Item.Properties().stacksTo(1), ElevItem.Type.OPERATOR_KEY));
    public static final RegistryObject<Item> TESTER = ITEMS.register("tester", () -> new ElevItem(new Item.Properties().stacksTo(1), ElevItem.Type.TESTER));
    public static final RegistryObject<Item> ROTATOR = ITEMS.register("rotator", () -> new com.mchoe.elevmod.item.RotatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DESIGN_TOOL = ITEMS.register("design_tool", () -> new com.mchoe.elevmod.item.DesignToolItem(new Item.Properties().stacksTo(1)));

    // ---------------------------------------------------------------- 블록엔티티
    public static final RegistryObject<BlockEntityType<ControllerBlockEntity>> CONTROLLER_BE = BLOCK_ENTITIES.register("controller",
            () -> BlockEntityType.Builder.of(ControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<BlockEntityType<LandingDoorBlockEntity>> LANDING_DOOR_BE = BLOCK_ENTITIES.register("landing_door",
            () -> BlockEntityType.Builder.of(LandingDoorBlockEntity::new, LANDING_DOOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<DeviceBlockEntity>> DEVICE_BE = BLOCK_ENTITIES.register("device",
            () -> BlockEntityType.Builder.of(DeviceBlockEntity::new, HALL_BUTTON.get(), HALL_PANEL.get(), INDICATOR_SEGMENT.get(),
                    INDICATOR_DOT.get(), INDICATOR_LCD.get(), INDICATOR_LAMP.get(), HALL_LANTERN.get(), FIRE_SWITCH.get(),
                    DESTINATION_PANEL.get(), CALL_RECEIVER.get()).build(null));
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE_BE = BLOCK_ENTITIES.register("machine",
            () -> BlockEntityType.Builder.of(MachineBlockEntity::new, MACHINE_GEARED.get(), MACHINE_GEARLESS.get(), GOVERNOR.get(), HYDRAULIC_UNIT.get()).build(null));

    // ---------------------------------------------------------------- 엔티티
    public static final RegistryObject<EntityType<ElevatorCarEntity>> CAR = ENTITIES.register("car",
            () -> EntityType.Builder.<ElevatorCarEntity>of(ElevatorCarEntity::new, MobCategory.MISC)
                    .sized(1.4f, 0.2f).clientTrackingRange(16).updateInterval(1).fireImmune().noSummon()
                    .build("car"));

    // ---------------------------------------------------------------- 크리에이티브 탭
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.elevmod"))
            .icon(() -> new ItemStack(CONTROLLER.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(r -> out.accept(r.get())))
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }
}
