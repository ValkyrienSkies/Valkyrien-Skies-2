package org.valkyrienskies.mod.compat.create

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block

object CreateAssemblyCompat {

    @JvmStatic
    fun updateMovedKineticBlockEntities(level: Level, positions: Collection<BlockPos>) {
        for (pos in positions) {
            val blockEntity = level.getBlockEntity(pos)
            if (blockEntity is KineticBlockEntity) {
                blockEntity.warnOfMovement()
                blockEntity.clearKineticInformation()
                blockEntity.updateSpeed = true
                blockEntity.setChanged()
            }
        }
    }


    @JvmStatic
    fun clearCarriedBeltItemsBeforeRemoval(level: Level, pos: BlockPos) {
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity is BeltBlockEntity && blockEntity.isController()) {
            blockEntity.getInventory()?.getTransportedItems()?.clear()
        }
    }

    /** @see clearCarriedBeltItemsBeforeRemoval */
    @JvmStatic
    fun clearCarriedBeltItemsBeforeRemoval(level: Level, positions: Collection<BlockPos>) {
        for (pos in positions) {
            clearCarriedBeltItemsBeforeRemoval(level, pos)
        }
    }

    @JvmStatic
    fun breakSplitBeltChains(level: Level, candidateBlocks: Set<BlockPos>) {
        val visitedPositions = HashSet<BlockPos>()

        for (pos in candidateBlocks) {
            if (pos in visitedPositions) continue

            val state = level.getBlockState(pos)
            if (!AllBlocks.BELT.has(state)) continue

            var start = pos
            var startState = state

            while (true) {
                val prev = BeltBlock.nextSegmentPosition(startState, start, false) ?: break
                if (!level.isLoaded(prev)) break
                val prevState = level.getBlockState(prev)
                if (!AllBlocks.BELT.has(prevState)) break
                start = prev
                startState = prevState
            }

            val chain = BeltBlock.getBeltChain(level, start)
            visitedPositions.addAll(chain)

            if (chain.size < 2) continue
            if (candidateBlocks.containsAll(chain)) continue

            val disconnectPoint = chain.firstOrNull { it !in candidateBlocks }
                ?: chain.lastOrNull { it !in candidateBlocks }

            if (disconnectPoint != null && AllBlocks.BELT.has(level.getBlockState(disconnectPoint))) {
                level.destroyBlock(disconnectPoint, true)
            }
        }
    }
}
