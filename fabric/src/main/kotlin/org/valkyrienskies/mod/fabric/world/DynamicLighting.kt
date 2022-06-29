package org.valkyrienskies.mod.fabric.world

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import me.lambdaurora.lambdynlights.DynamicLightSource
import me.lambdaurora.lambdynlights.LambDynLights
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction.DOWN
import net.minecraft.core.Direction.EAST
import net.minecraft.core.Direction.NORTH
import net.minecraft.core.Direction.SOUTH
import net.minecraft.core.Direction.UP
import net.minecraft.core.Direction.WEST
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.Vector3d
import org.valkyrienskies.core.game.ships.ShipDataCommon
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.util.set
import java.util.stream.Collectors
import kotlin.math.abs

interface DynamicLightingListener {
    fun onChunkLoad(chunkX: Int, chunkZ: Int, chunk: LevelChunk) {}
    fun onChunkUnload(chunkX: Int, chunkZ: Int) {}
    fun onSetBlockState(level: Level, blockPos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {}
}

private object NoDynamicLighting : DynamicLightingListener
private object DynamicLighting : DynamicLightingListener {

    val main = FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer::class.java)
        .filterIsInstance<LambDynLights>()
        .first()

    class ShipLightSource(
        val pos: BlockPos,
        val emission: Int,
        val ship: ShipDataCommon,
        val level: Level
    ) : DynamicLightSource {

        val transformedPos = Vector3d()
        val prevTransformedPos = Vector3d()

        private var trackedLitChunkPos = LongOpenHashSet()

        override fun getDynamicLightX(): Double {
            return transformedPos.x
        }

        override fun getDynamicLightY(): Double {
            return transformedPos.y
        }

        override fun getDynamicLightZ(): Double {
            return transformedPos.z
        }

        override fun getDynamicLightWorld(): Level {
            return level
        }

        override fun resetDynamicLight() {
        }

        override fun getLuminance(): Int {
            return emission
        }

        override fun dynamicLightTick() {
        }

        override fun shouldUpdateDynamicLight(): Boolean {
            return true
        }

        override fun lambdynlights_updateDynamicLight(renderer: LevelRenderer): Boolean {
            prevTransformedPos.set(transformedPos)
            ship.shipTransform.shipToWorldMatrix.transformPosition(transformedPos.set(pos))

            if (abs(prevTransformedPos.x - transformedPos.x) < 0.1 &&
                abs(prevTransformedPos.y - transformedPos.y) < 0.1 &&
                abs(prevTransformedPos.z - transformedPos.z) < 0.1
            ) {
                return false
            }

            val chunkPos = MutableBlockPos(
                transformedPos.x.toInt() shr 4,
                transformedPos.y.toInt() shr 4,
                transformedPos.z.toInt() shr 4
            )

            val newPos = LongOpenHashSet()

            LambDynLights.scheduleChunkRebuild(renderer, chunkPos)
            LambDynLights.updateTrackedChunks(chunkPos, this.trackedLitChunkPos, newPos)
            val directionX = if (transformedPos.x.toInt() and 15 >= 8) EAST else WEST
            val directionY = if (Mth.fastFloor(transformedPos.y) and 15 >= 8) UP else DOWN
            val directionZ = if (transformedPos.z.toInt() and 15 >= 8) SOUTH else NORTH

            for (i in 0..6) {
                if (i % 4 == 0) {
                    chunkPos.move(directionX)
                } else if (i % 4 == 1) {
                    chunkPos.move(directionZ)
                } else if (i % 4 == 2) {
                    chunkPos.move(directionX.opposite)
                } else {
                    chunkPos.move(directionZ.opposite)
                    chunkPos.move(directionY)
                }
                LambDynLights.scheduleChunkRebuild(renderer, chunkPos)
                LambDynLights.updateTrackedChunks(chunkPos, this.trackedLitChunkPos, newPos)
            }

            lambdynlights_scheduleTrackedChunksRebuild(renderer)
            trackedLitChunkPos = newPos

            return true
        }

        override fun lambdynlights_scheduleTrackedChunksRebuild(renderer: LevelRenderer) {
            val var2 = trackedLitChunkPos.iterator()
            while (var2.hasNext()) {
                val pos = var2.next() as Long
                LambDynLights.scheduleChunkRebuild(renderer, pos)
            }
        }
    }

    override fun onChunkLoad(chunkX: Int, chunkZ: Int, chunk: LevelChunk) {
        val ship = chunk.level.getShipManagingPos(chunkX, chunkZ) ?: return

        println("Loading chunk with lights: " + chunk.lights.collect(Collectors.toList()))

        chunk.lights.map { it.immutable() }.forEach { pos ->
            main.addLightSource(
                ShipLightSource(
                    pos = pos,
                    emission = chunk.getLightEmission(pos),
                    ship = ship,
                    level = chunk.level
                )
            )
        }
    }

    override fun onSetBlockState(level: Level, pos: BlockPos, prevBlockState: BlockState, newBlockState: BlockState) {
        if (prevBlockState.lightEmission > 0 && newBlockState.lightEmission == 0) {
            main.removeLightSources { it is ShipLightSource && it.pos == pos }
        }

        val ship = level.getShipManagingPos(pos)

        if (ship != null && prevBlockState.lightEmission == 0 && newBlockState.lightEmission > 0) {
            main.addLightSource(
                ShipLightSource(
                    pos = pos,
                    emission = newBlockState.lightEmission,
                    ship = ship,
                    level = level
                )
            )
        }
    }

    override fun onChunkUnload(chunkX: Int, chunkZ: Int) {
        main.removeLightSources { it is ShipLightSource && (it.pos.x shr 4) == chunkX && (it.pos.z shr 4) == chunkZ }
    }
}

val dynamicLightingListener = if (FabricLoader.getInstance().isModLoaded("lambdynlights"))
    DynamicLighting else NoDynamicLighting
