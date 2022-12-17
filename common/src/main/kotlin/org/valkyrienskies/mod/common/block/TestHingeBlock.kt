package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Material
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.AxisAngle4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.apigame.constraints.VSAttachmentConstraint
import org.valkyrienskies.core.apigame.constraints.VSHingeOrientationConstraint
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import kotlin.math.roundToInt

object TestHingeBlock :
    HorizontalDirectionalBlock(
        Properties.of(Material.METAL).strength(10.0f, 1200.0f).sound(SoundType.METAL)
    ), EntityBlock {
    private val SEAT_AABB: VoxelShape = box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)

    init {
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        return defaultBlockState()
            .setValue(FACING, ctx.horizontalDirection.opposite)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        state: BlockState, level: BlockGetter?, pos: BlockPos?, context: CollisionContext?
    ): VoxelShape = SEAT_AABB

    @Deprecated("Deprecated in Java")
    override fun use(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        blockHitResult: BlockHitResult
    ): InteractionResult {
        println("test")
        if (level.isClientSide) return InteractionResult.SUCCESS

        val blockEntity = level.getBlockEntity(pos, ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE)

        if (blockEntity.isPresent) {
            if (blockEntity.get().otherHingePos == null) {
                val ship = (level as ServerLevel).shipObjectWorld.createNewShipAtBlock(
                    pos.offset(0, 1, 0).toJOML(), false, 1.0, level.dimensionId
                )
                val shipCenterPos = BlockPos(
                    (ship.transform.positionInShip.x() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.y() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.z() - 0.5).roundToInt()
                )
                // println("shipCenterPos is $shipCenterPos")
                level.setBlockAndUpdate(shipCenterPos, Blocks.IRON_BLOCK.defaultBlockState())
                blockEntity.get().otherHingePos = shipCenterPos

                val shipThisIsIn = level.getShipManagingPos(pos)

                val shipId0 = shipThisIsIn?.id ?: level.shipObjectWorld.dimensionToGroundBodyIdImmutable[level.dimensionId]!!
                val shipId1 = ship.id

                // Attachment constraint
                run {
                    val attachmentCompliance = 1e-8
                    val attachmentLocalPos0: Vector3dc = Vector3d(pos.x + .5, pos.y + 1.5, pos.z + .5)
                    val attachmentLocalPos1: Vector3dc =
                        Vector3d(shipCenterPos.x.toDouble(), shipCenterPos.y.toDouble(), shipCenterPos.z.toDouble())
                    val attachmentMaxForce = 1e8
                    val attachmentFixedDistance = 0.0
                    val attachmentConstraint = VSAttachmentConstraint(
                        shipId0, shipId1, attachmentCompliance, attachmentLocalPos0, attachmentLocalPos1,
                        attachmentMaxForce, attachmentFixedDistance
                    )
                    blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(attachmentConstraint)
                }

                // Hinge orientation constraint
                run {
                    val hingeMaxTorque = 1e10
                    // Hinge constraints will attempt to align the X-axes of both bodies, so to align the Y axis we
                    // apply this rotation to the X-axis
                    // TODO: Logically this should be Quaterniond(AxisAngle4d(Math.toRadians(90.0), 0.0, 0.0, 1.0))
                    //       instead, maybe the physics api is broken?
                    val hingeOrientation = Quaterniond(AxisAngle4d(Math.toRadians(90.0), 1.0, 0.0, 0.0))
                    val hingeConstraint = VSHingeOrientationConstraint(
                        shipId0, shipId1, 1e-10, hingeOrientation, hingeOrientation, hingeMaxTorque
                    )
                    blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(hingeConstraint)
                }

                // Pos damping constraint
                // val posDampingConstraint = VSPosDampingConstraint(shipId0, shipId1, compliance, localPos0, localPos1, maxForce, 1e-3)
                // blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(posDampingConstraint)

                // Rot damping constraint
                // val rotDampingConstraint = VSRotDampingConstraint(shipId0, shipId1, compliance, Quaterniond(), Quaterniond(), maxTorque, 1e-2)
                // blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(rotDampingConstraint)
            }
        }
        return InteractionResult.CONSUME
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): TestHingeBlockEntity =
        TestHingeBlockEntity(blockPos, blockState)

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = BlockEntityTicker { _, _, _, blockEntity ->
        if (level.isClientSide) return@BlockEntityTicker
        if (blockEntity is TestHingeBlockEntity) {
            blockEntity.tick()
        }
    }
}
