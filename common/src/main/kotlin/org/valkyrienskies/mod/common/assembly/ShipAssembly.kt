package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Clearable
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.joml.Vector3d
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.core.util.datastructures.DenseBlockPosSet
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.isTickingChunk
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.util.relocateBlock
import org.valkyrienskies.mod.util.updateBlock

@OptIn(VsBeta::class)
@Deprecated("Use [ShipAssembler.assembleToShip] instead")
fun createNewShipWithBlocks(
    centerBlock: BlockPos, blocks: DenseBlockPosSet, level: ServerLevel
): ServerShip {
    if (blocks.isEmpty()) throw IllegalArgumentException()
    //return ShipAssembler.assembleToShip(level, blocks, true, 1.0)


    val ship = level.shipObjectWorld.createNewShipAtBlock(centerBlock.toJOML(), false, 1.0, level.dimensionId)
    val shipChunkX = ship.chunkClaim.xMiddle
    val shipChunkZ = ship.chunkClaim.zMiddle
    val worldChunkX = centerBlock.x shr 4
    val worldChunkZ = centerBlock.z shr 4
    val deltaX = worldChunkX - shipChunkX
    val deltaZ = worldChunkZ - shipChunkZ
    val chunksToBeUpdated = mutableMapOf<ChunkPos, Pair<ChunkPos, ChunkPos>>()
    blocks.forEachChunk { x, _, z, _ ->
        val sourcePos = ChunkPos(x, z)
        val destPos = ChunkPos(x - deltaX, z - deltaZ)
        chunksToBeUpdated[sourcePos] = Pair(sourcePos, destPos)
    }
    val chunkPairs = chunksToBeUpdated.values.toList()
    val chunkPoses = chunkPairs.flatMap { it.toList() }
    val chunkPosesJOML = chunkPoses.map { it.toJOML() }
    // Send a list of all the chunks that we plan on updating to players, so that they
    // defer all updates until assembly is finished
    // with(vsCore.simplePacketNetworking) {
    //     PacketStopChunkUpdates(chunkPosesJOML).sendToAllClients()
    // }
    // Use relocateBlock to copy all the blocks into the ship
    blocks.forEachChunk { chunkX, chunkY, chunkZ, chunk ->
        val sourceChunk = level.getChunk(chunkX, chunkZ)
        val destChunk = level.getChunk(chunkX - deltaX, chunkZ - deltaZ)
        chunk.forEach { x, y, z ->
            val fromPos = BlockPos((sourceChunk.pos.x shl 4) + x, (chunkY shl 4) + y, (sourceChunk.pos.z shl 4) + z)
            val toPos = BlockPos((destChunk.pos.x shl 4) + x, (chunkY shl 4) + y, (destChunk.pos.z shl 4) + z)
            relocateBlock(sourceChunk, fromPos, destChunk, toPos, false, ship)
        }
    }
    // Use updateBlock to update blocks after copying
    blocks.forEachChunk { chunkX, chunkY, chunkZ, chunk ->
        val sourceChunk = level.getChunk(chunkX, chunkZ)
        val destChunk = level.getChunk(chunkX - deltaX, chunkZ - deltaZ)
        chunk.forEach { x, y, z ->
            val fromPos = BlockPos((sourceChunk.pos.x shl 4) + x, (chunkY shl 4) + y, (sourceChunk.pos.z shl 4) + z)
            val toPos = BlockPos((destChunk.pos.x shl 4) + x, (chunkY shl 4) + y, (destChunk.pos.z shl 4) + z)
            updateBlock(destChunk.level, fromPos, toPos, destChunk.getBlockState(toPos))
        }
    }
    // Calculate the position of the block that the player clicked after it has been assembled
    val centerInShip = Vector3d(
        ((shipChunkX shl 4) + (centerBlock.x and 15)).toDouble(),
        centerBlock.y.toDouble(),
        ((shipChunkZ shl 4) + (centerBlock.z and 15)).toDouble()
    )
    // The ship's position has shifted from the center block since we assembled the ship, compensate for that
    val centerBlockPosInWorld = ship.inertiaData.centerOfMass.sub(centerInShip, Vector3d())
        .add(ship.transform.positionInWorld)
    // Put the ship into the compensated position, so that all the assembled blocks stay in the same place
    // TODO: AAAAAAAAA THIS IS HORRIBLE how can the API support this?
    // well now it doesnt kekw
    //(ship as ShipDataCommon).transform = (ship.transform).withTransformFrom(positionInWorld = centerBlockPosInWorld)

    (ship as ShipDataCommon).setFromTransform(vsApi.transformFactory.create(centerBlockPosInWorld, ship.transform.rotation, ship.transform.scaling, ship.transform.positionInShip))
    level.server.executeIf(
        // This condition will return true if all modified chunks have been both loaded AND
        // chunk update packets were sent to players
        { chunkPoses.all(level::isTickingChunk) }
    ) {
        // Once all the chunk updates are sent to players, we can tell them to restart chunk updates
        // with(vsCore.simplePacketNetworking) {
        //     PacketRestartChunkUpdates(chunkPosesJOML).sendToAllClients()
        // }
    }

    return ship
}

