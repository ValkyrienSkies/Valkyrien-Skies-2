package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.joml.Matrix4dc
import org.joml.Vector3d
import org.joml.Vector3dc
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ShipFragileBlocks {
    data class Impact(val point: Vector3dc, val inward: Vector3dc, val toOther: Matrix4dc, val force: Double)
    class Budget(var remaining: Int = 32768)
    private data class Hit(val pos: BlockPos, val state: BlockState, val box: AABB, val point: Vector3d)
    private data class Patch(val cells: Set<BlockPos>, val supported: Boolean, val complete: Boolean)
    private data class Face(val pos: BlockPos, val x: Int, val y: Int, val z: Int)

    /** Find contact blocks before any block is removed. */
    fun blocksToBreak(level: BlockGetter, impacts: List<Impact>, threshold: Double,
        isFragile: (BlockState) -> Boolean, ownBlock: (BlockPos) -> Boolean,
        otherBlock: (BlockPos) -> Boolean, protectWithFrame: Boolean,
        budget: Budget = Budget()): Set<BlockPos> {
        val broken = HashSet<BlockPos>()
        val patches = HashMap<Face, Patch>()
        for (impact in impacts) {
            if (!impact.force.isFinite() || impact.force <= 0.0 || !impact.point.isFinite
                || !impact.inward.isFinite || impact.inward.lengthSquared() < 1.0e-12) continue
            val inward = Vector3d(impact.inward).normalize()
            val hit = firstHit(level, impact.point, Vector3d(inward).mul(0.25).add(impact.point), ownBlock,
                budget) ?: continue
            if (!isFragile(hit.state) || hit.state.getDestroySpeed(level, hit.pos) < 0.0f) continue
            fun face(pos: BlockPos) = Face(pos, (inward.x * 10000).roundToInt(),
                (inward.y * 10000).roundToInt(), (inward.z * 10000).roundToInt())
            val patch = patches[face(hit.pos)] ?: contactPatch(level, hit, inward, impact.toOther,
                isFragile, ownBlock, otherBlock, protectWithFrame, budget).also { patch ->
                for (pos in patch.cells) patches[face(pos)] = patch
            }
            if (patch.complete && !patch.supported && patch.cells.isNotEmpty()
                && impact.force / patch.cells.size >= max(0.0, threshold)) broken.add(hit.pos)
        }
        return broken
    }

    private fun contactPatch(level: BlockGetter, first: Hit, inward: Vector3d, toOther: Matrix4dc,
        isFragile: (BlockState) -> Boolean, ownBlock: (BlockPos) -> Boolean,
        otherBlock: (BlockPos) -> Boolean, protectWithFrame: Boolean, budget: Budget): Patch {
        val plane = projection(supportPoint(first.box, inward), inward)
        val cells = HashSet<BlockPos>()
        val visited = HashSet<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(first.pos)
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!visited.add(pos) || !ownBlock(pos)) continue
            if (budget.remaining-- <= 0 || cells.size >= 4096) return Patch(cells, false, false)
            val state = level.getBlockState(pos)
            var touching = false
            for (localBox in state.getCollisionShape(level, pos).toAabbs()) {
                val box = localBox.move(pos)
                val center = supportPoint(box, inward)
                if (abs(projection(center, inward) - plane) > 0.03) continue
                // Check the contact position as well as the center of the face.
                for (point in listOf(supportPoint(box, inward, first.point), center)) {
                    val from = toOther.transformPosition(Vector3d(inward).mul(0.02).add(point))
                    val to = toOther.transformPosition(Vector3d(inward).mul(-0.08).add(point))
                    if (firstHit(level, from, to, otherBlock, budget) != null) {
                        touching = true
                        break
                    }
                }
                if (touching) break
            }
            if (budget.remaining <= 0) return Patch(cells, false, false)
            if (!touching) continue
            cells.add(pos)
            if (!isFragile(state)) {
                if (protectWithFrame) return Patch(cells, true, true)
                continue
            }
            for (direction in Direction.values()) queue.add(pos.relative(direction))
        }
        return Patch(cells, false, true)
    }

    private fun supportPoint(box: AABB, inward: Vector3dc, anchor: Vector3dc? = null): Vector3d {
        fun coordinate(low: Double, high: Double, normal: Double, contact: Double?) = when {
            normal > 1.0e-6 -> low
            normal < -1.0e-6 -> high
            contact != null -> contact.coerceIn(low + 1.0e-5, high - 1.0e-5)
            else -> (low + high) * 0.5
        }
        return Vector3d(coordinate(box.minX, box.maxX, inward.x(), anchor?.x()),
            coordinate(box.minY, box.maxY, inward.y(), anchor?.y()),
            coordinate(box.minZ, box.maxZ, inward.z(), anchor?.z()))
    }

    private fun projection(point: Vector3dc, normal: Vector3dc) = point.dot(normal)

    private fun firstHit(level: BlockGetter, from: Vector3dc, to: Vector3dc,
        accepted: (BlockPos) -> Boolean, budget: Budget): Hit? {
        if (!from.isFinite || !to.isFinite) return null
        val low = intArrayOf(floor(min(from.x(), to.x())).toInt(), floor(min(from.y(), to.y())).toInt(),
            floor(min(from.z(), to.z())).toInt())
        val high = intArrayOf(floor(max(from.x(), to.x())).toInt(), floor(max(from.y(), to.y())).toInt(),
            floor(max(from.z(), to.z())).toInt())
        if ((high[0].toLong() - low[0] + 1) * (high[1].toLong() - low[1] + 1)
            * (high[2].toLong() - low[2] + 1) > 512) return null
        val travel = Vector3d(to).sub(from)
        var nearest = Double.POSITIVE_INFINITY
        var hit: Hit? = null
        for (x in low[0]..high[0]) for (y in low[1]..high[1]) for (z in low[2]..high[2]) {
            val pos = BlockPos(x, y, z)
            if (!accepted(pos)) continue
            if (budget.remaining-- <= 0) return null
            val state = level.getBlockState(pos)
            for (localBox in state.getCollisionShape(level, pos).toAabbs()) {
                val box = localBox.move(pos)
                val distance = entry(box, from, travel) ?: continue
                if (distance < nearest) {
                    nearest = distance
                    hit = Hit(pos, state, box, Vector3d(travel).mul(distance).add(from))
                }
            }
        }
        return hit
    }

    private fun entry(box: AABB, from: Vector3dc, travel: Vector3dc): Double? {
        var enter = 0.0
        var leave = 1.0
        for (axis in 0..2) {
            val low = when (axis) { 0 -> box.minX; 1 -> box.minY; else -> box.minZ }
            val high = when (axis) { 0 -> box.maxX; 1 -> box.maxY; else -> box.maxZ }
            val origin = from[axis]
            val speed = travel[axis]
            if (abs(speed) < 1.0e-12) {
                if (origin < low || origin > high) return null
            } else {
                val first = (low - origin) / speed
                val last = (high - origin) / speed
                enter = max(enter, min(first, last))
                leave = min(leave, max(first, last))
                if (enter > leave) return null
            }
        }
        return enter
    }
}
