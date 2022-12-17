package org.valkyrienskies.mod.common

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import org.valkyrienskies.core.api.ships.setAttachment
import org.valkyrienskies.core.apigame.VSCore
import org.valkyrienskies.core.apigame.VSCoreClient
import org.valkyrienskies.core.impl.config.VSConfigClass
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.impl.hooks.VSEvents
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.networking.VSGamePackets
import org.valkyrienskies.mod.common.util.GameTickForceApplier

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var TEST_CHAIR: Block
    lateinit var TEST_HINGE: Block
    lateinit var SHIP_CREATOR_ITEM: Item
    lateinit var SHIP_ASSEMBLER_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM_SMALLER: Item
    lateinit var SHIP_MOUNTING_ENTITY_TYPE: EntityType<ShipMountingEntity>

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
}
