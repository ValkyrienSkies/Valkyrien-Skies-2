package org.valkyrienskies.mod.forge.common

import net.minecraft.commands.Commands.CommandSelection.ALL
import net.minecraft.commands.Commands.CommandSelection.INTEGRATED
import net.minecraft.commands.synchronization.ArgumentTypeInfos
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Item.Properties
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.event.AddReloadListenerEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.TagsUpdatedEvent
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
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
import org.valkyrienskies.mod.common.command.RelativeVector3Argument
import org.valkyrienskies.mod.common.command.ShipArgument
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
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import kotlin.jvm.java

@Mod(MOD_ID)
object ValkyrienSkiesModForge {
    private val BLOCKS = DeferredRegister.Blocks.createBlocks(MOD_ID)
    private val ITEMS = DeferredRegister.Items.createItems(MOD_ID)
    private val ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID)
    private val BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID)
    private val DATA_COMPONENTS = DeferredRegister.DataComponents.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID)
    private val COMMAND_ARGUMENT_TYPES = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, MOD_ID)
    private val TEST_CHAIR_REGISTRY: DeferredBlock<Block>
    private val TEST_HINGE_REGISTRY: DeferredBlock<Block>
    private val TEST_FLAP_REGISTRY: DeferredBlock<Block>
    private val TEST_WING_REGISTRY: DeferredBlock<Block>
    private val TEST_SPHERE_REGISTRY: DeferredBlock<Block>
    private val CONNECTION_CHECKER_ITEM_REGISTRY: DeferredItem<Item>
    private val SHIP_CREATOR_ITEM_REGISTRY: DeferredItem<Item>
    private val SHIP_CREATOR_SMALLER_ITEM_REGISTRY: DeferredItem<Item>
    private val AREA_ASSEMBLER_ITEM_REGISTRY: DeferredItem<Item>
    private val PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY: DeferredItem<Item>
    private val SHIP_MOUNTING_ENTITY_REGISTRY: DeferredHolder<EntityType<*>, EntityType<ShipMountingEntity>>
    private val PHYSICS_ENTITY_TYPE_REGISTRY: DeferredHolder<EntityType<*>, EntityType<VSPhysicsEntity>>
    private val SHIP_ASSEMBLER_ITEM_REGISTRY: DeferredItem<Item>
    private val TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY: DeferredHolder<BlockEntityType<*>, BlockEntityType<TestHingeBlockEntity>>
    private val BLOCK_POS_COMPONENT: DeferredHolder<DataComponentType<*>, DataComponentType<BlockPos>>

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

        val modBus = MOD_BUS
        val forgeBus = FORGE_BUS

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
        modBus.addListener(VSForgeNetworking::register)
        DATA_COMPONENTS.register(modBus)
        COMMAND_ARGUMENT_TYPES.register(modBus)

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory::class.java) {
            IConfigScreenFactory { _, parent ->
                VSClothConfig.createConfigScreenFor(
                    parent,
                    VSCoreConfig::class.java,
                    VSGameConfig::class.java
                )
            }
        }

        TEST_CHAIR_REGISTRY = registerBlockAndItem("test_chair") { TestChairBlock() }
        TEST_HINGE_REGISTRY = registerBlockAndItem("test_hinge") { TestHingeBlock() }
        TEST_FLAP_REGISTRY = registerBlockAndItem("test_flap") { TestFlapBlock() }
        TEST_WING_REGISTRY = registerBlockAndItem("test_wing") { TestWingBlock() }
        TEST_SPHERE_REGISTRY = registerBlockAndItem("test_sphere") { TestSphereBlock }
        SHIP_CREATOR_ITEM_REGISTRY =
            ITEMS.register("ship_creator") { ->
                ShipCreatorItem(Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling })
            }
        CONNECTION_CHECKER_ITEM_REGISTRY =
            ITEMS.register("connection_checker") { ->
                ConnectionCheckerItem(
                    Properties(),
                    { 1.0 },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        SHIP_CREATOR_SMALLER_ITEM_REGISTRY =
            ITEMS.register("ship_creator_smaller") { ->
                ShipCreatorItem(
                    Properties(),
                    { VSGameConfig.SERVER.miniShipSize },
                    { VSGameConfig.SERVER.minScaling }
                )
            }
        AREA_ASSEMBLER_ITEM_REGISTRY = ITEMS.register("area_assembler") { ->
            AreaAssemblerItem(
                Properties(),
                { 1.0 },
                { VSGameConfig.SERVER.minScaling }
            )
        }
        PHYSICS_ENTITY_CREATOR_ITEM_REGISTRY =
            ITEMS.register("physics_entity_creator") { ->
                PhysicsEntityCreatorItem(
                    Properties(),
                )
            }

        SHIP_MOUNTING_ENTITY_REGISTRY = ENTITIES.register("ship_mounting_entity") { ->
            EntityType.Builder.of(
                ::ShipMountingEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .build(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ship_mounting_entity").toString())
        }

        PHYSICS_ENTITY_TYPE_REGISTRY = ENTITIES.register("vs_physics_entity") { ->
            EntityType.Builder.of(
                ::VSPhysicsEntity,
                MobCategory.MISC
            ).sized(.3f, .3f)
                .setUpdateInterval(1)
                .clientTrackingRange(10)
                .build(ResourceLocation.fromNamespaceAndPath(MOD_ID, "vs_physics_entity").toString())
        }

        SHIP_ASSEMBLER_ITEM_REGISTRY =
            ITEMS.register("ship_assembler") { -> ShipAssemblerItem(Properties()) }

        TEST_HINGE_BLOCK_ENTITY_TYPE_REGISTRY = BLOCK_ENTITIES.register<BlockEntityType<TestHingeBlockEntity>>("test_hinge_block_entity") { ->
            BlockEntityType.Builder.of(::TestHingeBlockEntity, TEST_HINGE_REGISTRY.get()).build(null)
        }

        BLOCK_POS_COMPONENT = DATA_COMPONENTS.register("coordinate") { ->
            DataComponentType.builder<BlockPos>().persistent(BlockPos.CODEC).build()
        }

        COMMAND_ARGUMENT_TYPES.register("ship_argument") { ->
            ArgumentTypeInfos.registerByClass(
                ShipArgument::class.java,
                SingletonArgumentInfo.contextFree(ShipArgument::selectorOnly),
            )
        }

        COMMAND_ARGUMENT_TYPES.register("relative_vector_3") { ->
            ArgumentTypeInfos.registerByClass(
                RelativeVector3Argument::class.java,
                SingletonArgumentInfo.contextFree(RelativeVector3Argument::relativeVector3),
            )
        }

        val deferredRegister = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)
        deferredRegister.register("general") { ->
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

    private fun registerBlockAndItem(registryName: String, blockSupplier: () -> Block): DeferredBlock<Block> {
        val blockRegistry = BLOCKS.register(registryName, blockSupplier)
        ITEMS.register(registryName) { -> BlockItem(blockRegistry.get(), Properties()) }
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
        ValkyrienSkiesMod.BLOCK_POS_COMPONENT = BLOCK_POS_COMPONENT.get()
    }
}
