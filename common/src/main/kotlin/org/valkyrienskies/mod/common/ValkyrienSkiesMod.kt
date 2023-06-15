package org.valkyrienskies.mod.common

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.core.apigame.VSCore
import org.valkyrienskies.core.apigame.VSCoreClient
import org.valkyrienskies.core.impl.config.VSConfigClass
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.impl.hooks.VSEvents
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.networking.VSGamePackets
import org.valkyrienskies.mod.common.util.GameTickForceApplier

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var TEST_CHAIR: Block
    lateinit var TEST_CHAIR_ITEM: Item
    lateinit var TEST_HINGE: Block
    lateinit var TEST_HINGE_ITEM: Item
    lateinit var TEST_FLAP: Block
    lateinit var TEST_FLAP_ITEM: Item
    lateinit var TEST_WING: Block
    lateinit var TEST_WING_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM: Item
    lateinit var SHIP_ASSEMBLER_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM_SMALLER: Item
    lateinit var SHIP_MOUNTING_ENTITY_TYPE: EntityType<ShipMountingEntity>
    lateinit var TEST_HINGE_BLOCK_ENTITY_TYPE: BlockEntityType<TestHingeBlockEntity>

    val VS_CREATIVE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation("valkyrienskies"))

    @JvmStatic
    var currentServer: MinecraftServer? = null

    @JvmStatic
    lateinit var vsCore: VSCore

    @JvmStatic
    val vsCoreClient get() = vsCore as VSCoreClient

    fun init(core: VSCore) {
        this.vsCore = core

        BlockStateInfo.init()
        VSGamePackets.register()
        VSGamePackets.registerHandlers()

        VSConfigClass.registerConfig("vs_core", VSCoreConfig::class.java)
        VSConfigClass.registerConfig("vs", VSGameConfig::class.java)
        VSEvents.ShipLoadEvent.on { event ->
            event.ship.setAttachment(GameTickForceApplier())
        }
    }

    fun registerCreativeTab() {
        Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            VS_CREATIVE_TAB,
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup.valkyrienSkies"))
                .icon { ItemStack(SHIP_CREATOR_ITEM) }
                .displayItems { _, output ->
                    output.accept(TEST_CHAIR_ITEM)
                    output.accept(TEST_HINGE_ITEM)
                    output.accept(TEST_FLAP_ITEM)
                    output.accept(TEST_WING_ITEM)
                    output.accept(SHIP_CREATOR_ITEM)
                    output.accept(SHIP_ASSEMBLER_ITEM)
                    output.accept(SHIP_CREATOR_ITEM_SMALLER)
                }
                .build()
        )
    }
}
