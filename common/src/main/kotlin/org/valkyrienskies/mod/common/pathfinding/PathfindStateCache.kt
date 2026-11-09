package org.valkyrienskies.mod.common.pathfinding

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FluidState

/**
 * Per-pathfind cache of peek results, keyed by cell position alone. Reset at the start of
 * each `findPath`; safe because a single pathfind runs in exactly one frame.
 */
object PathfindStateCache {

    private val blockTL: ThreadLocal<Long2ObjectOpenHashMap<BlockState>> =
        ThreadLocal.withInitial { Long2ObjectOpenHashMap(256) }
    private val fluidTL: ThreadLocal<Long2ObjectOpenHashMap<FluidState>> =
        ThreadLocal.withInitial { Long2ObjectOpenHashMap(64) }

    @JvmStatic
    fun reset() {
        blockTL.get().clear()
        fluidTL.get().clear()
    }

    @JvmStatic
    fun getBlock(key: Long): BlockState? = blockTL.get().get(key)

    @JvmStatic
    fun putBlock(key: Long, state: BlockState) {
        blockTL.get().put(key, state)
    }

    @JvmStatic
    fun getFluid(key: Long): FluidState? = fluidTL.get().get(key)

    @JvmStatic
    fun putFluid(key: Long, state: FluidState) {
        fluidTL.get().put(key, state)
    }
}
