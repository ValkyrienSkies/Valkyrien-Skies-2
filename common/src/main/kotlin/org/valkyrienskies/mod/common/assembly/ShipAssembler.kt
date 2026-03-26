package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Clearable
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ServerLevelAccessor
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import org.joml.Quaterniond
import org.joml.RoundingMode
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.impl.config.VSCoreConfig
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.mod.common.config.VSGameConfig
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.forEach
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.inAssemblyBlacklist
import org.valkyrienskies.mod.common.isChunkLoadedForVS
import org.valkyrienskies.mod.common.networking.PacketRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.PacketStopChunkUpdates
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toDenseVoxelUpdate
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.common.yRange
import org.valkyrienskies.mod.util.AIR
import org.valkyrienskies.mod.util.StructureTemplateFillFromVoxelSet
import org.valkyrienskies.mod.util.logger

object ShipAssembler {

    val ASSEMBLY_LOGGER = logger("(Valkyrien Skies) Sandwich Factory").logger

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
        val minCorner = BlockPos.MutableBlockPos(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        val maxCorner = BlockPos.MutableBlockPos(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

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
    private fun getDistinctChunksFromBlockPosSet(blocks: Set<BlockPos>): Set<ChunkPos> {
        val chunkSet = hashSetOf<ChunkPos>()
        for (blockPos in blocks) {
            val chunkPos = ChunkPos(blockPos)
            chunkSet.add(chunkPos)
        }
        return chunkSet
    }

    data class AssembleContext(val ship: ServerShip, val fromCenter: Vector3d, val toCenter: Vector3d)

    @JvmStatic
    @OptIn(GameTickOnly::class)
    fun assembleToShipFull(level: ServerLevel, blocks: Set<BlockPos>, scale: Double = 1.0): AssembleContext {
        if (blocks.isEmpty()) {
            val error = RuntimeException("Assembly function received an empty set of blocks")
            ASSEMBLY_LOGGER.error(error)
            throw error
        }

        val (minB, maxB) = findMinAndMax(blocks)
        val oldMin = minB.toJOMLD()
        val oldMax = maxB.toJOMLD()
        //offset to center from corner of structure
        val offset = oldMax.get(Vector3d())
            .sub(oldMin)
            .add(1.0, 1.0, 1.0)
            .div(2.0)
        val fromCenter = offset.get(Vector3d()).add(oldMin)

        val fromShip = level.getLoadedShipManagingPos(fromCenter) ?: level.getShipManagingPos(fromCenter)
        val oldScale = fromShip?.transform?.scaling?.x() ?: 1.0
        val worldOldCenter = fromShip?.shipToWorld?.transformPosition(fromCenter.get(Vector3d())) ?: fromCenter.get(Vector3d())

        val toShip = level.shipObjectWorld.createNewShipAtBlock(Vector3i(worldOldCenter, RoundingMode.FLOOR), false, scale * oldScale, level.dimensionId)
        toShip.isStatic = fromShip == null || fromShip.isStatic

        val (wasSuccessful, _, toCenter) = moveBlocksFromTo(level, blocks, fromShip, toShip, minB, maxB, toShip.chunkClaim.getCenterBlockCoordinates(level.yRange, Vector3i()))

        if (!wasSuccessful) {
            level.shipObjectWorld.deleteShip(toShip)
            val error = AssertionError("Couldn't move blocks")
            ASSEMBLY_LOGGER.error(error)
            throw error
        }

        //teleport fn uses COM as center of ship, so it calculates such offset that centerOfShip will be "center" instead
        val posOffset =
            Vector3d(toShip.inertiaData.centerOfMass)
                .sub(Vector3d(toCenter))
                .let { fromShip?.shipToWorld?.transformDirection(it) ?: it }

        (toShip as VsiServerShip).unsafeSetKinematics(vsCore.newBodyKinematics(
            fromShip?.velocity ?: Vector3d(),
            fromShip?.angularVelocity ?: Vector3d(),
            vsCore.newBodyTransform(
                (fromShip?.shipToWorld?.transformPosition(Vector3d(fromCenter)) ?: fromCenter).add(posOffset),
                fromShip?.transform?.shipToWorldRotation ?: Quaterniond(),
                Vector3d(scale * oldScale, scale * oldScale, scale * oldScale),
                toCenter
            )
        ))
        toShip.isStatic = false
        return AssembleContext(toShip, fromCenter, toCenter)
    }

    data class MoveContext(val wasSuccessful: Boolean, val fromCenter: Vector3d, val toCenter: Vector3d)
    private val failedMove = MoveContext(false, Vector3d(), Vector3d())

    @JvmStatic
    @OptIn(GameTickOnly::class)
    fun moveBlocksFromTo(
        level: ServerLevel,
        blocks: Set<BlockPos>,
        fromShip: ServerShip?, toShip: ServerShip?,
        minStructurePos: BlockPos, maxStructurePos: BlockPos,
        toCenter: Vector3i,
        removeOriginal: Boolean = true)
    : MoveContext {
        val blocks = blocks.filter { level.getBlockState(it).let{!it.isAir && !it.inAssemblyBlacklist()} }.toSet()
        if (blocks.isEmpty()) return failedMove

        val fromId = fromShip?.id ?: -1L
        val eventData = mutableMapOf<String, CompoundTag>()

        val oldMin = minStructurePos.toJOMLD()
        val oldMax = maxStructurePos.toJOMLD()
        //offset to center from corner of structure
        val offset = oldMax.get(Vector3d())
            .sub(oldMin)
            .add(1.0, 1.0, 1.0)
            .div(2.0)
        val fromCenter = offset.get(Vector3d()).add(oldMin)

        var wasSplittingEnabled = true
        if (fromShip is LoadedServerShip) {
            val splittingDisabler = fromShip.getAttachment(SplittingDisablerAttachment::class.java)
            wasSplittingEnabled = splittingDisabler?.canSplit() != false
            splittingDisabler?.disableSplitting()
        }

        // ========== Copy Blocks
        VSAssemblyEvents.beforeCopy.emit(VSAssemblyEvents.BeforeCopy(level, oldMin, oldMax, fromCenter, fromShip, blocks, eventData))

        val template = StructureTemplate()
        template as StructureTemplateFillFromVoxelSet
        template.`vs$fillFromVoxelSet`(
            level, blocks,
            fromShip?.let { listOf(it) } ?: emptyList(),
            SingleItemMap(fromId, fromCenter, Vector3d()),
            minStructurePos, maxStructurePos
        )

        // ========== Pause Chunk Updates

        val toChunkCenter = ChunkPos(toCenter.x.toInt() shr 4, toCenter.z.toInt() shr 4)

        val fromChunkX = ((minStructurePos.x + maxStructurePos.x) / 2) shr 4
        val fromChunkZ = ((minStructurePos.z + maxStructurePos.z) / 2) shr 4

        val deltaX = fromChunkX - toChunkCenter.x
        val deltaZ = fromChunkZ - toChunkCenter.z

        val chunksToBeUpdated = mutableMapOf<ChunkPos, Pair<ChunkPos, ChunkPos>>()
        getDistinctChunksFromBlockPosSet(blocks).forEach { sourcePos ->
            val destPos = ChunkPos(sourcePos.x - deltaX, sourcePos.z - deltaZ)
            chunksToBeUpdated[sourcePos] = Pair(sourcePos, destPos)
        }
        val chunkPairs = chunksToBeUpdated.values.toList()
        val chunkPoses = chunkPairs.flatMap { it.toList() }
        val chunkPosesJOML = chunkPoses.map { it.toJOML() }

        level.players().forEach { player ->
            ASSEMBLY_LOGGER.debug("Pausing chunk updates for ${player.name}")
            with(vsCore.simplePacketNetworking) {
                PacketStopChunkUpdates(chunkPosesJOML).sendToClient(player.playerWrapper)
            }
        }

        // ========== Removing Old Blocks
        if (removeOriginal) {
            for (pos in blocks) {
                level.getBlockEntity(pos)?.let {
                    if (it is Clearable) {
                        Clearable.tryClear(it)
                    } else {
                        // Clear all NBT if it doesn't implement IClearable
                        it.load(CompoundTag())
                    }
                    // Without this, copycats still drop their items
                    level.removeBlockEntity(pos)
                }

                level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS)
            }
            for (pos in blocks) {
                val block = level.getBlockState(pos)
                level.removeBlock(pos, false)
                // 75 = flag 1 (block update) & flag 2 (send to clients) + flag 8 (force rerenders)
                val flags = 11 or Block.UPDATE_MOVE_BY_PISTON or Block.UPDATE_SUPPRESS_DROPS

                //updateNeighbourShapes recurses through nearby blocks, recursionLeft is the limit
                val recursionLeft = 511

                level.setBlocksDirty(pos, block, AIR)
                level.sendBlockUpdated(pos, block, AIR, flags)
                level.blockUpdated(pos, AIR.block)
                // This handles the update for neighboring blocks in worldspace
                AIR.updateIndirectNeighbourShapes(level, pos, flags, recursionLeft - 1)
                AIR.updateNeighbourShapes(level, pos, flags, recursionLeft)
                AIR.updateIndirectNeighbourShapes(level, pos, flags, recursionLeft)
                //This updates lighting for blocks in worldspace
                level.chunkSource.lightEngine.checkBlock(pos)
            }
        }
        // ========== Placing New Blocks

        //structure template builds from a corner, so offset center of plot so that structure's center and center of
        //plot roughly align
        val cornerOfShip = Vector3d(toCenter)
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
                SingleItemMap(fromId, toShip?.id ?: -1L, -1L) {it},
                SingleItemMap(fromId, Pair(fromCenter, Vector3d(centerOfShip)), Pair(Vector3d(), Vector3d()))
            )
        )

