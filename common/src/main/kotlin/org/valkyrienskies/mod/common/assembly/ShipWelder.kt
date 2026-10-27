package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import net.minecraft.world.Clearable
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Matrix4d
import org.joml.Matrix3d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3i
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.internal.ships.VsiServerShip
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.executeIf
import org.valkyrienskies.mod.common.isChunkLoadedForVS
import org.valkyrienskies.mod.common.networking.sendRestartChunkUpdates
import org.valkyrienskies.mod.common.networking.sendStopChunkUpdates
import org.valkyrienskies.mod.common.playerWrapper
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.toDenseVoxelUpdate
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment
import org.valkyrienskies.mod.common.util.collectShipBlocks
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.common.util.ShipGravityAttachment
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.vsCore
import org.valkyrienskies.mod.util.logger
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@OptIn(GameTickOnly::class)
internal object ShipWelder {
    private val log = logger("(Valkyrien Skies) Ship Welder").logger
    private data class Move(val from: BlockPos, val to: BlockPos, val oldState: BlockState, val newState: BlockState,
                            val originalTag: CompoundTag?, var pasteTag: CompoundTag?)
    private data class Plan(val rotation: WeldRotation, val moves: List<Move>, val targetBlocks: Set<BlockPos>)
    private data class Preparation(val plan: Plan? = null, val refusal: String? = null)
    private data class Session(val level: ServerLevel, val player: UUID, val targetPos: BlockPos, val targetFace: Direction,
                               val sourcePos: BlockPos, val sourceFace: Direction, val plan: Plan, val drive: WeldPullDrive,
                               val startTick: Int, val sourceWasStatic: Boolean, val sourceMass: Double, val sourceCenter: Vector3d,
                               val sourceTensor: Matrix3d, val sourceState: BlockState, val targetState: BlockState)
    private val sessions = LinkedHashMap<UUID, Session>()
    private val drives = ConcurrentHashMap<UUID, WeldPullDrive>()

    private fun prepare(level: ServerLevel, target: LoadedServerShip, targetPos: BlockPos, targetFace: Direction,
                        source: LoadedServerShip, sourcePos: BlockPos, sourceFace: Direction,
                        fixedRotation: WeldRotation? = null): Preparation {
        fun refuse(reason: String) = Preparation(refusal = reason)
        if (source.id == target.id) return refuse("same_ship")
        val sourceBlocks = collectShipBlocks(level, source) ?: return refuse("unloaded")
        val targetBlocks = collectShipBlocks(level, target) ?: return refuse("unloaded")
        if (sourcePos !in sourceBlocks || targetPos !in targetBlocks) return refuse("stale")
        if (source.getAttachment<SplittingDisablerAttachment>()?.canSplit() == false ||
            target.getAttachment<SplittingDisablerAttachment>()?.canSplit() == false) return refuse("busy")
        val scale = target.transform.scaling
        if ((0..2).any { abs(scale.get(it) - source.transform.scaling.get(it)) > 1e-6 } ||
            abs(scale.x() - scale.y()) > 1e-6 || abs(scale.x() - scale.z()) > 1e-6) return refuse("scale")

        val adapter = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        val joints = adapter.getAllJoints().toMutableMap()
        val persisted = level.shipObjectWorld.persistedJointStore
        persisted.getJointIdsForShip(source.id).forEach { id -> persisted.getJoint(id)?.let { joints.putIfAbsent(id, it) } }
        val sourceJoints = joints.filterValues { it.shipId0 == source.id || it.shipId1 == source.id }
        if (sourceJoints.isNotEmpty()) return refuse("jointed")

        val relative = Quaterniond(target.transform.rotation).invert().mul(source.transform.rotation)
        var chosenRotation: WeldRotation? = null
        var moves: List<Move>? = null
        for (rotation in fixedRotation?.let { listOf(it) } ?: WeldRotation.candidates(sourceFace, targetFace, relative)) {
            val candidate = ArrayList<Move>(sourceBlocks.size)
            for (pos in sourceBlocks) {
                val state = level.getBlockState(pos)
                if (rotation.yaw == null && state.hasBlockEntity() ||
                    rotation.yaw != net.minecraft.world.level.block.Rotation.NONE && state.block is ICopyableBlock) break
                val newState = rotation.rotateState(state) ?: break
                val destination = rotation.destination(pos, sourcePos, targetPos, targetFace)
                candidate.add(Move(pos, destination, state, newState, null, null))
            }
            if (candidate.size == sourceBlocks.size) {
                chosenRotation = rotation
                moves = candidate
                break
            }
        }
        if (chosenRotation == null) return refuse("rotation")
        val planned = moves!!
        if (planned.any { !target.chunkClaim.contains(it.to.x shr 4, it.to.z shr 4) || level.isOutsideBuildHeight(it.to) }) return refuse("bounds")
        planned.map { ChunkPos(it.to) }.toSet().forEach { level.getChunk(it.x, it.z) }
        if (planned.any { !level.getBlockState(it.to).isAir }) return refuse("overlap")
        return Preparation(Plan(chosenRotation, planned, targetBlocks))
    }

