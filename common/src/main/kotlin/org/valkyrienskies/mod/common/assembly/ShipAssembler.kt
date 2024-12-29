package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.core.api.attachment.getAttachment
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.mod.common.BlockStateInfo.onSetBlock
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment

object ShipAssembler {

    fun triggerBlockChange(level: Level?, pos: BlockPos?, prevState: BlockState?, newState: BlockState?) {
        onSetBlock(level!!, pos!!, prevState!!, newState!!)
    }

    fun isValidShipBlock(state: BlockState?): Boolean {
        if (state != null) {
            //return !state.tags.anyMatch { it== VsShipAssemblerTags.FORBIDDEN_ASSEMBLE }
            return !state.isAir
        }
        return true
    }


    fun assembleToShip(level: Level, blocks: List<BlockPos>, removeOriginal: Boolean, scale: Double = 1.0, shouldDisableSplitting: Boolean = false): ServerShip {
        assert(level is ServerLevel) { "Can't create ships clientside!" }
        val sLevel: ServerLevel = level as ServerLevel
        if (blocks.isEmpty()) {
            throw IllegalArgumentException("No blocks to assemble.")
        }

        val existingShip = sLevel.getShipObjectManagingPos(blocks.find { !sLevel.getBlockState(it).isAir } ?: throw IllegalArgumentException())

        var existingShipCouldSplit = true
        var structureCornerMin: BlockPos? = null
        var structureCornerMax: BlockPos? = null
        var hasSolids = false

        // Calculate bounds of the area containing all blocks adn check for solids and invalid blocks
        for (itPos in blocks) {
            if (isValidShipBlock(level.getBlockState(itPos))) {
                if (structureCornerMin == null) {
                    structureCornerMin = itPos
                    structureCornerMax = itPos
                } else {
                    structureCornerMin = AssemblyUtil.getMinCorner(structureCornerMin!!, itPos)
                    structureCornerMax = AssemblyUtil.getMaxCorner(structureCornerMax!!, itPos)
                }
                hasSolids = true
            }
        }
        if (!hasSolids) throw IllegalArgumentException("No solid blocks found in the structure")
        val shipOGPos: Vector3ic = AssemblyUtil.getMiddle(structureCornerMin!!, structureCornerMax!!)
        // Create new ship at center of bounds
        val shipWorldPos: Vector3i = if (existingShip != null) {
            val doubleVer = existingShip.shipToWorld.transformPosition(Vector3d(shipOGPos)).floor()
            Vector3i(doubleVer.x.toInt(), doubleVer.y.toInt(), doubleVer.z.toInt())
        } else {
            Vector3i(shipOGPos)
        }

        val newShip: Ship = (level as ServerLevel).server.shipObjectWorld
            .createNewShipAtBlock(shipWorldPos, false, scale, level.dimensionId)

        if (shouldDisableSplitting) {
            level.shipObjectWorld.loadedShips.getById(newShip.id)?.getAttachment<SplittingDisablerAttachment>()?.disableSplitting()
            if (existingShip != null) {
                existingShipCouldSplit = level.shipObjectWorld.loadedShips.getById(existingShip.id)?.getAttachment<SplittingDisablerAttachment>()?.canSplit() ?: true
                level.shipObjectWorld.loadedShips.getById(existingShip.id)?.getAttachment<SplittingDisablerAttachment>()?.disableSplitting()
            }
        }

        val shipspacePos = newShip.worldToShip.transformPosition(Vector3d(shipWorldPos.x.toDouble(),shipWorldPos.y.toDouble(),shipWorldPos.z.toDouble()))
        val shipBlockPos = BlockPos(shipspacePos.x.toInt(),shipspacePos.y.toInt(),shipspacePos.z.toInt())


        // Copy blocks and check if the center block got replaced (is default a stone block)
        var centerBlockReplaced = false
        for (itPos in blocks) {
            if (isValidShipBlock(level.getBlockState(itPos))) {
                val relative: BlockPos = itPos.subtract( BlockPos(shipOGPos.x(),shipOGPos.y(),shipOGPos.z()))
                val shipPos: BlockPos = shipBlockPos.offset(relative)
                AssemblyUtil.copyBlock(level, itPos, shipPos)
                if (relative.equals(BlockPos.ZERO)) centerBlockReplaced = true
            }
        }

        // Remove original blocks
        if (removeOriginal) {
            for (itPos in blocks) {
                if (isValidShipBlock(level.getBlockState(itPos))) {
                    AssemblyUtil.removeBlock(level, itPos)
                }
            }
        }

        // Trigger updates on both ships
        for (itPos in blocks) {
            val relative: BlockPos = itPos.subtract(BlockPos(shipOGPos.x(),shipOGPos.y(),shipOGPos.z()))
            val shipPos: BlockPos = shipBlockPos.offset(relative)
            AssemblyUtil.updateBlock(level,itPos,shipPos,level.getBlockState(shipPos))
        }

        val shipCenterPos = ((newShip as ServerShip).inertiaData.centerOfMass).add(0.5, 0.5, 0.5, Vector3d())
        // This is giga sus, but whatever
        val shipPos = Vector3d(shipOGPos).add(0.5, 0.5, 0.5)
        if (existingShip != null) {
            sLevel.server.shipObjectWorld
                .teleportShip(newShip as ServerShip, ShipTeleportDataImpl(existingShip.shipToWorld.transformPosition(shipPos, Vector3d()), existingShip.transform.shipToWorldRotation, existingShip.velocity, existingShip.omega, existingShip.chunkClaimDimension, newScale = existingShip.transform.shipToWorldScaling.x(), newPosInShip = shipCenterPos))

        } else {
            sLevel.server.shipObjectWorld
                .teleportShip(newShip as ServerShip, ShipTeleportDataImpl(newPos = shipPos, newPosInShip = shipCenterPos))
        }
        if (shouldDisableSplitting) {
            level.shipObjectWorld.loadedShips.getById(newShip.id)?.getAttachment<SplittingDisablerAttachment>()?.enableSplitting()
            if (existingShip != null) {
                if (existingShipCouldSplit){
                    level.shipObjectWorld.loadedShips.getById(existingShip.id)?.getAttachment<SplittingDisablerAttachment>()?.enableSplitting()
                } else {level.shipObjectWorld.loadedShips.getById(existingShip.id)?.getAttachment<SplittingDisablerAttachment>()?.disableSplitting()}
            }
        }

        return newShip as ServerShip
    }


}
