package org.valkyrienskies.mod.common.entity.handling

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import org.valkyrienskies.core.networking.simple.sendToClient
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.networking.PacketSyncVSEntityTypes
import org.valkyrienskies.mod.common.util.MinecraftPlayer

// TODO if needed initialize the handler with certain settings
object VSEntityManager {
    private val entityHandlersNamed = HashMap<ResourceLocation, VSEntityHandler>()
    private val namedEntityHandlers = HashMap<VSEntityHandler, ResourceLocation>()
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
        namedEntityHandlers[entityHandler] = name
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

    // Sends a packet with all the entity -> handler pairs to the client
    fun syncHandlers(player: MinecraftPlayer) {
        PacketSyncVSEntityTypes(
            Array(Registry.ENTITY_TYPE.count()) {
                val handler = getHandler(Registry.ENTITY_TYPE.byId(it))
                namedEntityHandlers[handler].toString()
            }
        ).sendToClient(player)
    }
}
