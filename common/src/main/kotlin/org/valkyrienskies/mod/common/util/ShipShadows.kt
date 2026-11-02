package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LightLayer
import net.minecraft.world.phys.Vec3
import org.joml.Vector3d
import org.joml.primitives.AABBd
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import java.util.WeakHashMap

object ShipShadows {
    private data class Cache(var tick: Long, val values: MutableMap<BlockPos, Int> = HashMap())
    private val caches = WeakHashMap<ServerLevel, Cache>()

    @JvmStatic
    fun clear() = caches.clear()

    fun traceOpaque(getter: BlockGetter, start: Vec3, end: Vec3, loaded: (BlockPos) -> Boolean): Boolean {
        return BlockGetter.traverseBlocks(start, end, getter, { world, cell ->
            if (!loaded(cell)) null else {
                val state = world.getBlockState(cell)
                if (!state.isAir && (state.canOcclude() || state.getLightBlock(world, cell) >= 15) &&
                    state.getShape(world, cell).clip(start, end, cell) != null) true else null
            }
        }, { false }) ?: false
    }

    fun skyBrightness(level: ServerLevel, pos: BlockPos, original: Int): Int {
        if (!VSGameConfig.SERVER.ShipInteractions.shipShadows || original <= 0 ||
            !level.dimensionType().hasSkyLight()) return original
        val cache = caches.getOrPut(level) { Cache(level.gameTime) }
        if (cache.tick != level.gameTime || cache.values.size > 16384) {
            cache.values.clear()
            cache.tick = level.gameTime
        }
        return cache.values.getOrPut(pos.immutable()) { calculate(level, pos) }.coerceAtMost(original)
    }

    private fun calculate(level: ServerLevel, pos: BlockPos): Int {
        val owner = level.getShipManagingPos(pos)
        val world = Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        owner?.shipToWorld?.transformPosition(world)
        val worldPos = BlockPos.containing(world.x, world.y, world.z)
        var sky = level.lightEngine.getLayerListener(LightLayer.SKY).getLightValue(worldPos)
        val ships = level.shipObjectWorld.loadedShips
        var top = level.maxBuildHeight.toDouble()
        for (ship in ships) if (ship.chunkClaimDimension == level.dimensionId) top = maxOf(top, ship.worldAABB.maxY() + 1.0)
        if (world.y >= top || sky <= 0) return sky
        val column = AABBd(world.x - 0.001, world.y + 0.01, world.z - 0.001,
            world.x + 0.001, top, world.z + 0.001)
        for (ship in ships.getIntersecting(column, level.dimensionId)) {
            // The ship's own light engine already includes its roof.
            if (ship.id == owner?.id) continue
            val bounds = ship.worldAABB
            val bottom = maxOf(world.y + 0.01, bounds.minY() - 0.01)
            val upper = minOf(top, bounds.maxY() + 0.01)
            if (bottom >= upper) continue
            val start = ship.worldToShip.transformPosition(Vector3d(world.x, bottom, world.z)).toMinecraft()
            val end = ship.worldToShip.transformPosition(Vector3d(world.x, upper, world.z)).toMinecraft()
            val blocked = traceOpaque(level, start, end) { cell ->
                ship.activeChunksSet.contains(cell.x shr 4, cell.z shr 4) && level.hasChunkAt(cell)
            }
            if (blocked) sky = 0
            if (sky == 0) break
        }
        return sky
    }
}
