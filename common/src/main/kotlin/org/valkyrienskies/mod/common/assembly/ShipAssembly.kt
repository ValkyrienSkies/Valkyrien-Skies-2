package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import org.joml.Vector3d
import org.valkyrienskies.core.datastructures.DenseBlockPosSet
import org.valkyrienskies.core.game.VSBlockType.AIR
import org.valkyrienskies.core.game.ships.ShipData
import org.valkyrienskies.core.networking.simple.sendToClient
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML

fun createNewShipWithBlocks(
    centerBlock: BlockPos, blocks: DenseBlockPosSet, level: ServerLevel
): ShipData {
    val ship = level.shipObjectWorld.createNewShipAtBlock(centerBlock.toJOML(), false, 1.0, level.dimensionId)

    val shipChunkX = ship.chunkClaim.xMiddle
    val shipChunkZ = ship.chunkClaim.zMiddle

    val worldChunkX = centerBlock.x shr 4
    val worldChunkZ = centerBlock.z shr 4

    val deltaX = worldChunkX - shipChunkX
    val deltaZ = worldChunkZ - shipChunkZ

    // source pos -> Pair<source, dest>
    val updateChunks = mutableMapOf<ChunkPos, Pair<LevelChunk, LevelChunk>>()
    val lights = level.chunkSource.lightEngine

    blocks.forEachChunk { chunkX, chunkY, chunkZ, chunk ->
        println("Moving section: $chunkX $chunkY $chunkZ")

        val sourceChunk = level.getChunk(chunkX, chunkZ)
        val destChunk = level.getChunk(chunkX - deltaX, chunkZ - deltaZ)

        val sourceSection = sourceChunk.sections[chunkY]
            ?: throw IllegalArgumentException(
                "Chunk at $chunkX $chunkY $chunkZ doesn't have a section that exists at $chunkY"
            )

        var destSection = destChunk.sections[chunkY]

        if (destSection == null) {
            destSection = LevelChunkSection(chunkY shl 4)
            destChunk.sections[chunkY] = destSection
        }

        chunk.forEach { x, y, z ->
            println("Moving block $x $y $z in section $chunkX $chunkY $chunkZ")

            val blockState = sourceSection.getBlockState(x, y, z)
            destSection.setBlockState(x, y, z, blockState, false)
            sourceSection.setBlockState(x, y, z, Blocks.AIR.defaultBlockState(), false)

            BlockStateInfo.get(blockState)?.let { (mass, type) ->
                ship.onSetBlock(
                    (destChunk.pos.x shl 4) + x,
                    (chunkY shl 4) + y,
                    (destChunk.pos.z shl 4) + z,
                    AIR, type, 0.0, mass
                )
            }
        }

        val pos = SectionPos.of(chunkX, chunkY, chunkZ)
        lights.updateSectionStatus(pos, false)
        lights.updateSectionStatus(SectionPos.of(destChunk.pos.x, pos.y, destChunk.pos.z), false)

        updateChunks[sourceChunk.pos] = sourceChunk to destChunk
    }

    val centerInShip = Vector3d(
        ((shipChunkX shl 4) + (centerBlock.x and 15)).toDouble(),
        centerBlock.y.toDouble(),
        ((shipChunkZ shl 4) + (centerBlock.z and 15)).toDouble()
    )
    val centerBlockPosInWorld = ship.inertiaData.getCenterOfMassInShipSpace().sub(centerInShip, Vector3d())
        .add(ship.shipTransform.shipPositionInWorldCoordinates)

    ship.shipTransform = ship.shipTransform.copy(
        shipPositionInWorldCoordinates = centerBlockPosInWorld
    )

    // List<Pair<Player, Pair<[source, dest]>>>
    val chunkPairsPlayerIsWatching = mutableListOf<Pair<ServerPlayer, List<Pair<LevelChunk, LevelChunk>>>>()
    level.players().forEach { player ->
        val modifiedChunksThatPlayerIsWatching = updateChunks.filter { (pos, _) ->
            level.chunkSource.chunkMap.getPlayers(pos, false).anyMatch { it == player }
        }.values.toList()

        chunkPairsPlayerIsWatching.add(player to modifiedChunksThatPlayerIsWatching)
    }

    chunkPairsPlayerIsWatching.forEach { (player, chunkPairs) ->
        val chunks = chunkPairs.flatMap { it.toList() }
        val chunkPoses = chunks.map { it.pos }
        val chunkPairPoses = chunkPairs.map { (c1, c2) -> c1.pos to c2.pos }

        PacketStopChunkUpdates(chunkPoses).sendToClient(player.playerWrapper)

        chunks.forEach { chunk ->
            chunk.markUnsaved()

            val chunkPacket = ClientboundLevelChunkPacket(chunk, 65535)
            // Send chunk update packet
            player.connection.send(chunkPacket)


            lights.lightChunk(chunk, false).thenAccept {
                val lightUpdatePacket = ClientboundLightUpdatePacket(chunk.pos, lights, true)

                // Send light update packet
                player.connection.send(lightUpdatePacket)
            }
        }

        PacketRestartChunkUpdates(chunkPoses, chunkPairPoses, ship.id).sendToClient(player.playerWrapper)
    }


    return ship
}
