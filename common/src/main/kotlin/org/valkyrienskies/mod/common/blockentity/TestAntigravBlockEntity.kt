package org.valkyrienskies.mod.common.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

/**
 * Exists solely to test [BlockEntityPhysicsListener]s.
 */
class TestAntigravBlockEntity(blockPos: BlockPos, blockState: BlockState) : BlockEntity(ValkyrienSkiesMod.TEST_ANTIGRAV_BLOCK_ENTITY_TYPE,
    blockPos, blockState
), BlockEntityPhysicsListener {

    @Volatile
    override lateinit var dimension: DimensionId

    override fun physTick(
        physShip: PhysShip?, physLevel: PhysLevel
    ) {
        if (physShip == null) {
            return
        }
        val atmoGravity = physLevel.aerodynamicUtils.getAtmosphereForDimension(physLevel.dimension).third
        val forceToCancel = physShip.mass.times(atmoGravity)
        val forceVec = Vector3d(0.0, forceToCancel, 0.0)
        physShip.applyWorldForceToBodyPos(forceVec)
    }

}
