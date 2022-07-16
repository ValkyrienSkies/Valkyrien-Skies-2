package org.valkyrienskies.mod.common

import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.networking.impl.VSGameNetworking

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var SHIP_CREATOR_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM_SMALLER: Item
    lateinit var SHIP_MOUNTING_ENTITY_TYPE: EntityType<ShipMountingEntity>
    val MASS_DATAPACK_RESOLVER = MassDatapackResolver()

    fun init() {
        println("Hello from init")
        VSNetworking.init()
        VSGameNetworking.registerHandlers()
        BlockStateInfo.init()
    }
}
