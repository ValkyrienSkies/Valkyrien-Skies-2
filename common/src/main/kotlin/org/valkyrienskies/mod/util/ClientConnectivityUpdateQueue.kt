package org.valkyrienskies.mod.util

import net.minecraft.client.Minecraft
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunkSection
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toDenseVoxelUpdate
import org.valkyrienskies.mod.common.vsCore

object ClientConnectivityUpdateQueue {
    val toInitialize = ArrayDeque<Pair<ChunkPos, Boolean>>()

    @JvmStatic
    fun queueChunkForInitialization(pos: ChunkPos, forceUpdate: Boolean) {
        toInitialize.addLast(Pair(pos, forceUpdate))
    }

    fun onRegistriesCompleted() {
        do {
            val (pos, shouldForce) = toInitialize.removeFirstOrNull() ?: break
            val level = Minecraft.getInstance().level ?: continue
            val worldChunk = level.getChunk(pos.x, pos.z) ?: continue
            val clientShipWorld = level.shipObjectWorld
            if (clientShipWorld != null && VSGameConfig.CLIENT.Connectivity.enableClientConnectivity) {
                val voxelShapeUpdates: ArrayList<VsiTerrainUpdate> = ArrayList()

                val chunkSections = worldChunk.getSections()

                for (sectionY in (0 until chunkSections.size)) {
                    val chunkSection: LevelChunkSection = chunkSections[sectionY]
                    val chunkPos: Vector3ic = Vector3i(pos.x, worldChunk.getSectionYFromSectionIndex(sectionY), pos.z)

                    if (chunkSection != null && !chunkSection.hasOnlyAir()) {
                        // Add this chunk to the ground rigid body
                        val voxelShapeUpdate: VsiTerrainUpdate =
                            chunkSection.toDenseVoxelUpdate(chunkPos)
                        voxelShapeUpdates.add(voxelShapeUpdate)
                    } else {
                        val emptyVoxelShapeUpdate: VsiTerrainUpdate = vsCore
                            .newEmptyVoxelShapeUpdate(chunkPos.x(), chunkPos.y(), chunkPos.z(), true)
                        voxelShapeUpdates.add(emptyVoxelShapeUpdate)
                    }
                }
                if (!shouldForce) {
                    clientShipWorld.addTerrainUpdates(vsApi.getDimensionId(level), voxelShapeUpdates)
                } else {
                    for (update in voxelShapeUpdates) {
                        clientShipWorld.forceUpdateConnectivityChunk(
                            vsApi.getDimensionId(level),
                            update.chunkX,
                            update.chunkY,
                            update.chunkZ,
                            update
                        )
                    }
                }
            }
        } while (toInitialize.isNotEmpty())
    }
}
