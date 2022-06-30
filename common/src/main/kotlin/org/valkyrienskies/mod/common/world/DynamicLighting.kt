package org.valkyrienskies.mod.common.world

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkStatus
import org.joml.Vector3d
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.util.x
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.set
import org.valkyrienskies.mod.mixin.accessors.client.render.chunk.RenderChunkRegionAccessor
import org.valkyrienskies.mod.mixinducks.client.LevelChunkDuck
import kotlin.math.max

/**
 * heavily inspired by LambdAurora's LambDynamicLights, which is MIT licensed.
 */
object DynamicLighting {

    @JvmStatic
    fun updateChunkLighting(level: ClientLevel) {
        // TODO: actually use the ship's aabb to determine this...
        for (ship in level.shipObjectWorld.shipObjects.values) {
            val shipPos = ship.renderTransform.shipPositionInWorldCoordinates
            for (y in 0..15) {
                level.setSectionDirtyWithNeighbors(shipPos.x.toInt() shr 4, y, shipPos.z().toInt() shr 4)
            }
        }
    }

    @JvmStatic
    fun getLightColor(world: BlockAndTintGetter, state: BlockState, pos: BlockPos, originalLightColor: Int): Int {
        if (ChunkAllocator.isBlockInShipyard(pos.x, pos.y, pos.z)) {
            return originalLightColor // todo: for now dynamic lighting is only from ship -> world
        }

        val level = (world as? RenderChunkRegionAccessor)?.level as ClientLevel? ?: return originalLightColor
        val shipWorld = level.shipObjectWorld
        val temp0 = Vector3d()

        var additionalLight = 0.0

        for (ship in shipWorld.shipObjects.values) {
            val shipToWorld = ship.renderTransform.shipToWorldMatrix

            ship.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                val chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) as? LevelChunkDuck
                // lightPos is mutable!
                chunk?.`vs$getLights`()?.forEach { (lightPos, luminance) ->
                    val lightPosInWorld = shipToWorld.transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))
                    val distance = lightPosInWorld.distance(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

                    if (distance <= 7.75) {
                        val multiplier = 1.0 - distance / 7.75
                        val lightLevel = multiplier * luminance
                        additionalLight = max(additionalLight, lightLevel)
                    }
                }
            }
        }

        return getLightmapWithDynamicLight(additionalLight, originalLightColor)
    }

    /**
     * This function modified from LambDynamicLights, under the MIT license
     *
     * Copyright © 2020 LambdAurora <aurora42lambda@gmail.com>
     */
    private fun getLightmapWithDynamicLight(dynamicLightLevel: Double, lightmap: Int): Int {
        var lightmap = lightmap
        if (dynamicLightLevel > 0.0) {
            val blockLevel = LightTexture.block(lightmap)
            if (dynamicLightLevel > blockLevel.toDouble()) {
                val luminance = (dynamicLightLevel * 16.0).toInt()
                lightmap = lightmap and -1048576
                lightmap = lightmap or (luminance and 1048575)
            }
        }
        return lightmap
    }
}