    fun start(level: ServerLevel, player: UUID, target: LoadedServerShip, targetPos: BlockPos, targetFace: Direction,
              source: LoadedServerShip, sourcePos: BlockPos, sourceFace: Direction): String? {
        if (sessions.containsKey(player) || sessions.values.any {
                it.drive.targetId in setOf(target.id, source.id) || it.drive.sourceId in setOf(target.id, source.id)
            }) return "busy"
        val preparation = prepare(level, target, targetPos, targetFace, source, sourcePos, sourceFace)
        preparation.refusal?.let { return it }
        val plan = preparation.plan!!
        val sourceCenter = Vector3d(source.inertiaData.centerOfMass)
        val selectedCenter = Vector3d(sourcePos.x + 0.5, sourcePos.y + 0.5, sourcePos.z + 0.5)
        val destination = targetPos.relative(targetFace)
        val rotation = plan.rotation.quaternion()
        val modelCenter = rotation.transform(Vector3d(sourceCenter).sub(selectedCenter))
            .add(destination.x + 0.5, destination.y + 0.5, destination.z + 0.5)
        val scale = source.transform.scaling.x()
        val radius = (plan.moves.maxOf { Vector3d(it.from.x + 0.5, it.from.y + 0.5, it.from.z + 0.5).distance(sourceCenter) } + sqrt(3.0) / 2.0) * scale
        val drive = WeldPullDrive(level.dimensionId, target.id, source.id, modelCenter, rotation, radius, min(0.02, 0.02 * scale))
        drive.gravityOverride = source.getAttachment<ShipGravityAttachment>()?.gravityOverride
        val session = Session(level, player, targetPos.immutable(), targetFace, sourcePos.immutable(), sourceFace,
            plan, drive, level.server.tickCount, source.isStatic, source.inertiaData.mass, sourceCenter,
            Matrix3d(source.inertiaData.inertiaTensor), level.getBlockState(sourcePos), level.getBlockState(targetPos))
        source.isStatic = false
        sessions[player] = session
        drives[player] = drive
        return null
    }

    @JvmStatic
    @OptIn(PhysTickOnly::class)
    fun physTick(level: PhysLevel, delta: Double) {
        drives.values.forEach { it.tick(level, delta) }
    }

