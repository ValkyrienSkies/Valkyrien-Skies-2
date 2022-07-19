package org.valkyrienskies.mod.common.world

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkStatus
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.util.VSIterationUtils
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.core.util.set
import org.valkyrienskies.core.util.signedDistanceTo
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.set
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.client.render.LevelRendererAccessor
import org.valkyrienskies.mod.mixin.accessors.client.render.chunk.RenderChunkRegionAccessor
import org.valkyrienskies.mod.mixinducks.client.LevelChunkDuck
import kotlin.math.max

/**
 * heavily inspired by LambdAurora's LambDynamicLights, which is MIT licensed.
 */
object DynamicLighting {

    private val numChunksToRefresh = 1
    private val queueRunInterval = 2
    private val queueRefreshInterval = 10

    /**
     * Key: chunk section to rerender
     * Value: tickNum when added
     */
    private val rerenderQueue = Object2IntOpenHashMap<BlockPos>()

    private var tickNum = 0

    // Taken from LambDynLights
    private val maxLightDistance = 7.75

    private val agePriorityFactor = 30.0
    private val distancePriorityFactor = 0
    private val frustumPriorityAddition = 1e6

    /**
     * Queued rerenders older than this threshold will be discorded
     */
    private val ageThreshold = 300

    fun updateChunkLighting(level: ClientLevel, camera: Camera, frustum: Frustum) {
        tickNum++

        if (tickNum % queueRefreshInterval == 0) {
            queueRerenders(level)
            println("Queued rerenders.  Size = ${rerenderQueue.size}")
        }

        if (tickNum % queueRunInterval == 0) {
            println("Running rerenders. Size = ${rerenderQueue.size}")
            runQueuedRerenders(level, camera, frustum)
            println("Ran rerenders.     Size = ${rerenderQueue.size}")
        }
    }

