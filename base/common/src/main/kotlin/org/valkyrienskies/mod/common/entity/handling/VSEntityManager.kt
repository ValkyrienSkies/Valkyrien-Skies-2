package org.valkyrienskies.mod.common.entity.handling

import com.google.common.cache.CacheBuilder
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.networking.PacketSyncVSEntityTypes
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.compat.CreateCompat
import java.time.Duration
import kotlin.text.RegexOption.IGNORE_CASE

// TODO if needed initialize the handler with certain settings
object VSEntityManager {
    private val entityHandlersNamed = HashMap<ResourceLocation, VSEntityHandler>()
    private val namedEntityHandlers = HashMap<VSEntityHandler, ResourceLocation>()
    private val entityHandlers = HashMap<EntityType<*>, VSEntityHandler>()
    private val default = WorldEntityHandler
    private var contraptionHandler: VSEntityHandler = DefaultShipyardEntityHandler

    private val defaultHandlersCache =
        CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).build<EntityType<*>, VSEntityHandler>()

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

    // Uses some heuristics to try to figure out which VSEntityHandler we should use for an entity
    private fun getDefaultHandler(entity: Entity): VSEntityHandler {
        return defaultHandlersCache.get(entity.type) { determineDefaultHandler(entity) }
    }

    private val seatRegistryName = "(?<![a-z])(seat|chair)(?![a-z])".toRegex(IGNORE_CASE)

    private fun determineDefaultHandler(entity: Entity): VSEntityHandler {
        try {
            val className = entity::class.java.simpleName
            val registryName = Registry.ENTITY_TYPE.getKey(entity.type)

            if (className.contains("SeatEntity", true) || registryName.path.contains(seatRegistryName)) {
                return DefaultShipyardEntityHandler
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

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

        with(vsCore.simplePacketNetworking) {
            PacketSyncVSEntityTypes(entityTypes).sendToClient(player)
        }
    }
}
