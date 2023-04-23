package org.valkyrienskies.mod.common.world

import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.QueryableShipData
import org.valkyrienskies.core.apigame.world.ClientShipWorldCore
import org.valkyrienskies.core.apigame.world.chunks.BlockType
import org.valkyrienskies.core.apigame.world.properties.DimensionId
import org.valkyrienskies.core.impl.game.ships.QueryableShipDataImpl
import java.net.SocketAddress

object DummyShipWorldClient : ClientShipWorldCore {
    override fun tickNetworking(server: SocketAddress) {
        throw UnsupportedOperationException()
    }

    override fun postTick() {
        throw UnsupportedOperationException()
    }

    override fun updateRenderTransforms(partialTicks: Double) {
        throw UnsupportedOperationException()
    }

    override fun destroyWorld() {
        throw UnsupportedOperationException()
    }

    override val isSyncedWithServer: Boolean
        get() = throw UnsupportedOperationException()

    override fun onSetBlock(
        posX: Int, posY: Int, posZ: Int, dimensionId: DimensionId, oldBlockType: BlockType, newBlockType: BlockType,
        oldBlockMass: Double, newBlockMass: Double
    ) {
    }

    override val allShips: QueryableShipData<ClientShip> get() = loadedShips
    override val loadedShips: QueryableShipData<ClientShip> = QueryableShipDataImpl()

    override fun isChunkInShipyard(chunkX: Int, chunkZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    override fun isBlockInShipyard(blockX: Int, blockY: Int, blockZ: Int, dimensionId: DimensionId): Boolean {
        return false
    }

    @Deprecated("redundant", replaceWith = ReplaceWith("loadedShips.getIntersecting(aabb)"))
    override fun getShipObjectsIntersecting(aabb: AABBdc): List<ClientShip> {
        return emptyList()
    }
}
