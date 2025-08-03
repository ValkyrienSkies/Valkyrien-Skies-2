package org.valkyrienskies.mod.common.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.util.ITestTickable
import org.valkyrienskies.mod.common.util.toJOMLD

class TestThrusterBlockEntity(blockPos: BlockPos, blockState: BlockState) : ITestTickable, BlockEntity (
    ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE, blockPos, blockState
){
    var added = false

    @Volatile
    var isActive = false

    @Volatile
    var shipMountedTo = -1L

    val facing: Direction
        get() = blockState.getValue(BlockStateProperties.FACING)
    val pos: BlockPos
        get() = worldPosition

    override fun setRemoved() {
        DebugPhysicsTickables.remove(this)
        super.setRemoved()
    }

    override fun matchesDimension(
        dimensionId: DimensionId
    ): Boolean {
        return level?.dimensionId == dimensionId
    }

    @OptIn(VsBeta::class)
    override fun physTick(physLevel: PhysLevel, delta: Double) {
        if (!isActive || shipMountedTo < 0) {
            return
        }
        val ship = physLevel.getShipById(shipMountedTo) ?: return
        
        ship.applyRotDependentForceToPos(facing.normal.toJOMLD().mul(50000.0), pos.toJOMLD().add(0.5, 0.5, 0.5).sub(ship.transform.positionInShip))
    }
}
