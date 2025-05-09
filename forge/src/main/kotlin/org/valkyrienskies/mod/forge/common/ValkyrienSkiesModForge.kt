package org.valkyrienskies.mod.forge.common

import net.minecraft.commands.Commands.CommandSelection.ALL
import net.minecraft.commands.Commands.CommandSelection.INTEGRATED
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TagsUpdatedEvent
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject
import org.valkyrienskies.core.apigame.VSCoreFactory
import org.valkyrienskies.core.impl.config_impl.VSCoreConfig
import org.valkyrienskies.mod.client.EmptyRenderer
import org.valkyrienskies.mod.client.VSPhysicsEntityRenderer
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.ValkyrienSkiesMod.MOD_ID
import org.valkyrienskies.mod.common.block.TestChairBlock
import org.valkyrienskies.mod.common.block.TestFlapBlock
import org.valkyrienskies.mod.common.block.TestHingeBlock
import org.valkyrienskies.mod.common.block.TestSphereBlock
import org.valkyrienskies.mod.common.block.TestWingBlock
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.command.VSCommands
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSEntityHandlerDataLoader
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.config.VSKeyBindings
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.entity.VSPhysicsEntity
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager
import org.valkyrienskies.mod.common.hooks.VSGameEvents
import org.valkyrienskies.mod.common.item.AreaAssemblerItem
import org.valkyrienskies.mod.common.item.ConnectionCheckerItem
import org.valkyrienskies.mod.common.item.PhysicsEntityCreatorItem
import org.valkyrienskies.mod.common.item.ShipAssemblerItem
import org.valkyrienskies.mod.common.item.ShipCreatorItem
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

