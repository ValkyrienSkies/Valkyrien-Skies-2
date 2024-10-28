package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.mod.common.util.toBlockPos
import org.valkyrienskies.mod.common.vsCore

@Deprecated("Use [ShipAssembler.assembleToShip] instead")
fun createNewShipWithBlocks(
    centerBlock: BlockPos, blocks: DenseBlockPosSet, level: ServerLevel
): ServerShip = with(vsCore.simplePacketNetworking) {
    if (blocks.isEmpty()) throw IllegalArgumentException()

    val blockList: MutableList<BlockPos> = mutableListOf()

    blocks.toList().forEach { blockList.add(it.toBlockPos()) }
    return ShipAssembler.assembleToShip(level, blockList, true, 1.0)
}
