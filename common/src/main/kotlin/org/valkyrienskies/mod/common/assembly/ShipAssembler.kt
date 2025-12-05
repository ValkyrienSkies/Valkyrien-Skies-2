package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.joml.Quaterniond
import org.joml.RoundingMode
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet

object ShipAssembler {
    class SingleItemMap<K, V>(val mkey: K, val mvalue: V, val default: V, val defaultFn: ((K) -> V)? = null): Map<K, V> {
        override val size: Int = 1
        override val keys: Set<K> = setOf(mkey)

        override val values: Collection<V> = setOf(mvalue)
        override val entries: Set<Map.Entry<K, V>> = setOf(object : Map.Entry<K, V> {
            override val key = mkey
            override val value = mvalue
        })

        override fun isEmpty(): Boolean = false
        override fun containsKey(key: K): Boolean = true
        override fun containsValue(value: V): Boolean = true
        override fun get(key: K): V? = if (key == this.mkey) mvalue else defaultFn?.invoke(key) ?: default
    }

    @JvmStatic
    fun findMinAndMax(blocks: Iterable<BlockPos>): Pair<BlockPos, BlockPos> {
        var minCorner = BlockPos.MutableBlockPos(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        var maxCorner = BlockPos.MutableBlockPos(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

        for (pos in blocks) {
            minCorner.x = Math.min(minCorner.x, pos.x)
            minCorner.y = Math.min(minCorner.y, pos.y)
            minCorner.z = Math.min(minCorner.z, pos.z)

            maxCorner.x = Math.max(maxCorner.x, pos.x)
            maxCorner.y = Math.max(maxCorner.y, pos.y)
            maxCorner.z = Math.max(maxCorner.z, pos.z)
        }

        return minCorner to maxCorner
    }

    @JvmStatic
    @OptIn(GameTickOnly::class)
    fun assembleToShip(level: ServerLevel, blocks: Set<BlockPos>, scale: Double): ServerShip {
        if (blocks.isEmpty()) throw RuntimeException("Empty set of blocks")

        // val eventData = mutableMapOf<String, CompoundTag>()
        // VSAssemblyEvents.onCopy.emit(VSAssemblyEvents.OnCopy(level, TODO(), TODO(), eventData))

        val (minB, maxB) = findMinAndMax(blocks)
        val oldMin = minB.toJOMLD()
        val oldMax = maxB.toJOMLD()
        //offset to center from corner of structure
        val offset = oldMax.get(Vector3d())
            .sub(oldMin)
            .add(1.0, 1.0, 1.0)
            .div(2.0)
        val oldCenter = offset.get(Vector3d()).add(oldMin)

        val oldShip = level.getShipManagingPos(oldCenter)
        val oldId = oldShip?.id ?: -1L
        val oldScale = oldShip?.transform?.scaling?.x() ?: 1.0

        val worldOldCenter = oldShip?.shipToWorld?.transformPosition(oldCenter.get(Vector3d())) ?: oldCenter.get(Vector3d())

        val template = StructureTemplate()
        template as StructureTemplateFillFromVoxelSet
        template.`vs$fillFromVoxelSet`(
            level, blocks,
            oldShip?.let { listOf(it) } ?: emptyList(),
            SingleItemMap(oldId, oldCenter, Vector3d()),
            minB, maxB
        )

        val air = Blocks.AIR.defaultBlockState()
        for (pos in blocks) {level.removeBlock(pos, true)}

        val ship = level.shipObjectWorld.createNewShipAtBlock(Vector3i(worldOldCenter, RoundingMode.FLOOR), false, scale * oldScale, level.dimensionId)
        ship.isStatic = true
        val centerOfPlot = ship.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i())

        //structure template builds from a corner, so offset center of plot so that structure's center and center of
        //plot roughly align
        val cornerOfShip = Vector3d(centerOfPlot)
            .sub(offset)
            .ceil()
            .let { BlockPos(
                it.x.toInt(),
                it.y.toInt(),
                it.z.toInt(),
            ) }

        val centerOfShip = cornerOfShip.toJOMLD().add(offset)

        val structureSettings = StructurePlaceSettings().addProcessor(
            ICopyableProcessor(
                SingleItemMap(oldId, ship.id, -1) {it},
                SingleItemMap(oldId, Pair(oldCenter, Vector3d(centerOfShip)), Pair(Vector3d(), Vector3d()))
            )
        )

        structureSettings.rotationPivot = cornerOfShip

        // VSAssemblyEvents.onPasteBeforeBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteBeforeBlocksAreCopied(level, TODO(), Pair(TODO(), ship), TODO(), eventData))
        //TODO what is 2?
        template.placeInWorld(level, cornerOfShip, cornerOfShip, structureSettings, level.random, 2)
        // VSAssemblyEvents.onPasteAfterBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteAfterBlocksAreLoaded(level, TODO(), TODO(), eventData))

        val shipPos = oldCenter.get(Vector3d()).add(0.5, 0.5, 0.5, Vector3d())

        //teleport fn uses COM as center of ship, so it calculates such offset that centerOfShip will be "center" instead
        val posOffset =
            Vector3d(ship.inertiaData.centerOfMass)
            .sub(Vector3d(centerOfShip))
            .let { oldShip?.shipToWorld?.transformDirection(it) ?: it }

        level.shipObjectWorld.teleportShip(ship, vsCore.newShipTeleportData(
            (oldShip?.shipToWorld?.transformPosition(shipPos) ?: shipPos).add(posOffset),
            oldShip?.transform?.shipToWorldRotation ?: Quaterniond(),
            oldShip?.velocity ?: Vector3d(),
            oldShip?.angularVelocity ?: Vector3d(),
        ))

        ship.isStatic = false

        return ship
    }

    class ICopyableProcessor(
        val oldShipIdToNewShipId: Map<ShipId, ShipId>,
        val centerPositions: Map<Long, Pair<Vector3d, Vector3d>>
    ): StructureProcessor() {
        override fun processBlock(
            levelReader: LevelReader, oldBPos: BlockPos, newBPos: BlockPos,
            oldStructureBlockInfo: StructureTemplate.StructureBlockInfo,
            newStructureBlockInfo: StructureTemplate.StructureBlockInfo, structurePlaceSettings: StructurePlaceSettings
        ): StructureTemplate.StructureBlockInfo? {
            val block = newStructureBlockInfo.state.block
            if (block !is ICopyableBlock) return newStructureBlockInfo
            block.onPaste((levelReader as ServerLevelAccessor).level, newBPos, newStructureBlockInfo.state, oldShipIdToNewShipId, centerPositions, newStructureBlockInfo.nbt)
            return newStructureBlockInfo
        }

        //TODO i don't think it does anything, but i'm not sure
        override fun getType(): StructureProcessorType<*>? = null
    }
}
