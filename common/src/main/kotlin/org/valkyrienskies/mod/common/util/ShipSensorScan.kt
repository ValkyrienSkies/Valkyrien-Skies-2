package org.valkyrienskies.mod.common.util

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.BasePressurePlateBlock
import net.minecraft.world.level.block.ButtonBlock
import net.minecraft.world.level.block.LeverBlock
import net.minecraft.world.level.block.TargetBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.Ship
import java.util.function.Predicate
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Use a new scan each tick. Reuse empty section results until a sensor operates. */
internal class ShipSensorScan(private val level: ServerLevel) {
    private val chunks = Long2ObjectOpenHashMap<LevelChunk?>()
    private val emptySections = LongOpenHashSet()

    fun invalidate() {
        chunks.clear()
        emptySections.clear()
    }

    private fun chunk(cx: Int, cz: Int): LevelChunk? {
        val key = net.minecraft.world.level.ChunkPos.asLong(cx, cz)
        if (!chunks.containsKey(key)) chunks.put(key, level.chunkSource.getChunkNow(cx, cz))
        return chunks[key]
    }

    fun hasSensors(ship: Ship): Boolean {
        // Sensors can be outside the collision bounds. Check all active sections.
        val minSection = level.minBuildHeight shr 4
        val maxSection = (level.maxBuildHeight - 1) shr 4
        var found = false
        ship.activeChunksSet.forEach { cx, cz ->
            if (!found) {
                val chunk = chunk(cx, cz)
                if (chunk != null) {
                    for (sy in minSection..maxSection) {
                        if (mayHaveSensors(chunk, cx, sy, cz)) {
                            found = true
                            break
                        }
                    }
                }
            }
        }
        return found
    }

    fun scan(bounds: AABBdc, ship: Ship?, action: (BlockPos) -> Unit) {
        val minX = floor(bounds.minX() - 0.25).toInt()
        val maxX = floor(bounds.maxX() + 0.25).toInt()
        val minZ = floor(bounds.minZ() - 0.25).toInt()
        val maxZ = floor(bounds.maxZ() + 0.25).toInt()
        val minY = max(level.minBuildHeight, floor(bounds.minY() - 0.25).toInt())
        val maxY = min(level.maxBuildHeight - 1, floor(bounds.maxY() + 0.25).toInt())
        var sectionCount = 0
        for (cx in (minX shr 4)..(maxX shr 4)) for (cz in (minZ shr 4)..(maxZ shr 4)) {
            if (ship != null && !ship.activeChunksSet.contains(cx, cz)) continue
            val chunk = chunk(cx, cz) ?: continue
            for (sy in (minY shr 4)..(maxY shr 4)) {
                if (++sectionCount > 2048) return
                if (!mayHaveSensors(chunk, cx, sy, cz)) continue
                val section = chunk.getSection(chunk.getSectionIndex(sy shl 4))
                // Read cells in the original order. A sensor can change a later cell.
                for (x in max(minX, cx shl 4)..min(maxX, (cx shl 4) + 15))
                    for (z in max(minZ, cz shl 4)..min(maxZ, (cz shl 4) + 15))
                        for (y in max(minY, sy shl 4)..min(maxY, (sy shl 4) + 15)) {
                            if (IS_SENSOR.test(section.getBlockState(x and 15, y and 15, z and 15))) {
                                action(BlockPos(x, y, z))
                                invalidate()
                            }
                        }
            }
        }
    }

    private fun mayHaveSensors(chunk: LevelChunk, cx: Int, sy: Int, cz: Int): Boolean {
        val key = SectionPos.asLong(cx, sy, cz)
        if (emptySections.contains(key)) return false
        val section = chunk.getSection(chunk.getSectionIndex(sy shl 4))
        if (section.maybeHas(IS_SENSOR)) return true
        emptySections.add(key)
        return false
    }

    companion object {
        private val IS_SENSOR = Predicate<BlockState> { state ->
            state.block is BasePressurePlateBlock || state.block is ButtonBlock ||
                state.block is LeverBlock || state.block is TargetBlock
        }
    }
}
