package org.valkyrienskies.mod.common.world

import it.unimi.dsi.fastutil.doubles.Double2ObjectAVLTreeMap
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.valkyrienskies.core.game.ChunkAllocator
import org.valkyrienskies.core.game.ships.ShipObjectClient
import org.valkyrienskies.core.game.ships.ShipTransform
import org.valkyrienskies.core.util.VSIterationUtils
import org.valkyrienskies.core.util.expand
import org.valkyrienskies.core.util.set
import org.valkyrienskies.core.util.signedDistanceTo
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.set
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.util.toMinecraft
import org.valkyrienskies.mod.mixin.accessors.client.render.LevelRendererAccessor
import org.valkyrienskies.mod.mixin.accessors.client.render.chunk.RenderChunkRegionAccessor
import org.valkyrienskies.mod.mixinducks.client.LevelChunkDuck
import java.util.WeakHashMap
import kotlin.math.max

/**
 * heavily inspired by LambdAurora's LambDynamicLights, which is MIT licensed.
 */
object DynamicLighting {

    private val numChunksToRefresh = 1
    private val queueRunInterval = 2
    private val queueRefreshInterval = 10

    /**
     * Key: chunk section to rerender (chunk coordinates, not block coordinates)
     * Value: tickNum when added
     */
    private val rerenderQueue = Object2IntOpenHashMap<BlockPos>()
    private val prevRenderedTransforms = WeakHashMap<ShipObjectClient, ShipTransform>()

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

    val rerenderQueueSize get() = rerenderQueue.size

    fun updateChunkLighting(level: ClientLevel, camera: Camera, frustum: Frustum) {
        tickNum++

        if (tickNum % queueRefreshInterval == 0) {
            queueRerenders(level)
        }

        if (tickNum % queueRunInterval == 0) {
            runQueuedRerenders(level, camera, frustum)
        }
    }

    fun getLightColor(world: BlockAndTintGetter, state: BlockState, pos: BlockPos, originalLightColor: Int): Int {
        val level = (world as? RenderChunkRegionAccessor)?.level as ClientLevel? ?: return originalLightColor
        val temp0 = Vector3d()

        var additionalLight = 0.0

        val posInWorld = pos.toJOMLD()

        if (ChunkAllocator.isBlockInShipyard(pos.x, pos.y, pos.z)) {
            val ship = level.getShipObjectManagingPos(pos) ?: return originalLightColor

            ship.renderTransform.shipToWorldMatrix.transformPosition(posInWorld)

            val chunkXInWorld = posInWorld.x.toInt() shr 4
            val chunkZInWorld = posInWorld.z.toInt() shr 4

            VSIterationUtils.expand2d(chunkXInWorld, chunkZInWorld) { x, z ->
                level.getLights(x, z)?.forEach { (lightPos, luminance) ->
                    val lightPosInShip = ship.renderTransform.worldToShipMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    additionalLight = addAdditionalLight(pos, lightPosInShip, luminance, additionalLight)
                }
            }
        }

        for (ship in getShipsNear(posInWorld, level)) {
            val shipToWorld = ship.renderTransform.shipToWorldMatrix

            ship.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                level.getLights(chunkX, chunkZ)?.forEach { (lightPos, luminance) ->
                    val lightPosInWorld = shipToWorld.transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    additionalLight = addAdditionalLight(posInWorld, lightPosInWorld, luminance, additionalLight)
                }
            }
        }