@Mod(MOD_ID)
class ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)
    private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)
    private val ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID)
    private val TEST_CHAIR_REGISTRY: RegistryObject<Block>
    private val TEST_HINGE_REGISTRY: RegistryObject<Block>
    private val TEST_FLAP_REGISTRY: RegistryObject<Block>
    private val TEST_WING_REGISTRY: RegistryObject<Block>
    private val TEST_SPHERE_REGISTRY: RegistryObject<Block>
    private val CONNECTION_CHECKER_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: RegistryObject<Item>
    private val AREA_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>
    private val PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY: RegistryObject<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: RegistryObject<EntityType<ShipMountingEntity>>
    private val PHYSICS_ENTITY_TYPE_REGISTRY: RegistryObject<EntityType<VSPhysicsEntity>>
    private val SHIP_ASSEMBLER_ITEM_REGISTRY: RegistryObject<Item>
    private val TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY: RegistryObject<BlockEntityType<TestHingeBlockEntity>>

    init {
        val isClient = FMLEnvironment.dist.isClient
        val vsCore = if (isClient) {
            VSCoreFactory.instance.newVsCoreClient(ForgeHooksImpl)
        } else {
            VSCoreFactory.instance.newVsCoreServer(ForgeHooksImpl)
        }

        VSForgeNetworking.registerPacketHandlers(vsCore.hooks)

        ValkyrienSkiesMod.init(vsCore)
        VSEntityManager.registerContraptionHandler(ContraptionShipyardEntityHandlerForge)

        val modBus = Bus.MOD.bus().get()
        val forgeBus = Bus.FORGE.bus().get()

        BLOCKS.register(modBus)
        ITEMS.register(modBus)
        ENTITIES.register(modBus)
        BLOCK_ENTITIES.register(modBus)
        if (isClient) {
            modBus.addListener(::registerKeyBindings)
            modBus.addListener(::entityRenderers)
        }
        modBus.addListener(::loadComplete)

        forgeBus.addListener(::registerCommands)
        forgeBus.addListener(::tagsUpdated)
        forgeBus.addListener(::registerResourceManagers)

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory::class.java) {
            ConfigScreenHandler.ConfigScreenFactory { _, parent ->
                VSClothConfig.createConfigScreenFor(
                    parent,
                    VSCoreConfig::class.java,
                    VSGameConfig::class.java
                )
            }
        }

        TEST_CHAIR_REGISTRY = registerBlockAndItem("test_chair") { TestChairBlock }
        TEST_HINGE_REGISTRY = registerBlockAndItem("test_hinge") { TestHingeBlock }
        TEST_FLAP_REGISTRY = registerBlockAndItem("test_flap") { TestFlapBlock }
        TEST_WING_REGISTRY = registerBlockAndItem("test_wing") { TestWingBlock }
        TEST_SPHERE_REGISTRY = registerBlockAndItem("test_sphere") { TestSphereBlock }
        SHIP_CREATOR_ITEM_REGISTRY =
            ITEMS.register("ship_creator") {
                ShipCreatorItem(Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling })
            }
        CONNECTION_CHECKER_ITEM_REGISTRY =
            ITEMS.register("connection_checker") {
                ConnectionCheckerItem(
                    Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        SHIP_CREATOR_SMALLER_ITEM_REGISTRY =
            ITEMS.register("ship_creator_smaller") {
                ShipCreatorItem(
                    Properties(),
                    { VSGameConfig.SERVER.miniShipSize },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        AREA_ASSEMBLER_ITEM_REGISTRY = ITEMS.register("area_assembler") {
            AreaAssemblerItem(
                Properties(),
                { 1.0 },
                { VSGameConfig.SERVER.minScaling }
            )
        }
        PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY =
            ITEMS.register("physics_entity_creator") {
                PhysicsEntityCreatorItem(
                    Properties(),
                )
            }

        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") {
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation(MOD_ID, "ship_mounting_entity").toString())
        }

        PHYSICS_ENTITY_TYPE_REGISTRY = ENTITIES.register("vs_physics_entity") {
            EntityType.Builder.of(
                ::VSPhysicsEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .setUpdateInterval(1)
                .clientTrackingRange(10)
                .build(ResourceLocation(MOD_ID, "vs_physics_entity").toString())
        }

        SHIP_ASSEMBLER_ITEM_REGISTRY =
            ITEMS.register("ship_assembler") { ShipAssemblerItem(Properties()) }
        TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register("test_hinge_block_entity") {
            BlockEntityType.Builder.of(::TestHingeBlockEntity, TestHingeBlock).build(null)
        }

        val deferredRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)
        deferredRegister.register("general") {
            ValkyrienSkiesMod.createCreativeTab()
        }
        deferredRegister.register(modBus)
    }

    private fun registerResourceManagers(event: AddReloadListenerEvent) {
        event.addListener(MassDatapackResolver.loader)
        event.addListener(VSEntityHandlerDataLoader)
    }

    private fun registerKeyBindings(event: RegisterKeyMappingsEvent) {
        VSKeyBindings.clientSetup {
            event.register(it)
        }
    }

    private fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(SHIP_MOUNTING_ENTITY_REGISTRY.get(), ::EmptyRenderer)
        event.registerEntityRenderer(PHYSICS_ENTITY_TYPE_REGISTRY.get(), ::VSPhysicsEntityRenderer)
    }

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block): RegistryObject<Block> {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { BlockItem(blockRegistry.get(), Properties()) }
        return blockRegistry
    }

    private fun registerCommands(event: RegisterCommandsEvent) {
        VSCommands.registerServerCommands(event.dispatcher)

        if (event.commandSelection == ALL || event.commandSelection == INTEGRATED) {
            VSCommands.registerClientCommands(event.dispatcher)
        }
    }

    private fun tagsUpdated(event: TagsUpdatedEvent) {
        VSGameEvents.tagsAreLoaded.emit(Unit)
    }

    private fun loadComplete(event: FMLLoadCompleteEvent) {
        ValkyrienSkiesMod.TEST_CHAIR = TEST_CHAIR_REGISTRY.get()
        ValkyrienSkiesMod.TEST_HINGE = TEST_HINGE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_FLAP = TEST_FLAP_REGISTRY.get()
        ValkyrienSkiesMod.TEST_WING = TEST_WING_REGISTRY.get()
        ValkyrienSkiesMod.TEST_SPHERE = TEST_SPHERE_REGISTRY.get()
        ValkyrienSkiesMod.CONNECTION_CHECKER_ITEM = CONNECTION_CHECKER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM = SHIP_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_ASSEMBLER_ITEM = SHIP_ASSEMBLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_CREATOR_ITEM_SMALLER = SHIP_CREATOR_SMALLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.AREA_ASSEMBLER_ITEM = AREA_ASSEMBLER_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.PHYSICS_ENTITY_CREATOR_ITEM = PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY.get()
        ValkyrienSkiesMod.SHIP_MOUNTING_ENTITY_TYPE = SHIP_MOUNTING_ENTITY_REGISTRY.get()
        ValkyrienSkiesMod.PHYSICS_ENTITY_TYPE = PHYSICS_ENTITY_TYPE_REGISTRY.get()
        ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE = TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY.get()
    }
}
