package org.valkyrienskies.mod.common.pathfinding

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.pathfinder.BlockPathTypes
import org.valkyrienskies.mod.mixin.accessors.world.level.pathfinder.WalkNodeEvaluatorInvoker

/**
 * Merge resolution for overlapping ship lookups: when several ships' AABBs share a world cell,
 * the cell physically contains the union of their geometry, so the most-dangerous verdict wins
 * (a wall on any ship blocks the cell; lava on any ship hazards it). Driven by vanilla's
 * [BlockPathTypes.malus] so no VS2-invented ranking gets layered on top of vanilla pathfinding.
 */
object PathTypeDanger {

    @JvmStatic
    fun pickMostDangerousState(level: BlockGetter, candidates: List<BlockPos>): BlockState {
        require(candidates.isNotEmpty()) { "candidates must be non-empty" }
        var bestState: BlockState = level.getBlockState(candidates[0])
        var bestType: BlockPathTypes =
            WalkNodeEvaluatorInvoker.`vs$getBlockPathTypeRaw`(level, candidates[0])
        for (i in 1 until candidates.size) {
            val pos = candidates[i]
            val type = WalkNodeEvaluatorInvoker.`vs$getBlockPathTypeRaw`(level, pos)
            if (moreThreatening(type, bestType)) {
                bestState = level.getBlockState(pos)
                bestType = type
            }
        }
        return bestState
    }

    // -1 (absolute reject) trumps any non-negative malus; otherwise higher malus = more dangerous.
    private fun moreThreatening(a: BlockPathTypes, b: BlockPathTypes): Boolean {
        val am = a.malus
        val bm = b.malus
        if (am < 0f && bm >= 0f) return true
        if (bm < 0f && am >= 0f) return false
        return am > bm
    }
}
