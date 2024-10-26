package org.valkyrienskies.mod.common.assembly

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.Vector3i
import org.joml.Vector3ic
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.getAttachment
import org.valkyrienskies.core.impl.game.ShipTeleportDataImpl
import org.valkyrienskies.mod.common.BlockStateInfo.onSetBlock
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipObjectManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.SplittingDisablerAttachment
import org.valkyrienskies.mod.common.util.toJOMLD
import org.valkyrienskies.mod.util.logger

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

        var structureCornerMin: BlockPos = blocks[0]
        var structureCornerMax: BlockPos = blocks[0]
        var hasSolids = false

        // Calculate bounds of the area containing all blocks adn check for solids and invalid blocks
        for (itPos in blocks) {
            if (isValidShipBlock(level.getBlockState(itPos))) {
                structureCornerMin = AssemblyUtil.getMinCorner(structureCornerMin, itPos)
                structureCornerMax = AssemblyUtil.getMaxCorner(structureCornerMax, itPos)
                hasSolids = true
            }
        }
        if (!hasSolids) throw IllegalArgumentException("No solid blocks found in the structure")
        val contraptionOGPos: Vector3ic = AssemblyUtil.getMiddle(structureCornerMin, structureCornerMax)
        // Create new contraption at center of bounds
        val contraptionWorldPos: Vector3i = if (existingShip != null) {
            val doubleVer = existingShip.shipToWorld.transformPosition(Vector3d(contraptionOGPos)).floor()
            Vector3i(doubleVer.x.toInt(), doubleVer.y.toInt(), doubleVer.z.toInt())
        } else {
            Vector3i(contraptionOGPos)
        }
        //val contraptionPosition = ContraptionPosition(Quaterniond(Vec3d(0.0, 1.0, 1.0), 0.0), contraptionWorldPos, null)

        val newShip: Ship = (level as ServerLevel).server.shipObjectWorld
            .createNewShipAtBlock(contraptionWorldPos, false, scale, level.dimensionId)

        if (shouldDisableSplitting) {
            level.shipObjectWorld.loadedShips.getById(newShip.id)?.getAttachment<SplittingDisablerAttachment>()?.disableSplitting()

        }

        val contraptionShipPos = newShip.worldToShip.transformPosition(Vector3d(contraptionWorldPos.x.toDouble(),contraptionWorldPos.y.toDouble(),contraptionWorldPos.z.toDouble()))
        val contraptionBlockPos = BlockPos(contraptionShipPos.x.toInt(),contraptionShipPos.y.toInt(),contraptionShipPos.z.toInt())


        // Copy blocks and check if the center block got replaced (is default a stone block)
        var centerBlockReplaced = false
        for (itPos in blocks) {
            if (isValidShipBlock(level.getBlockState(itPos))) {
                val relative: BlockPos = itPos.subtract( BlockPos(contraptionOGPos.x(),contraptionOGPos.y(),contraptionOGPos.z()))
                val shipPos: BlockPos = contraptionBlockPos.offset(relative)
                AssemblyUtil.copyBlock(level, itPos, shipPos)
                if (relative.equals(BlockPos.ZERO)) centerBlockReplaced = true
            }
        }

        // If center block got not replaced, remove the stone block
        // if (!centerBlockReplaced) {
        //     level.setBlock(contraptionBlockPos, Blocks.AIR.defaultBlockState(), 3)
        // }

        // Remove original blocks
        if (removeOriginal) {
            for (itPos in blocks) {
                if (isValidShipBlock(level.getBlockState(itPos))) {
                    AssemblyUtil.removeBlock(level, itPos)
                }
            }
        }

        // Trigger updates on both contraptions
        for (itPos in blocks) {
            val relative: BlockPos = itPos.subtract(BlockPos(contraptionOGPos.x(),contraptionOGPos.y(),contraptionOGPos.z()))
            val shipPos: BlockPos = contraptionBlockPos.offset(relative)
            AssemblyUtil.updateBlock(level,itPos,shipPos,level.getBlockState(shipPos))
        }

        val spawnWorldPos: Vector3dc = AssemblyUtil.getMiddle(structureCornerMin.toJOMLD(), structureCornerMax.toJOMLD()).add(0.5, 0.5, 0.5)

        if (existingShip != null) {
            sLevel.server.shipObjectWorld
                .teleportShip(newShip as ServerShip, ShipTeleportDataImpl(existingShip.shipToWorld.transformPosition(spawnWorldPos, Vector3d()), existingShip.transform.shipToWorldRotation, existingShip.velocity, existingShip.omega, existingShip.chunkClaimDimension))

        } else {
            sLevel.server.shipObjectWorld
                .teleportShip(newShip as ServerShip, ShipTeleportDataImpl(spawnWorldPos))
        }
        if (shouldDisableSplitting) {
            level.shipObjectWorld.loadedShips.getById(newShip.id)?.getAttachment<SplittingDisablerAttachment>()?.enableSplitting()
        }

        return newShip as ServerShip
    }


}
