package org.valkyrienskies.mod.common.blockentity

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.ships.PhysShip
import org.valkyrienskies.core.api.util.PhysTickOnly
import org.valkyrienskies.core.api.world.PhysLevel
import org.valkyrienskies.core.api.world.properties.DimensionId
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.util.toJOMLD

@OptIn(PhysTickOnly::class)
class TestThrusterBlockEntity(blockPos: BlockPos, blockState: BlockState) : BlockEntityPhysicsListener, BlockEntity (
    ValkyrienSkiesMod.TEST_THRUSTER_BLOCK_ENTITY_TYPE, blockPos, blockState
){

    @Volatile
    override lateinit var dimension: DimensionId

    @Volatile
    var isActive: Boolean = blockState.getValue(BlockStateProperties.POWERED)

    val facing: Direction
        get() = blockState.getValue(BlockStateProperties.FACING)
    val pos: BlockPos
        get() = worldPosition

    @OptIn(VsBeta::class)
    override fun physTick(physShip: PhysShip?, physLevel: PhysLevel) {
        if (!isActive || physShip == null) {
            return
        }
        
        physShip.applyModelForce(facing.normal.toJOMLD().mul(100000.0), pos.toJOMLD().add(0.5, 0.5, 0.5))
    }
}