    fun getLightColor(world: BlockAndTintGetter, state: BlockState, pos: BlockPos, originalLightColor: Int): Int {
        val level = (world as? RenderChunkRegionAccessor)?.level as ClientLevel? ?: return originalLightColor
        val temp0 = Vector3d()

        var additionalLight = 0.0

        if (ChunkAllocator.isBlockInShipyard(pos.x, pos.y, pos.z)) {
            val ship = level.getShipObjectManagingPos(pos) ?: return originalLightColor
            val transform = ship.renderTransform
            val posInWorld = transform.shipToWorldMatrix.transformPosition(temp0.set(pos))

            val chunkXInWorld = posInWorld.x.toInt() shr 4
            val chunkZInWorld = posInWorld.z.toInt() shr 4

            VSIterationUtils.expand2d(chunkXInWorld, chunkZInWorld) { x, z ->
                val chunk = level.getChunk(x, z, ChunkStatus.FULL, false) as? LevelChunkDuck
                chunk?.vs_getLights()?.forEach { (lightPos, luminance) ->
                    val lightPosInShip = transform.worldToShipMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    additionalLight = addAdditionalLight(pos, lightPosInShip, luminance, additionalLight)
                }
            }
        } else {
            for (ship in getShipsNear(pos, level)) {
                val shipToWorld = ship.renderTransform.shipToWorldMatrix

                ship.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                    val chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) as? LevelChunkDuck
                    // lightPos is mutable!
                    chunk?.vs_getLights()?.forEach { (lightPos, luminance) ->
                        val lightPosInWorld = shipToWorld.transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                        additionalLight = addAdditionalLight(pos, lightPosInWorld, luminance, additionalLight)
                    }
                }
            }
        }

        return getLightmapWithDynamicLight(additionalLight, originalLightColor)
    }

    fun onSetBlock(level: ClientLevel, pos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {
        val temp0 = Vector3d()

        if (ChunkAllocator.isBlockInShipyard(pos.x, pos.y, pos.z)) {
            val ship = level.getShipObjectManagingPos(pos) ?: return
            val posInWorld = ship.renderTransform.shipToWorldMatrix.transformPosition(temp0.set(pos))
            rerenderNeighborsToBlock(posInWorld)
        } else {
            for (ship in getShipsNear(pos, level)) {
                val posInShip = ship.shipData.shipTransform.shipToWorldMatrix.transformPosition(temp0.set(pos))
                rerenderNeighborsToBlock(posInShip)
            }
        }
    }

    private fun runQueuedRerenders(level: ClientLevel, camera: Camera, frustum: Frustum) {
        val prioritized = Double2ObjectAVLTreeMap<BlockPos>()

        val temp0 = AABBd()

        rerenderQueue.object2IntEntrySet().removeIf { (pos, tick) ->
            val chunkBB = temp0.set(
                pos.x * 16.0, pos.y * 16.0, pos.z * 16.0,
                pos.x * 16.0 + 16, pos.y * 16.0 + 16, pos.z * 16.0 + 16
            )
            val ship = level.getShipObjectManagingPos(pos.x, pos.z)
            if (ship != null) {
                chunkBB.transform(ship.shipData.shipTransform.shipToWorldMatrix)
            }

            val age = tickNum - tick
            val agePriority = age * agePriorityFactor
            val distancePriority = chunkBB.signedDistanceTo(camera.position.toJOML()) * distancePriorityFactor
            val frustumPriority = if (frustum.isVisible(chunkBB.toMinecraft())) frustumPriorityAddition else 0.0

            val priority = agePriority + distancePriority + frustumPriority

            // put the highest priority first
            prioritized.put(-priority, pos)

            // remove this task if the age is greater than the threshold
            age > ageThreshold
        }

        if (rerenderQueue.size > 1000) {
            println("Rerender queue has gotten too large!!!, removing ${rerenderQueue.size - 1000} items")
            val iter = rerenderQueue.object2IntEntrySet().fastIterator()
            while (rerenderQueue.size > 1000) {
                iter.remove()
            }
        }

        for ((i, pos) in prioritized.values.withIndex()) {
            if (i > numChunksToRefresh) break
            (Minecraft.getInstance().levelRenderer as LevelRendererAccessor).viewArea
                .setDirty(pos.x, pos.y, pos.z, false)
            rerenderQueue.removeInt(pos)
        }
    }

    private fun queueRerenders(level: ClientLevel) {
        for (ship in level.shipObjectWorld.shipObjects.values) {
            val prevTransform = ship.shipData.prevTickShipTransform
            val transform = ship.shipData.shipTransform

            if (prevTransform.shipPositionInWorldCoordinates.equals(
                    transform.shipPositionInWorldCoordinates, 1e-4
                ) &&
                prevTransform.shipCoordinatesToWorldCoordinatesRotation.equals(
                    transform.shipCoordinatesToWorldCoordinatesRotation, 1e-4
                ) &&
                prevTransform.shipCoordinatesToWorldCoordinatesScaling.equals(
                    transform.shipCoordinatesToWorldCoordinatesScaling, 1e-4
                )
            ) return

            val temp0 = Vector3d()

            ship.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                val chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) as? LevelChunkDuck
                // lightPos is mutable!
                chunk?.vs_getLights()?.forEach { (lightPos) ->
                    val lightPosInWorld = transform.shipToWorldMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    rerenderNeighborsToBlock(lightPosInWorld)
                }
            }

            val aabb = ship.shipData.shipAABB

            val minChunkX = aabb.minX().toInt() shr 4
            val minChunkZ = aabb.minZ().toInt() shr 4
            val maxChunkX = aabb.maxX().toInt() shr 4
            val maxChunkZ = aabb.maxZ().toInt() shr 4

            VSIterationUtils.iterate2d(minChunkX, minChunkZ, maxChunkX, maxChunkZ) { x, z ->
                val chunk = level.getChunk(x, z, ChunkStatus.FULL, false) as? LevelChunkDuck
                chunk?.vs_getLights()?.forEach { (lightPos) ->
                    val lightPosInShip = transform.worldToShipMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    rerenderNeighborsToBlock(lightPosInShip)
                }
            }
        }
    }

    private fun getShipsNear(pos: BlockPos, level: ClientLevel): List<ShipObjectClient> {
        val blockAABB = AABBd(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()
        ).expand(0.5 + maxLightDistance * 2)

        return level.shipObjectWorld.getShipObjectsIntersecting(blockAABB)
    }

    private fun rerenderNeighborsToBlock(pos: Vector3dc) {
        rerenderNeighbors(pos.x().toInt() shr 4, pos.y().toInt() shr 4, pos.z().toInt() shr 4)
    }

    private fun rerenderNeighbors(x: Int, y: Int, z: Int) {
        VSIterationUtils.expand3d(x, y, z, ::rerender)
    }

    private fun rerender(x: Int, y: Int, z: Int) {
        rerenderQueue.putIfAbsent(BlockPos(x, y, z), tickNum)
    }

    /**
     * Adds the light source at [lightPos] with [luminance] to the total amount of light at [pos],
     * represented by [additionalLight].
     *
     * @return [additionalLight] with the correct amount of light added
     */
    private fun addAdditionalLight(pos: BlockPos, lightPos: Vector3d, luminance: Int, additionalLight: Double): Double {
        val distance = lightPos.distance(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

        if (distance <= maxLightDistance) {
            val multiplier = 1.0 - distance / maxLightDistance
            val lightLevel = multiplier * luminance
            return max(additionalLight, lightLevel)
        }

        return additionalLight
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
