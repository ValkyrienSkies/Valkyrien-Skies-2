package org.valkyrienskies.mod.common.util

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.util.logger
import java.util.function.Consumer

class SplitHandler(private val doEdges: Boolean, private val doCorners: Boolean) {

    private val splitQueue: HashMap<DimensionId, HashMap<ShipId, Int>> = hashMapOf()

    fun queueSplit(level: Level, shipId: ShipId?) {
        if (shipId == null) return
SPLITLOGGER.logger.debug("[split-debug] queueSplit requested: ship=$shipId dim=${level.dimensionId}")
        splitQueue[level.dimensionId]?.put(shipId, VSGameConfig.SERVER.defaultSplitGraceTimer) ?: run {
            splitQueue[level.dimensionId] = hashMapOf(shipId to VSGameConfig.SERVER.defaultSplitGraceTimer)
        }
    }

    fun tick(level: ServerLevel) {
        if (splitQueue[level.dimensionId] != null && splitQueue[level.dimensionId]!!.isNotEmpty()) {
            val splitsToProcess = HashSet<ShipId>()
            for (splitIndex in splitQueue[level.dimensionId]!!.keys) {
                if (splitQueue[level.dimensionId]!![splitIndex]!! <= 0) {
                    splitsToProcess.add(splitIndex)
                } else {
                    splitQueue[level.dimensionId]!![splitIndex] = splitQueue[level.dimensionId]!![splitIndex]!! - 1
                }
            }
            splitsToProcess.forEach {
                SPLITLOGGER.logger.info("[split-debug] grace timer expired, running split() for ship=$it")
                splitQueue[level.dimensionId]!!.remove(it)
                split(level, it)
            }
        }
    }

    fun split(level: Level, shipId: ShipId, after: Consumer<ServerShip>? = null) {
        if (level !is ServerLevel) return
        val loadedShip: LoadedServerShip = level.shipObjectWorld.loadedShips.getById(shipId) ?: return
        if (loadedShip.getAttachment<SplittingDisablerAttachment>()?.canSplit() == false) return

        val allBlocks = HashSet<BlockPos>()
        loadedShip.activeChunksSet.forEach { cx, cz ->
            val chunk = level.chunkSource.getChunkNow(cx, cz) ?: return@forEach
            val sections = chunk.sections
            for (sIdx in sections.indices) {
                val section = sections[sIdx] ?: continue
                if (section.hasOnlyAir()) continue
                val baseY = chunk.getSectionYFromSectionIndex(sIdx) shl 4
                for (lx in 0..15) for (ly in 0..15) for (lz in 0..15) {
                    if (!section.getBlockState(lx, ly, lz).isAir) {
                        allBlocks.add(BlockPos((cx shl 4) + lx, baseY + ly, (cz shl 4) + lz))
                    }
                }
            }
        }
        if (allBlocks.size <= 1) return

        val components = connectedComponents(allBlocks, getOffsets(doEdges, doCorners))
        SPLITLOGGER.logger.info("[split-debug] BFS: ${components.size} component(s) from ${allBlocks.size} blocks")
        if (components.size <= 1) return // still one connected piece -> no split

        components.sortByDescending { it.size }
        loadedShip.getAttachment(SplittingDisablerAttachment::class.java)?.disableSplitting()
        try {
            for (i in 1 until components.size) {
                val comp = components[i]
                if (comp.isEmpty()) continue
                val newShip = ShipAssembler.assembleToShip(level, comp, 1.0)
                SPLITLOGGER.logger.info("[split-debug] split off new ship ${newShip.id} (${comp.size} blocks)")
                after?.accept(newShip)
            }
        } finally {
            loadedShip.getAttachment(SplittingDisablerAttachment::class.java)?.enableSplitting()
        }
    }

    private fun connectedComponents(blocks: Set<BlockPos>, offsets: List<Vec3i>): MutableList<MutableSet<BlockPos>> {
        val remaining = HashSet(blocks)
        val components = ArrayList<MutableSet<BlockPos>>()
        while (remaining.isNotEmpty()) {
            val start = remaining.iterator().next()
            remaining.remove(start)
            val comp = HashSet<BlockPos>().apply { add(start) }
            val stack = ArrayDeque<BlockPos>().apply { addLast(start) }
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                for (off in offsets) {
                    val nb = cur.offset(off.x, off.y, off.z)
                    if (remaining.remove(nb)) {
                        comp.add(nb)
                        stack.addLast(nb)
                    }
                }
            }
            components.add(comp)
        }
        return components
    }

    companion object {

        val SPLITLOGGER = logger("(Valkyrien Skies) kitkat factory")

        val offsetsToCheck: ArrayList<Vec3i> = arrayListOf(
            Vec3i(1, 0, 0),
            Vec3i(-1, 0, 0),
            Vec3i(0, 1, 0),
            Vec3i(0, -1, 0),
            Vec3i(0, 0, 1),
            Vec3i(0, 0, -1)
        )

        fun getOffsets(doEdges: Boolean, doCorners: Boolean): ArrayList<Vec3i> {
            val list = ArrayList<Vec3i>(offsetsToCheck)
            if (doEdges) { //later: check block edge connectivity config
                list.add(Vec3i(1, 1, 0))
                list.add(Vec3i(1, -1, 0))
                list.add(Vec3i(-1, 1, 0))
                list.add(Vec3i(-1, -1, 0))
                list.add(Vec3i(1, 0, 1))
                list.add(Vec3i(1, 0, -1))
                list.add(Vec3i(-1, 0, 1))
                list.add(Vec3i(-1, 0, -1))
                list.add(Vec3i(0, 1, 1))
                list.add(Vec3i(0, 1, -1))
                list.add(Vec3i(0, -1, 1))
                list.add(Vec3i(0, -1, -1))
            }
            if (doCorners) { //later: check block corner connectivity config
                list.add(Vec3i(1, 1, 1))
                list.add(Vec3i(1, 1, -1))
                list.add(Vec3i(1, -1, 1))
                list.add(Vec3i(1, -1, -1))
                list.add(Vec3i(-1, 1, 1))
                list.add(Vec3i(-1, 1, -1))
                list.add(Vec3i(-1, -1, 1))
                list.add(Vec3i(-1, -1, -1))
            }
            return list
        }
    }
}