fun createNewShipWithStructure(
    lowerCorner: BlockPos, higherCorner: BlockPos, blocks: StructureTemplate, level: ServerLevel
): ServerShip {
    //if (blocks.size.toJOML().length() < 0.0001) throw IllegalArgumentException()

    val ship = level.shipObjectWorld.createNewShipAtBlock(lowerCorner.toJOML(), false, 1.0, level.dimensionId)
    val shipChunkX = ship.chunkClaim.xMiddle
    val shipChunkZ = ship.chunkClaim.zMiddle

    // Calculate the position of the block that the player clicked after it has been assembled
    val lowerCornerInShip = Vector3d(
        ((shipChunkX shl 4) + (lowerCorner.x and 15)).toDouble(),
        lowerCorner.y.toDouble(),
        ((shipChunkZ shl 4) + (lowerCorner.z and 15)).toDouble()
    )
    val higherCornerInShip = Vector3d(
        ((shipChunkX shl 4) + (higherCorner.x and 15)).toDouble(),
        higherCorner.y.toDouble(),
        ((shipChunkZ shl 4) + (higherCorner.z and 15)).toDouble()
    )

    blocks.placeInWorld(level, BlockPos(lowerCornerInShip.toMinecraft()), BlockPos(lowerCornerInShip.toMinecraft()), StructurePlaceSettings(), level.random, Block.UPDATE_ALL)

    val diff = higherCorner.subtract(lowerCorner)
    val centerPos = lowerCorner.toJOMLD().add(diff.x + 1 / 2.0, diff.y + 1 / 2.0, diff.z + 1 / 2.0)

    // The ship's position has shifted from the center block since we assembled the ship, compensate for that
    val centerBlockPosInWorld = ship.inertiaData.centerOfMass.sub(centerPos, Vector3d())
        .add(ship.transform.positionInWorld)
    // Put the ship into the compensated position, so that all the assembled blocks stay in the same place
    level.shipObjectWorld
        .teleportShip(ship, ShipTeleportDataImpl(newPos = centerBlockPosInWorld.add(0.5, 128.5 - centerBlockPosInWorld.y, 0.5, Vector3d()), newPosInShip = ship.inertiaData.centerOfMass))


    for (x in lowerCorner.x..higherCorner.x) {
        for (y in lowerCorner.y..higherCorner.y) {
            for (z in lowerCorner.z..higherCorner.z) {
                if (!level.getBlockState(BlockPos(x, y, z)).isAir) {
                    val blockEntity: BlockEntity? = level.getBlockEntity(BlockPos(x, y, z))
                    Clearable.tryClear(blockEntity)
                    level.removeBlockEntity(BlockPos(x, y, z))
                    level.getChunk(x,z).setBlockState(BlockPos(x,y,z), org.valkyrienskies.mod.util.AIR, false)

                    //level.getChunk(BlockPos(x, y, z)).setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), false)
                }
            }
        }
    }
    return ship
}