        return getLightmapWithDynamicLight(additionalLight, originalLightColor)
    }

    fun onLoadChunk(level: ClientLevel, pos: ChunkPos, chunk: LevelChunk) {
        chunk.sections.forEachIndexed { i, _ ->
            rerenderNeighborChunksToBlock(level, BlockPos(pos.x * 16, i * 16, pos.z * 16))
        }
    }

    fun onSetBlock(level: ClientLevel, pos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {
        if (prevBlockState.lightEmission == newBlockState.lightEmission) return

        rerenderNeighborChunksToBlock(level, pos, true)
    }

    private fun rerenderNeighborChunksToBlock(level: ClientLevel, pos: BlockPos, immediate: Boolean = false) {
        val ship = level.getShipObjectManagingPos(pos)

        val posInWorld = pos.toJOMLD()

        if (ship != null) {
            ship.renderTransform.shipToWorldMatrix.transformPosition(posInWorld)
            rerenderNeighborsToBlockCoordinate(posInWorld, immediate)
        }

        for (nearbyShip in getShipsNear(posInWorld, level)) {
            val posInShip =
                nearbyShip.shipData.shipTransform.worldToShipMatrix.transformPosition(posInWorld, Vector3d())
            rerenderNeighborsToBlockCoordinate(posInShip, immediate)
        }
    }

    private fun runQueuedRerenders(level: ClientLevel, camera: Camera, frustum: Frustum) {
        val prioritized = Double2ObjectAVLTreeMap<BlockPos>()

        val temp0 = AABBd()

        var droppedRerenders = 0
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

            if (age > ageThreshold) droppedRerenders++

            // remove this task if the age is greater than the threshold
            age > ageThreshold
        }

        if (droppedRerenders > 0)
            println("Dropping $droppedRerenders rerenders due to age")

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
            val prevTransform = prevRenderedTransforms[ship]
            val transform = ship.shipData.shipTransform

            if (prevTransform != null &&
                prevTransform.shipPositionInWorldCoordinates.equals(
                    transform.shipPositionInWorldCoordinates, 1e-6
                ) &&
                prevTransform.shipCoordinatesToWorldCoordinatesRotation.equals(
                    transform.shipCoordinatesToWorldCoordinatesRotation, 1e-6
                ) &&
                prevTransform.shipCoordinatesToWorldCoordinatesScaling.equals(
                    transform.shipCoordinatesToWorldCoordinatesScaling, 1e-6
                )
            ) return

            prevRenderedTransforms[ship] = transform

            val temp0 = Vector3d()

            // Rerender chunks in world near lights on the ship

            ship.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                level.getLights(chunkX, chunkZ)?.forEach { (lightPos) ->
                    val lightPosInWorld = transform.shipToWorldMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    rerenderNeighborsToBlockCoordinate(lightPosInWorld)
                }
            }

            // Rerender chunks in ship near lights on other ships

            val expandedAabb = AABBd(ship.shipData.shipAABB).expand(16.0)

            for (otherShip in level.shipObjectWorld.getShipObjectsIntersecting(expandedAabb)) {
                val otherShipToShipTransform = transform.worldToShipMatrix
                    .mul(otherShip.renderTransform.shipToWorldMatrix, Matrix4d())

                otherShip.shipData.shipActiveChunksSet.iterateChunkPos { chunkX, chunkZ ->
                    level.getLights(chunkX, chunkZ)?.forEach { (lightPos) ->
                        val lightPosInShip = otherShipToShipTransform
                            .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                        rerenderNeighborsToBlockCoordinate(lightPosInShip)
                    }
                }
            }

            // Rerender chunks in ship near lights in the world
            val aabb = ship.shipData.shipAABB

            val minChunkX = aabb.minX().toInt() shr 4
            val minChunkZ = aabb.minZ().toInt() shr 4
            val maxChunkX = aabb.maxX().toInt() shr 4
            val maxChunkZ = aabb.maxZ().toInt() shr 4

            VSIterationUtils.iterate2d(minChunkX - 1, minChunkZ - 1, maxChunkX + 1, maxChunkZ + 1) { x, z ->
                level.getLights(x, z)?.forEach { (lightPos) ->
                    val lightPosInShip = transform.worldToShipMatrix
                        .transformPosition(temp0.set(lightPos).add(0.5, 0.5, 0.5))

                    rerenderNeighborsToBlockCoordinate(lightPosInShip)
                }
            }
        }
    }

    private fun ClientLevel.getLights(chunkX: Int, chunkZ: Int): Object2IntMap<BlockPos>? {
        return (getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) as? LevelChunkDuck)?.vs_getLights()
    }

    private fun getShipsNear(pos: Vector3dc, level: ClientLevel): List<ShipObjectClient> {
        val aabb = AABBd(pos, pos).expand(maxLightDistance)

        return level.shipObjectWorld.getShipObjectsIntersecting(aabb)
    }

    private fun getShipsNear(pos: BlockPos, level: ClientLevel): List<ShipObjectClient> {
        val blockAABB = AABBd(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()
        ).expand(maxLightDistance)

        return level.shipObjectWorld.getShipObjectsIntersecting(blockAABB)
    }

    private fun rerenderNeighborsToBlockCoordinate(pos: Vector3dc, immediate: Boolean = false) {
        rerenderNeighbors(pos.x().toInt() shr 4, pos.y().toInt() shr 4, pos.z().toInt() shr 4, immediate)
    }

    private fun rerenderNeighbors(chunkX: Int, chunkY: Int, chunkZ: Int, immediate: Boolean) {
        VSIterationUtils.expand3d(chunkX, chunkY, chunkZ) { x, y, z -> rerender(x, y, z, immediate) }
    }

    private fun rerender(chunkX: Int, chunkY: Int, chunkZ: Int, immediate: Boolean) {
        if (immediate) {
            (Minecraft.getInstance().levelRenderer as LevelRendererAccessor).viewArea
                .setDirty(chunkX, chunkY, chunkZ, immediate)
        } else {
            rerenderQueue.putIfAbsent(BlockPos(chunkX, chunkY, chunkZ), tickNum)
        }
    }

    /**
     * Adds the light source at [lightPos] with [luminance] to the total amount of light at [pos],
     * represented by [additionalLight].
     *
     * @return [additionalLight] with the correct amount of light added
     */
    private fun addAdditionalLight(
        pos: BlockPos, lightPos: Vector3dc, luminance: Int, additionalLight: Double
    ): Double {
        val distance = lightPos.distance(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

        if (distance <= maxLightDistance) {
            val multiplier = 1.0 - distance / maxLightDistance
            val lightLevel = multiplier * luminance
            return max(additionalLight, lightLevel)
        }

        return additionalLight
    }

    /**
     * Adds the light source at [lightPos] with [luminance] to the total amount of light at [pos],
     * represented by [additionalLight].
     *
     * @return [additionalLight] with the correct amount of light added
     */
    private fun addAdditionalLight(
        pos: Vector3dc, lightPos: Vector3dc, luminance: Int, additionalLight: Double
    ): Double {
        val distance = lightPos.distance(pos)

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
