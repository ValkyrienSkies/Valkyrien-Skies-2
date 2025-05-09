package org.valkyrienskies.mod.api_impl.events

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.mod.api.VsApi
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.events.PreRenderShipEvent
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipMountedToData
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.compat.clothconfig.VSClothConfig

@Suppress("OVERRIDE_DEPRECATION")
class VsApiImpl(
    private val core: VsCoreApi
) : VsApi, VsCoreApi by core {

    override val registerBlockStateEvent = EventEmitterImpl<RegisterBlockStateEvent>()
    override val preRenderShipEvent = EventEmitterImpl<PreRenderShipEvent>()
    override val postRenderShipEvent = EventEmitterImpl<PostRenderShipEvent>()

    override fun createConfigScreenLegacy(parent: Screen, vararg configs: Class<*>): Screen
        = VSClothConfig.createConfigScreenFor(parent, *configs)

    override fun getDimensionId(level: Level): String =
        level.dimensionId

    override fun getShipMountedTo(entity: Entity?): Ship? =
        entity?.let { org.valkyrienskies.mod.common.getShipMountedTo(it) }

    override fun getMountPosInShip(entity: Entity?): Vector3dc? =
        entity?.let { getShipMountedToData(it) }?.mountPosInShip

    override fun getMountPosInShip(entity: Entity?, partialTicks: Float): Vector3dc? =
        entity?.let { getShipMountedToData(it, partialTicks) }?.mountPosInShip

    override fun getServerShipWorld(): ServerShipWorld? =
        getServerShipWorld(ValkyrienSkiesMod.currentServer)

    override fun getServerShipWorld(server: MinecraftServer?): ServerShipWorld? =
        (server as IShipObjectWorldServerProvider?)?.shipObjectWorld

    override fun getClientShipWorld(): ClientShipWorld? =
        getClientShipWorld(Minecraft.getInstance())

    override fun getShipWorld(level: Level?): ShipWorld? =
        when (level) {
            is ServerLevel -> getServerShipWorld(level.server)
            is ClientLevel -> getClientShipWorld()
            else -> null
        }

    override fun isChunkInShipyard(level: Level?, chunkX: Int, chunkZ: Int): Boolean {
        if (level == null) return false
        return getShipWorld(level)?.isChunkInShipyard(chunkX, chunkZ, getDimensionId(level)) ?: false
    }

    override fun getClientShipWorld(client: Minecraft?): ClientShipWorld? =
        (client as IShipObjectWorldClientProvider?)?.shipObjectWorld

    override fun getShipManagingChunk(level: Level?, chunkX: Int, chunkZ: Int): Ship? =
        level?.getShipManagingPos(chunkX, chunkZ)

    override fun getShipManagingChunk(level: ClientLevel?, chunkX: Int, chunkZ: Int): ClientShip? =
        level?.getShipObjectManagingPos(chunkX, chunkZ)

    override fun getShipManagingChunk(level: ServerLevel?, chunkX: Int, chunkZ: Int): ServerShip? =
        level?.getShipObjectManagingPos(chunkX, chunkZ)

    override fun getShipsIntersecting(level: Level?, aabb: AABBdc?): Iterable<Ship> {
        if (level == null || aabb == null) return emptyList()
        return level.getShipsIntersecting(aabb)
    }

    override fun getShipsIntersecting(level: Level?, x: Double, y: Double, z: Double): Iterable<Ship> =
        getShipsIntersecting(level, AABBd(x, y, z, x, y, z))
}
