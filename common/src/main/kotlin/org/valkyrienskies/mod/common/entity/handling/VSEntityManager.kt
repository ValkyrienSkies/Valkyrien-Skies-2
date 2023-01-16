package org.valkyrienskies.mod.common.entity.handling

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.valkyrienskies.core.impl.networking.simple.sendToClient
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.networking.PacketSyncVSEntityTypes
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.compat.CreateCompat

// TODO if needed initialize the handler with certain settings
object VSEntityManager {
    private val entityHandlersNamed = HashMap<ResourceLocation, VSEntityHandler>()
    private val namedEntityHandlers = HashMap<VSEntityHandler, ResourceLocation>()
    private val entityHandlers = HashMap<EntityType<*>, VSEntityHandler>()
    private val default = WorldEntityHandler
    private var contraptionHandler: VSEntityHandler = DefaultShipyardEntityHandler

    init {
        register(ResourceLocation(ValkyrienSkiesMod.MOD_ID, "shipyard"), DefaultShipyardEntityHandler)
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

    fun registerContraptionHandler(contraptionHandler: VSEntityHandler) {
        this.contraptionHandler = contraptionHandler
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

    fun getHandler(entity: Entity): VSEntityHandler {
        if (CreateCompat.isContraption(entity)) {
            return contraptionHandler
        }
        return entityHandlers[entity.type] ?: getDefaultHandler(entity)
    }

    private fun getDefaultHandler(entity: Entity): VSEntityHandler {
        return default
    }

    fun getHandler(type: ResourceLocation): VSEntityHandler? {
        return entityHandlersNamed[type]
    }

    // Sends a packet with all the entity -> handler pairs to the client
    fun syncHandlers(player: MinecraftPlayer) {
        val entityTypes: Map<Int, String> =
            (0 until Registry.ENTITY_TYPE.count())
                .asSequence()
                .mapNotNull { i ->
                    val handler = entityHandlers[Registry.ENTITY_TYPE.byId(i)] ?: return@mapNotNull null
                    i to namedEntityHandlers[handler].toString()
                }
                .toMap()

        PacketSyncVSEntityTypes(entityTypes).sendToClient(player)
    }
}
