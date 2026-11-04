package org.valkyrienskies.mod.api_impl.events

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.VsCoreApi
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.api.world.ServerShipWorld
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.core.util.events.EventEmitterImpl
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener
import org.valkyrienskies.mod.api.VsApi
import org.valkyrienskies.mod.api.events.PostRenderShipEvent
import org.valkyrienskies.mod.api.events.PreRenderShipEvent
import org.valkyrienskies.mod.api.events.RegisterBlockStateEvent
import org.valkyrienskies.mod.api.getShipManagingBlock
import org.valkyrienskies.mod.api.shipWorld
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.entity.ShipMountingEntity
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.getShipMountedToData
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.util.DimensionIdProvider
import java.util.concurrent.ConcurrentHashMap

@Suppress("OVERRIDE_DEPRECATION")
class VsApiImpl(
    private val core: VsCoreApi
) : VsApi, VsCoreApi by core {

    override val registerBlockStateEvent = EventEmitterImpl<RegisterBlockStateEvent>()
    override val preRenderShipEvent = EventEmitterImpl<PreRenderShipEvent>()
    override val postRenderShipEvent = EventEmitterImpl<PostRenderShipEvent>()

    override fun isShipMountingEntity(entity: Entity): Boolean {
        return entity is ShipMountingEntity
    }

    override fun getDimensionId(level: Level): String =
        (level as DimensionIdProvider).dimensionId

    override fun getShipMountedTo(entity: Entity?): Ship? =
        entity?.let { org.valkyrienskies.mod.common.getShipMountedTo(it) }

    override fun getMountPosInShip(entity: Entity?): Vector3dc? =
        entity?.let { getShipMountedToData(it) }?.mountPosInShip

    override fun getMountPosInShip(entity: Entity?, partialTicks: Float): Vector3dc? =
        entity?.let { getShipMountedToData(it, partialTicks) }?.mountPosInShip

    @GameTickOnly
    override fun getServerShipWorld(): ServerShipWorld? =
        getServerShipWorld(ValkyrienSkiesMod.currentServer)

    @GameTickOnly
    override fun getServerShipWorld(server: MinecraftServer?): ServerShipWorld? =
        (server as IShipObjectWorldServerProvider?)?.shipObjectWorld

    @GameTickOnly
    override fun getClientShipWorld(): ClientShipWorld? =
        getClientShipWorld(Minecraft.getInstance())

    @GameTickOnly
    override fun getClientShipWorld(clientLevel: ClientLevel?): ClientShipWorld? =
        (clientLevel as IShipObjectWorldClientProvider?)?.shipObjectWorld

    @GameTickOnly
    override fun getClientShipWorld(client: Minecraft?): ClientShipWorld? =
        (client as IShipObjectWorldClientProvider?)?.shipObjectWorld

    @GameTickOnly
    override fun getShipWorld(level: Level?): ShipWorld? =
        when {
            level == null -> null
            level is ServerLevel -> getServerShipWorld(level.server)
            level is ClientLevel && level.isClientSide -> getClientShipWorld(level)
            else -> null
        }

    @GameTickOnly
    override fun isChunkInShipyard(level: Level?, chunkX: Int, chunkZ: Int): Boolean =
        level != null && getShipWorld(level)?.isChunkInShipyard(chunkX, chunkZ, getDimensionId(level)) ?: false

    override fun getShipManagingBlock(level: Level?, pos: BlockPos?): Ship? {
        return pos?.let { level?.getShipManagingPos(it) }
    }

    override fun getShipManagingChunk(level: Level?, pos: ChunkPos?): Ship? {
        return pos?.let { level?.getShipManagingPos(it) }
    }

    override fun getShipManagingChunk(level: Level?, chunkX: Int, chunkZ: Int): Ship? {
        return level?.getShipManagingPos(chunkX, chunkZ)
    }

    override fun getShipsIntersecting(level: Level?, aabb: AABBdc?): Iterable<Ship> {
        if (level == null || aabb == null) return emptyList()
        return level.getShipsIntersecting(aabb)
    }

    override fun getShipsIntersecting(level: Level?, x: Double, y: Double, z: Double): Iterable<Ship> =
        getShipsIntersecting(level, AABBd(x, y, z, x, y, z))
}
