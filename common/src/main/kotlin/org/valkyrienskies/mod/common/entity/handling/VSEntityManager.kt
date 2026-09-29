package org.valkyrienskies.mod.common.entity.handling

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.Projectile
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.networking.PacketSyncVSEntityTypes
import org.valkyrienskies.mod.common.util.MinecraftPlayer
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.compat.CreateCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.text.RegexOption.IGNORE_CASE

// TODO if needed initialize the handler with certain settings
object VSEntityManager {
    private val entityHandlersNamed = HashMap<ResourceLocation, VSEntityHandler>()
    private val namedEntityHandlers = HashMap<VSEntityHandler, ResourceLocation>()
    private val entityHandlers = HashMap<EntityType<*>, VSEntityHandler>()
    private val default = WorldEntityHandler
    private var contraptionHandler: VSEntityHandler = DefaultShipyardEntityHandler

    // The default handler is a function of the entity type alone (its registry name, class and whether it is a
    // Projectile), so it is determined once per type and kept; the map is bounded by the entity type registry, and
    // getHandler consults the explicit pairs first, so a later pair() still wins over a remembered default.
    private val defaultHandlers = ConcurrentHashMap<EntityType<*>, VSEntityHandler>()

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
        val type = entity.type
        val cached = defaultHandlers[type]
        if (cached != null) {
            return cached
        }
        // A run of the heuristics that threw is not remembered, so a one-off failure cannot stick to the type
        val handler = determineDefaultHandler(entity) ?: return default
        return defaultHandlers.putIfAbsent(type, handler) ?: handler
    }

    private val seatRegistryName = "(?<![a-z])(seat|chair)(?![a-z])".toRegex(IGNORE_CASE)

    private fun determineDefaultHandler(entity: Entity): VSEntityHandler? {
        try {
            val className = entity::class.java.simpleName
            val registryName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type)

            if (entity is Projectile || className.contains("SeatEntity", true) || registryName.path.contains(seatRegistryName)) {
                return DefaultShipyardEntityHandler
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        }

        return default
    }

    fun getHandler(type: ResourceLocation): VSEntityHandler? {
        return entityHandlersNamed[type]
    }

    // Sends a packet with all the entity -> handler pairs to the client
    fun syncHandlers(player: MinecraftPlayer) {
        val entityTypes: Map<Int, String> =
            (0 until BuiltInRegistries.ENTITY_TYPE.count())
                .asSequence()
                .mapNotNull { i ->
                    val handler = entityHandlers[BuiltInRegistries.ENTITY_TYPE.byId(i)] ?: return@mapNotNull null
                    i to namedEntityHandlers[handler].toString()
                }
                .toMap()

        with(vsCore.simplePacketNetworking) {
            PacketSyncVSEntityTypes(entityTypes).sendToClient(player)
        }
    }

    @JvmStatic
    fun isShipyardEntity(entity: Entity): Boolean {
        return getHandler(entity) == DefaultShipyardEntityHandler
    }
}
