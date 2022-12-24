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
import org.valkyrienskies.core.apigame.constraints.VSPosDampingConstraint
import org.valkyrienskies.core.apigame.constraints.VSRotDampingAxes.PERPENDICULAR
import org.valkyrienskies.core.apigame.constraints.VSRotDampingConstraint
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.core.impl.game.ships.ShipTransformImpl
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
        if (level.isClientSide) return InteractionResult.SUCCESS

        val blockEntity = level.getBlockEntity(pos, ValkyrienSkiesMod.TEST_HINGE_BLOCK_ENTITY_TYPE)

        if (blockEntity.isPresent) {
            if (blockEntity.get().otherHingePos == null) {
                // The ship that owns [pos]
                val shipThisIsIn = level.getShipManagingPos(pos)

                val ship = (level as ServerLevel).shipObjectWorld.createNewShipAtBlock(
                    pos.offset(0, 1, 0).toJOML(), false, 1.0, level.dimensionId
                )
                val shipCenterPos = BlockPos(
                    (ship.transform.positionInShip.x() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.y() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.z() - 0.5).roundToInt()
                )

                // Extra height added to the hinge to keep the top ship slightly above the bottom ship
                val extraHeight = 0.1

                val attachmentLocalPos0: Vector3dc = Vector3d(pos.x + 0.5, pos.y + 1.5 - 0.5 + extraHeight, pos.z + 0.5)
                val attachmentLocalPos1: Vector3dc =
                    Vector3d(shipCenterPos.x + 0.5, shipCenterPos.y + 0.5 - 0.5, shipCenterPos.z + 0.5)

                // Move [ship] if we are on a ship
                if (shipThisIsIn != null) {
                    // Put the new ship where the old ship is
                    val newPos = shipThisIsIn.transform.shipToWorld.transformPosition(attachmentLocalPos0, Vector3d())
                    val newTransform = ShipTransformImpl(
                        newPos,
                        ship.transform.positionInShip,
                        shipThisIsIn.transform.shipToWorldRotation, // Copy source ship rotation
                        ship.transform.shipToWorldScaling
                    )
                    // Update the ship transform
                    (ship as ShipDataCommon).transform = newTransform
                } else {
                    // Move ship up by [extraHeight]
                    val newTransform = ShipTransformImpl(
                        ship.transform.positionInWorld.add(0.0, extraHeight, 0.0, Vector3d()),
                        ship.transform.positionInShip,
                        ship.transform.shipToWorldRotation,
                        ship.transform.shipToWorldScaling
                    )
                    // Update the ship transform
                    (ship as ShipDataCommon).transform = newTransform
                }

                level.setBlockAndUpdate(shipCenterPos, Blocks.IRON_BLOCK.defaultBlockState())
                blockEntity.get().otherHingePos = shipCenterPos

                val shipId0 = shipThisIsIn?.id ?: level.shipObjectWorld.dimensionToGroundBodyIdImmutable[level.dimensionId]!!
                val shipId1 = ship.id

                // Attachment constraint
                run {
                    // I don't recommend setting compliance lower than 1e-10 because it tends to cause instability
                    // TODO: Investigate why small compliance cause instability
                    val attachmentCompliance = 1e-10
                    val attachmentMaxForce = 1e10
                    val attachmentFixedDistance = 0.0
                    val attachmentConstraint = VSAttachmentConstraint(
                        shipId0, shipId1, attachmentCompliance, attachmentLocalPos0, attachmentLocalPos1,
                        attachmentMaxForce, attachmentFixedDistance
                    )
                    blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(attachmentConstraint)
                }

                // Hinge constraints will attempt to align the X-axes of both bodies, so to align the Y axis we
                // apply this rotation to the X-axis
                val hingeOrientation = Quaterniond(AxisAngle4d(Math.toRadians(90.0), 0.0, 0.0, 1.0))

                // Hinge orientation constraint
                run {
                    // I don't recommend setting compliance lower than 1e-10 because it tends to cause instability
                    val hingeOrientationCompliance = 1e-10
                    val hingeMaxTorque = 1e10
                    val hingeConstraint = VSHingeOrientationConstraint(
                        shipId0, shipId1, hingeOrientationCompliance, hingeOrientation, hingeOrientation, hingeMaxTorque
                    )
                    blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(hingeConstraint)
                }

                // Add position damping to make the hinge more stable
                val posDampingConstraint = VSPosDampingConstraint(shipId0, shipId1, 1e-10, attachmentLocalPos0, attachmentLocalPos1, 1e10, 1e3)
                blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(posDampingConstraint)

                // Add perpendicular rotation damping to make the hinge more stable
                val perpendicularRotDampingConstraint = VSRotDampingConstraint(shipId0, shipId1, 1e-10, hingeOrientation, hingeOrientation, 1e10, 1e3, PERPENDICULAR)
                blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(perpendicularRotDampingConstraint)

                // Add parallel rotation damping to prevent the hinge from spinning forever
                // val parallelRotDampingConstraint = VSRotDampingConstraint(shipId0, shipId1, 0.0, hingeOrientation, hingeOrientation, 1e10, 1e-1, PARALLEL)
                // blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(parallelRotDampingConstraint)
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