        structureSettings.rotationPivot = cornerOfShip

        VSAssemblyEvents.onPasteBeforeBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteBeforeBlocksAreLoaded(level, fromShip, toShip, Pair(fromCenter, centerOfShip), eventData))
        template.placeInWorld(level, cornerOfShip, cornerOfShip, structureSettings, level.random, Block.UPDATE_CLIENTS)

        // ========== Resume Chunk Updates
        val timeAtExecution = level.server.tickCount
        level.server.executeIf(
            // This condition will return true if all modified chunks have been both loaded AND
            // chunk update packets were sent to players
            { chunkPoses.all(level::isChunkLoadedForVS) || level.server.tickCount - timeAtExecution > 60 }
        ) {
            if (level.server.tickCount - timeAtExecution > 60) {
                ASSEMBLY_LOGGER.warn("Timed out waiting for chunks to start ticking after assembly! Forcibly resuming...")
                ASSEMBLY_LOGGER.warn("All chunks involved in assembly: $chunkPoses")
                ASSEMBLY_LOGGER.warn("Chunks that were not loaded: ${chunkPoses.filterNot { level.isChunkLoadedForVS(it) }}")
            }
            // Once all the chunk updates are sent to players, we can tell them to restart chunk updates
            level.players().forEach { player ->
                ASSEMBLY_LOGGER.debug("Resuming chunk updates for ${player.name}")
                with (vsCore.simplePacketNetworking) {
                    PacketRestartChunkUpdates(chunkPosesJOML).sendToClient(player.playerWrapper)
                }
            }
            VSAssemblyEvents.onPasteAfterBlocksAreLoaded.emit(VSAssemblyEvents.OnPasteAfterBlocksAreLoaded(level, fromShip, toShip, Pair(fromCenter, centerOfShip), eventData))
            //force update connectivity because this new assemblyslop doesn't update it :(
            if (VSCoreConfig.SERVER.sp.enableConnectivity) {
                for (pos in chunkPoses) {
                    val worldChunk = level.getChunk(pos.x, pos.z) ?: continue
                    val chunkSections = worldChunk.sections ?: continue
                    for (sectionY in 0 until worldChunk.sectionsCount) {
                        val sectionPos = Vector3i(pos.x, worldChunk.getSectionYFromSectionIndex(sectionY), pos.z)
                        val section = chunkSections[sectionY] ?: continue
                        if (section.hasOnlyAir()) continue
                        val update = section.toDenseVoxelUpdate(sectionPos)
                        level.shipObjectWorld.forceUpdateConnectivityChunk(
                            level.dimensionId,
                            sectionPos.x,
                            sectionPos.y,
                            sectionPos.z,
                            update
                        )
                    }
                }
            }

            if (fromShip is LoadedServerShip) {
                val splittingDisabler = fromShip.getAttachment(SplittingDisablerAttachment::class.java)
                if (wasSplittingEnabled) {
                    splittingDisabler?.enableSplitting()
                }
            }
        }

        return MoveContext(true, fromCenter, centerOfShip)
    }

    @JvmStatic
    @OptIn(GameTickOnly::class)
    fun assembleToShip(level: ServerLevel, blocks: Set<BlockPos>, scale: Double = 1.0): ServerShip {
        return assembleToShipFull(level, blocks, scale).ship
    }
    //legacy method to not break shit
    @Deprecated("Old")
    fun assembleToShip(level: Level, blocks: List<BlockPos>, removeOriginal: Boolean, scale: Double = 1.0, shouldDisableSplitting: Boolean = false): ServerShip {
        return assembleToShip(level as ServerLevel, blocks.toSet(), scale)
    }

    @Suppress("unused")
    fun isValidShipBlock(state: BlockState?) : Boolean {
        if (state == null) return false
        if (state.isAir) return false
        val block: Block = state.block
        //assembly blacklist check
        return !state.inAssemblyBlacklist()
    }

    fun deleteShip(level: ServerLevel, ship: ServerShip, deleteBlocks: Boolean, dropBlocks: Boolean): Int {
        if (ship is LoadedServerShip) {
            val splittingDisabler = ship.getAttachment(SplittingDisablerAttachment::class.java)
            splittingDisabler?.disableSplitting()
        }
        if (deleteBlocks) {
            val aabb = ship.shipAABB ?: return 0
            aabb.forEach { x, y, z ->
                if (dropBlocks)
                    level.destroyBlock(BlockPos(x, y, z), true)
                else
                    // Not sure if 2 is what we want, but it's what /fill uses
                    level.setBlock(BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2)
            }
        }

        vsCore.deleteShips(level.shipObjectWorld, listOf<ServerShip>(ship))
        return 1
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

        // getType is used for referencing this processor from a datapack, which we don't need
        override fun getType(): StructureProcessorType<*>? = null
    }
}
