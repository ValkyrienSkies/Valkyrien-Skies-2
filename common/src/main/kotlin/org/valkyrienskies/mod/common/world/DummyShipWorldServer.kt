package org.valkyrienskies.mod.common.world

import org.joml.Vector3ic
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.apigame.ShipTeleportData
import org.valkyrienskies.core.apigame.constraints.VSConstraint
import org.valkyrienskies.core.apigame.constraints.VSConstraintId
import org.valkyrienskies.core.apigame.world.IPlayer
import org.valkyrienskies.core.apigame.world.ServerShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.core.apigame.world.chunks.ChunkUnwatchTask
import org.valkyrienskies.core.apigame.world.chunks.ChunkWatchTask
import org.valkyrienskies.core.apigame.world.chunks.ChunkWatchTasks
import org.valkyrienskies.core.apigame.world.chunks.TerrainUpdate
import org.valkyrienskies.core.apigame.world.properties.DimensionId
import org.valkyrienskies.core.impl.game.ships.QueryableShipDataImpl

object DummyShipWorldServer : ServerShipWorldCore {
    override var players: Set<IPlayer> = emptySet()

    override fun addTerrainUpdates(dimensionId: DimensionId, terrainUpdates: List<TerrainUpdate>) {
        TODO("Not yet implemented")
    }

    override fun getIPlayersWatchingShipChunk(chunkX: Int, chunkZ: Int, dimensionId: DimensionId): Iterator<IPlayer> {
        TODO("Not yet implemented")
    }

    override fun getChunkWatchTasks(): ChunkWatchTasks {
        TODO("Not yet implemented")
    }

    override fun setExecutedChunkWatchTasks(
        watchTasks: Iterable<ChunkWatchTask>, unwatchTasks: Iterable<ChunkUnwatchTask>
    ) {
        TODO("Not yet implemented")
    }

    override fun createNewShipAtBlock(
        blockPosInWorldCoordinates: Vector3ic, createShipObjectImmediately: Boolean, scaling: Double,
        dimensionId: DimensionId
    ): ServerShip {
        TODO("Not yet implemented")
    }

    override fun createNewConstraint(vsConstraint: VSConstraint): VSConstraintId? {
        TODO("Not yet implemented")
    }

    override fun updateConstraint(constraintId: VSConstraintId, updatedVSConstraint: VSConstraint): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeConstraint(constraintId: VSConstraintId): Boolean {
        TODO("Not yet implemented")
    }

    override fun addDimension(dimensionId: DimensionId, yRange: LevelYRange) {
        TODO("Not yet implemented")
    }

    override fun removeDimension(dimensionId: DimensionId) {
        TODO("Not yet implemented")
    }

    override fun onDisconnect(player: IPlayer) {
    }

    override fun deleteShip(ship: ServerShip) {
        TODO("Not yet implemented")
    }

    override fun teleportShip(ship: ServerShip, teleportData: ShipTeleportData) {
        TODO("Not yet implemented")
    }

    override val dimensionToGroundBodyIdImmutable: Map<DimensionId, ShipId>
        get() = TODO("Not yet implemented")

    override fun onSetBlock(
        posX: Int, posY: Int, posZ: Int, dimensionId: DimensionId, oldBlockType: BlockType, newBlockType: BlockType,
        oldBlockMass: Double, newBlockMass: Double
    ) {
    }

    override val allShips: QueryableShipData<ServerShip>
        get() = loadedShips
    override val loadedShips: QueryableShipData<LoadedServerShip> = QueryableShipDataImpl()

    override fun isChunkInShipyard(chunkX: Int, chunkZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    override fun isBlockInShipyard(blockX: Int, blockY: Int, blockZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    @Deprecated("redundant", replaceWith = ReplaceWith("loadedShips.getIntersecting(aabb)"))
    override fun getShipObjectsIntersecting(aabb: AABBdc): List<LoadedServerShip> {
        return emptyList()
    }
}