    @JvmStatic
    fun tick(level: ServerLevel) {
        for (session in sessions.values.toList()) {
            if (session.level.server.getLevel(session.level.dimension()) !== session.level) {
                finish(session, "stale", false)
                continue
            }
            if (session.level !== level) continue
            val drive = session.drive
            val source = level.shipObjectWorld.loadedShips.getById(drive.sourceId)
            val target = level.shipObjectWorld.loadedShips.getById(drive.targetId)
            val player = level.server.playerList.getPlayer(session.player)
            val invalid = source == null || target == null || player?.level() !== level || drive.invalid ||
                level.getLoadedShipManagingPos(session.sourcePos)?.id != drive.sourceId ||
                level.getLoadedShipManagingPos(session.targetPos)?.id != drive.targetId ||
                level.getBlockState(session.sourcePos) != session.sourceState ||
                level.getBlockState(session.targetPos) != session.targetState ||
                source != null && (abs(source.inertiaData.mass - session.sourceMass) > 1e-6 ||
                    Vector3d(source.inertiaData.centerOfMass).distance(session.sourceCenter) > 1e-6 ||
                    !session.sourceTensor.equals(source.inertiaData.inertiaTensor, 1e-6) || source.isStatic ||
                    level.shipObjectWorld.persistedJointStore.hasJointsForShip(source.id) ||
                    ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId).getJointsFromShip(source.id).isNotEmpty())
            if (invalid) { finish(session, "stale", false); continue }
            if (level.server.tickCount - session.startTick > 1200) { finish(session, "timeout", false); continue }
            val readySource = source!!
            val readyTarget = target!!
            drive.gravityOverride = readySource.getAttachment<ShipGravityAttachment>()?.gravityOverride
            if (drive.settledSeconds < 0.25) continue
            val goal = WeldPullController.goal(pose(readyTarget), readyTarget.shipToWorld, drive.modelCenter, drive.relativeRotation)
            if (!WeldPullController.command(pose(readySource), goal, drive.radius).aligned(drive.tolerance)) continue
            val preparation = prepare(level, readyTarget, session.targetPos, session.targetFace, readySource, session.sourcePos, session.sourceFace, session.plan.rotation)
            if (preparation.refusal != null) { finish(session, preparation.refusal, false); continue }
            val originalBlocks = session.plan.moves.associate { it.from to it.oldState }
            if (preparation.plan!!.moves.size != originalBlocks.size || preparation.plan.moves.any { originalBlocks[it.from] != it.oldState }) {
                finish(session, "stale", false)
                continue
            }
            drive.active = false
            try {
                val refusal = merge(level, readyTarget, session.targetPos, session.targetFace, readySource, session.sourcePos, preparation.plan)
                finish(session, refusal ?: "success", refusal == null)
            } catch (exception: Exception) {
                log.error("Could not complete ship weld", exception)
                finish(session, "failed", false)
            }
        }
    }

    fun cancel(player: UUID): Boolean {
        val session = sessions[player] ?: return false
        finish(session, "cancelled", false)
        return true
    }

    @JvmStatic
    fun cancelAll(server: MinecraftServer) {
        sessions.values.toList().filter { it.level.server === server }.forEach { finish(it, null, false) }
    }

    private fun finish(session: Session, message: String?, merged: Boolean) {
        session.drive.active = false
        drives.remove(session.player, session.drive)
        sessions.remove(session.player)
        if (!merged && session.sourceWasStatic) session.level.shipObjectWorld.allShips.getById(session.drive.sourceId)?.isStatic = true
        message?.let { session.level.server.playerList.getPlayer(session.player)?.sendSystemMessage(Component.translatable("item.valkyrienskies.ship_welder.$it")) }
    }

    private fun pose(ship: LoadedServerShip) = WeldPullController.Pose(ship.kinematics.position, ship.transform.rotation, ship.velocity, ship.angularVelocity)

    private fun merge(level: ServerLevel, target: LoadedServerShip, targetPos: BlockPos, targetFace: Direction,
                      source: LoadedServerShip, sourcePos: BlockPos, plan: Plan): String? {
        val planned = plan.moves
        val targetBlocks = plan.targetBlocks

        val fromCenter = Vector3d(sourcePos.x + 0.5, sourcePos.y + 0.5, sourcePos.z + 0.5)
        val destinationAnchor = targetPos.relative(targetFace)
        val toCenter = Vector3d(destinationAnchor.x + 0.5, destinationAnchor.y + 0.5, destinationAnchor.z + 0.5)
        val ids = mapOf(source.id to target.id)
        val centers = mapOf(source.id to (fromCenter to toCenter))
        // Capture every block entity before placing or removing any blocks (including inventory contents).
        val saved = planned.map { move ->
            val entity = level.getBlockEntity(move.from)
            val originalTag = entity?.saveWithFullMetadata()
            val copyTag = (move.oldState.block as? ICopyableBlock)?.onCopy(
                level, move.from, move.oldState, entity, listOf(source), mapOf(source.id to fromCenter)
            ) ?: originalTag?.copy()
            move.copy(originalTag = originalTag, pasteTag = copyTag)
        }
        val oldTransform = Matrix4d(target.shipToWorld)
        val oldKinematics = target.kinematics
        val oldCom = oldTransform.transformPosition(Vector3d(target.inertiaData.centerOfMass))
        val oldVelocity = Vector3d(target.velocity)
        val oldOmega = Vector3d(target.angularVelocity)
        val oldRotation = Quaterniond(target.transform.rotation)
        val oldScale = Vector3d(target.transform.scaling)
        val targetSplitting = target.getAttachment<SplittingDisablerAttachment>() ?: SplittingDisablerAttachment(true).also { target.setAttachment(it) }
        val sourceSplitting = source.getAttachment<SplittingDisablerAttachment>() ?: SplittingDisablerAttachment(true).also { source.setAttachment(it) }
        val chunks = saved.flatMap { listOf(ChunkPos(it.from), ChunkPos(it.to)) }.toSet().toList()
        val chunkVectors = chunks.map { it.toJOML() }
        targetSplitting.disableSplitting()
        sourceSplitting.disableSplitting()
        var removingSource = false
        var success = false
        try {
            level.players().forEach { player -> with(vsCore.simplePacketNetworking) { sendStopChunkUpdates(chunkVectors, player.playerWrapper) } }
            for (move in saved) level.getChunkAt(move.to).setBlockState(move.to, move.newState, false)
            for (move in saved) {
                if (level.getBlockState(move.to) != move.newState) error("Could not place welded block at ${move.to}")
                val copyable = move.newState.block as? ICopyableBlock
                move.pasteTag = copyable?.onPaste(level, move.to, move.newState, ids, centers, move.pasteTag) ?: move.pasteTag
                move.pasteTag?.let { tag ->
                    val entity = level.getBlockEntity(move.to)
                    if (move.newState.hasBlockEntity() && entity == null) error("Missing welded block entity at ${move.to}")
                    entity?.load(WeldBlockData.relocatedTag(tag, move.to))
                    entity?.setChanged()
                }
            }
            removingSource = true
            for (move in saved) clearBlock(level, move.from)
            val newCom = Vector3d(target.inertiaData.centerOfMass)
            val center = WeldKinematics.rebase(oldTransform, oldCom, oldVelocity, oldOmega, newCom)
            (target as VsiServerShip).unsafeSetKinematics(vsCore.newBodyKinematics(center.velocity, oldOmega,
                vsCore.newBodyTransform(center.position, oldRotation, oldScale, newCom)))
            level.shipObjectWorld.deleteShip(source)
            success = true
        } catch (exception: Exception) {
            log.error("Ship welding failed; restoring the original blocks", exception)
            saved.forEach { clearBlock(level, it.to) }
            if (removingSource) saved.forEach { move ->
                level.getChunkAt(move.from).setBlockState(move.from, move.oldState, false)
                move.originalTag?.let { tag -> level.getBlockEntity(move.from)?.let { it.load(tag); it.setChanged() } }
            }
            (target as VsiServerShip).unsafeSetKinematics(oldKinematics)
        } finally {
            val startTick = level.server.tickCount
            try {
                level.server.executeIf({ chunks.all(level::isChunkLoadedForVS) || level.server.tickCount - startTick > 20 }) {
                    try {
                        level.players().forEach { player -> with(vsCore.simplePacketNetworking) { sendRestartChunkUpdates(chunkVectors, player.playerWrapper) } }
                    } finally {
                        targetSplitting.enableSplitting()
                        sourceSplitting.enableSplitting()
                    }
                }
            } catch (exception: Exception) {
                targetSplitting.enableSplitting()
                sourceSplitting.enableSplitting()
                level.players().forEach { player -> with(vsCore.simplePacketNetworking) { sendRestartChunkUpdates(chunkVectors, player.playerWrapper) } }
                throw exception
            }
            saved.forEach { move ->
                notifyBlock(level, move.from)
                notifyBlock(level, move.to)
            }
            val sections = saved.flatMap { listOf(Vector3i(it.from.x shr 4, it.from.y shr 4, it.from.z shr 4),
                Vector3i(it.to.x shr 4, it.to.y shr 4, it.to.z shr 4)) }.toSet()
            sections.forEach { pos ->
                val chunk = level.getChunk(pos.x, pos.z)
                val section = chunk.sections[chunk.getSectionIndexFromSectionY(pos.y)]
                level.shipObjectWorld.forceUpdateConnectivityChunk(level.dimensionId, pos.x, pos.y, pos.z, section.toDenseVoxelUpdate(pos, level))
            }
            ShipAssembler.initSkyLightForShip(level, targetBlocks.toList() + if (success) saved.map { it.to } else emptyList())
        }
        return if (success) null else "failed"
    }

    private fun clearBlock(level: ServerLevel, pos: BlockPos) {
        level.getBlockEntity(pos)?.let { Clearable.tryClear(it) }
        level.removeBlockEntity(pos)
        level.getChunkAt(pos).setBlockState(pos, Blocks.AIR.defaultBlockState(), false)
    }

    private fun notifyBlock(level: ServerLevel, pos: BlockPos) {
        val state = level.getBlockState(pos)
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)
        level.blockUpdated(pos, state.block)
        level.chunkSource.lightEngine.checkBlock(pos)
    }
}
