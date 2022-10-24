package org.valkyrienskies.mod.common

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import org.valkyrienskies.core.config.VSConfigClass
import org.valkyrienskies.core.config.VSCoreConfig
import org.valkyrienskies.core.program.VSCore
import org.valkyrienskies.core.program.VSCoreClient
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.networking.VSGamePackets

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var TEST_CHAIR: Block
    lateinit var SHIP_CREATOR_ITEM: Item
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

        this.vsCore.networking.init()
        BlockStateInfo.init()
        VSGamePackets.register()
        VSGamePackets.registerHandlers()

        VSConfigClass.registerConfig("vs_core", VSCoreConfig::class.java)
        VSConfigClass.registerConfig("vs", VSGameConfig::class.java)
    }
}
