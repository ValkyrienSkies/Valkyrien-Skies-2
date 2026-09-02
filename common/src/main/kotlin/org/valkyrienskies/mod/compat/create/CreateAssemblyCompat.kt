package org.valkyrienskies.mod.compat.create

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import com.simibubi.create.content.kinetics.belt.BeltBlock
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object CreateAssemblyCompat {

    @JvmStatic
    fun fixMovedKineticBlockEntities(level: Level, positions: Collection<BlockPos>) {
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
        val visitedChainStarts = HashSet<BlockPos>()
        val chainsToBreak = ArrayList<List<BlockPos>>()
        for (pos in candidateBlocks) {
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

            if (!visitedChainStarts.add(start)) continue

            val chain = BeltBlock.getBeltChain(level, start)
            if (chain.size < 2) continue

            if (!candidateBlocks.containsAll(chain)) {
                chainsToBreak.add(chain)
            }
        }

        //break belt
        for (chain in chainsToBreak) {
            val first = chain[0]
            if (AllBlocks.BELT.has(level.getBlockState(first))) {
                level.destroyBlock(first, true)
            }
        }
    }
}
