package org.valkyrienskies.mod.common.blockstate

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.Wing
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.block.WingBlock
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getLoadedShipManagingPos
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.vsCore

@OptIn(GameTickOnly::class)
object BlockStateInfo2ElectricBoogaloo {

    var isInitialized = false
        private set

    fun init() {
        isInitialized = true
    }

    fun onSetBlock(level: Level, blockPos: BlockPos, oldState: BlockState, newState: BlockState) =
        onSetBlock(level, blockPos.x, blockPos.y, blockPos.z, oldState, newState)

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
            x, y, z, level.dimensionId, oldState.vsType, newState.vsType, oldState.mass, newState.mass
        )

        if (level is ServerLevel) {
            if (ValkyrienSkiesMod.vsCore.hooks.enableConnectivity) {
                ValkyrienSkiesMod.splitHandler.queueSplit(level, level.getShipManagingPos(x.toDouble(), y.toDouble(), z.toDouble())?.id)
            }
        }
    }

    /**
     * Recalculates mass of a ship. Useful if block masses were changed in a data pack, game config or in VS itself.
     * The ship is made static before any of its physical properties are modified and only returns to original status
     * if recalculation has been successfully completed.
     *
     * NOTE: There is no distinction between masses that were added by placing real blocks and those that were
     * added "manually" by calling onSetBlock. Some addons implement custom masses in this hacky way. Before
     * triggering a remass, these custom masses should be removed.
     *
     * @return false if mass recalculation has failed for any reason
     */
    fun remassShip(level: Level, ship: Ship): Boolean {
        if (level !is ServerLevel) return false
        if (ship !is LoadedServerShip) return false

        val aabb = ship.shipAABB ?: return false

        // Last thing we want is something physical happening to our zero-mass ship.
        val wasStatic = ship.isStatic
        ship.isStatic = true

        // Before we rebuild masses, make sure we have a ship with zero mass and no colliders.
        // Blocks are replaced with air (in physical representation of a ship, no Minecraft blockstates are changed)
        BlockPos.betweenClosed(
            aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(),
            aabb.maxZ()
        ).forEach {
            val type = level.getVsiBlockType(it)
            if (type != vsCore.blockTypes.air) {
                level.shipObjectWorld.onSetBlock(
                    it.x, it.y, it.z,
                    level.dimensionId,
                    type, vsCore.blockTypes.air,
                    0.0, 0.0 // Making the block air without modifying ship mass, CoM and MoI
                )
            }
        }
        // Zeroing out ship mass.
        // This looks wrong but is actually fine. As per ShipInertiaDataImpl, if the resulting ship mass is zero,
        // its CoM and MoI are explicitly zeroed out, bypassing any calculations. Any blockPos inside ship AABB is good
        // for this purpose.
        level.shipObjectWorld.onSetBlock(
            aabb.minX(), aabb.minY(), aabb.minZ(), level.dimensionId,
            vsCore.blockTypes.air, vsCore.blockTypes.air,
            ship.inertiaData.mass, 0.0
        )
        // Readding all blocks to ship's physics representation (mass, block type)
        BlockPos.betweenClosed(
            aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(),
            aabb.maxZ()
        ).forEach {
            val state = level.getBlockState(it)
            if (state.vsType != vsCore.blockTypes.air) {
                level.shipObjectWorld.onSetBlock(
                    it.x, it.y, it.z,
                    level.dimensionId,
                    vsCore.blockTypes.air, state.vsType,
                    0.0, state.mass
                )
            }
        }
        ship.isStatic = wasStatic

        return true
    }
}
