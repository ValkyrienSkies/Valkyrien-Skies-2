package org.valkyrienskies.mod.common.entity.handling

import net.minecraft.world.entity.EntityType

object VSEntityManager {

    private val entityHandler = HashMap<EntityType<*>, VSEntityHandler>()
    private val default = WorldEntityHandler

    init {
        entityHandler[EntityType.ITEM_FRAME] = ShipyardEntityHandler
    }

    fun getHandler(Type: EntityType<*>): VSEntityHandler {
        return entityHandler[Type] ?: default
    }
}
