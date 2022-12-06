package org.valkyrienskies.mod.common.world

import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.apigame.world.ShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.core.apigame.world.properties.DimensionId
import org.valkyrienskies.core.impl.game.ships.QueryableShipDataImpl

object DummyShipWorld : ShipWorldCore {
    override fun onSetBlock(
        posX: Int, posY: Int, posZ: Int, dimensionId: DimensionId, oldBlockType: BlockType, newBlockType: BlockType,
        oldBlockMass: Double, newBlockMass: Double
    ) {
    }

    override val allShips: QueryableShipData<Ship> get() = loadedShips
    override val loadedShips: QueryableShipData<LoadedShip> = QueryableShipDataImpl()

    override fun isChunkInShipyard(chunkX: Int, chunkZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    override fun isBlockInShipyard(blockX: Int, blockY: Int, blockZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    @Deprecated("redundant", replaceWith = ReplaceWith("loadedShips.getIntersecting(aabb)"))
    override fun getShipObjectsIntersecting(aabb: AABBdc): List<LoadedShip> {
        return emptyList()
    }
}
