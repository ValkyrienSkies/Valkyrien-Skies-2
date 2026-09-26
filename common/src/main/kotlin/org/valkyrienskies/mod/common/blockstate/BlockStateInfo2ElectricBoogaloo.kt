package org.valkyrienskies.mod.common.blockstate

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.ships.Wing
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.WingBlock
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld

object BlockStateInfo2ElectricBoogaloo {

    fun onSetBlock(level: Level, x: Int, y: Int, z: Int, oldState: BlockState, newState: BlockState) {
        val shipObjectWorld = level.shipObjectWorld

        if (level is ServerLevel) { // inject wings
            val loadedShip = level.getLoadedShipManagingPos(x shr 4, z shr 4)
            if (loadedShip != null) {
                val wingManager = loadedShip.wingManager!!
                val wasOldBlockWing = oldState.block is WingBlock
                val newBlockStateBlock = newState.block
                val newWing: Wing? =
                    if (newBlockStateBlock is WingBlock) newBlockStateBlock.getWing(
                        level, BlockPos(x, y, z), newState
                    ) else null
                if (newWing != null) {
                    // Place the new wing
                    wingManager.setWing(wingManager.getFirstWingGroupId(), x, y, z, newWing)
                } else if (wasOldBlockWing) {
                    // Delete the old wing
                    wingManager.setWing(wingManager.getFirstWingGroupId(), x, y, z, null)
                }
            }
        }

        shipObjectWorld.onSetBlock(
            x, y, z, level.dimensionId, oldState.vsType, newState.vsType, oldState,
            newBlockMass
        )

        if (level is ServerLevel) {
            if (ValkyrienSkiesMod.vsCore.hooks.enableConnectivity) {
                ValkyrienSkiesMod.splitHandler.queueSplit(level, level.getShipManagingPos(x.toDouble(), y.toDouble(), z.toDouble())?.id)
            }
        }
    }
}
