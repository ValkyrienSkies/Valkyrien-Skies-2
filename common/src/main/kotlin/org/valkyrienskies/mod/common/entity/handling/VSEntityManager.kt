package org.valkyrienskies.mod.common.entity.handling

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

// TODO if needed initialize the handler with certain settings
object VSEntityManager {
    private val entityHandlersNamed = HashMap<ResourceLocation, VSEntityHandler>()
    private val entityHandlers = HashMap<EntityType<*>, VSEntityHandler>()
    private val default = WorldEntityHandler

    init {
        register(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "shipyard"), ShipyardEntityHandler)
        register(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "default"), WorldEntityHandler)
    }

    /**
     * Register the handler with a name
     *
     * @param name The name of the entity handler
     * @param entityHandler The entity handler
     */
    fun register(name: ResourceLocation, entityHandler: VSEntityHandler) {
        entityHandlersNamed[name] = entityHandler
    }

    /**
     * Pair the entity type with the entity handler
     * Should be preferably configured via datapacks
     *
     * @param entityType The entity type
     * @param entityHandler The entity handler
     */
    fun pair(entityType: EntityType<*>, entityHandler: VSEntityHandler) {
        entityHandlers[entityType] = entityHandler
    }

    fun getHandler(type: EntityType<*>): VSEntityHandler {
        return entityHandlers[type] ?: default
    }

    fun getHandler(type: ResourceLocation): VSEntityHandler? {
        return entityHandlersNamed[type]
    }
}
