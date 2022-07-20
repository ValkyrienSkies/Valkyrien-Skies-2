package org.valkyrienskies.mod.common

import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import org.valkyrienskies.core.networking.VSNetworking
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.networking.VSGamePackets
import org.valkyrienskies.mod.common.networking.impl.VSGameNetworking

object ValkyrienSkiesMod {
    const val MOD_ID = "valkyrienskies"

    lateinit var TEST_CHAIR: Block
    lateinit var SHIP_CREATOR_ITEM: Item
    lateinit var SHIP_CREATOR_ITEM_SMALLER: Item
    lateinit var SHIP_MOUNTING_ENTITY_TYPE: EntityType<ShipMountingEntity>

    fun init() {
        VSNetworking.init()
        VSGameNetworking.registerHandlers()
        BlockStateInfo.init()
        VSGamePackets.register()
        VSGamePackets.registerHandlers()
    }
}
