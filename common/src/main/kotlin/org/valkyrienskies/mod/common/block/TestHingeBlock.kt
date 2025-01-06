package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.DOWN
import net.minecraft.core.Direction.EAST
import net.minecraft.core.Direction.NORTH
import net.minecraft.core.Direction.SOUTH
import net.minecraft.core.Direction.UP
import net.minecraft.core.Direction.WEST
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.EntityBlock
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
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.apigame.joints.VSJointMaxForceTorque
import org.valkyrienskies.core.apigame.joints.VSJointPose
import org.valkyrienskies.core.apigame.joints.VSRevoluteJoint
import org.valkyrienskies.core.impl.bodies.properties.BodyKinematicsFactory
import org.valkyrienskies.core.impl.game.ships.ShipDataCommon
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import kotlin.math.roundToInt

object TestHingeBlock :
    DirectionalBlock(
        Properties.of(Material.METAL).strength(10.0f, 1200.0f).sound(SoundType.METAL)
    ), EntityBlock {

    private val EAST_AABB = box(0.0, 0.0, 0.0, 8.0, 16.0, 16.0)
    private val WEST_AABB = box(8.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    private val SOUTH_AABB = box(0.0, 0.0, 0.0, 16.0, 16.0, 8.0)
    private val NORTH_AABB = box(0.0, 0.0, 8.0, 16.0, 16.0, 16.0)
    private val UP_AABB =  box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0)
    private val DOWN_AABB = box(0.0, 8.0, 0.0, 16.0, 16.0, 16.0)

    init {
        registerDefaultState(this.stateDefinition.any().setValue(FACING, UP))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(FACING, ctx.nearestLookingDirection.opposite)
    }

    @Deprecated("Deprecated in Java")
    override fun getShape(
        state: BlockState, level: BlockGetter?, pos: BlockPos?, context: CollisionContext?
    ): VoxelShape {
        when (state.getValue(FACING)) {
            DOWN -> {
                return DOWN_AABB
            }
            NORTH -> {
                return NORTH_AABB
            }
            SOUTH -> {
                return SOUTH_AABB
            }
            WEST -> {
                return WEST_AABB
            }
            EAST -> {
                return EAST_AABB
            }
            UP -> {
                return UP_AABB
            }
            else -> {
                // This should be impossible, but have this here just in case
                return UP_AABB
            }
        }
    }

    @OptIn(VsBeta::class)
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

                // Create an empty ship
                val ship = (level as ServerLevel).shipObjectWorld.createNewShipAtBlock(
                    pos.offset(0, 1, 0).toJOML(), false, 1.0, level.dimensionId
                )
                val shipCenterPos = BlockPos(
                    (ship.transform.positionInShip.x() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.y() - 0.5).roundToInt(),
                    (ship.transform.positionInShip.z() - 0.5).roundToInt()
                )

                // Extra height added to the hinge to keep the top ship slightly above the bottom ship
                val extraHeight = 0.0

                // The rotation we apply to different face values. The code below is set up to create Y-hinges by
                // default, and [rotationQuaternion] converts them to other types of hinges
                val rotationQuaternion: Quaterniondc
                when (state.getValue(FACING)) {
                    DOWN -> {
                        rotationQuaternion = Quaterniond(AxisAngle4d(Math.PI, Vector3d(1.0, 0.0, 0.0)))
                    }
                    NORTH -> {
                        rotationQuaternion = Quaterniond(AxisAngle4d(Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
                    }
                    EAST -> {
                        rotationQuaternion = Quaterniond(AxisAngle4d(0.5 * Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
                    }
                    SOUTH -> {
                        rotationQuaternion = Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0))).normalize()
                    }
                    WEST -> {
                        rotationQuaternion = Quaterniond(AxisAngle4d(1.5 * Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
                    }
                    UP -> {
                        // Do nothing
                        rotationQuaternion = Quaterniond()
                    }
                    else -> {
                        // This should be impossible, but have this here just in case
                        rotationQuaternion = Quaterniond()
                    }
                }

                // The positions the hinge attaches relative to the center of mass
                val attachmentOffset0: Vector3dc = rotationQuaternion.transform(Vector3d(0.0, 0.5 + extraHeight, 0.0))
                val attachmentOffset1: Vector3dc = rotationQuaternion.transform(Vector3d(0.0, -0.5, 0.0))

                val attachmentLocalPos0: Vector3dc = Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5).add(attachmentOffset0)
                val attachmentLocalPos1: Vector3dc =
                    Vector3d(shipCenterPos.x + 0.5, shipCenterPos.y + 0.5, shipCenterPos.z + 0.5).add(attachmentOffset1)

                // Move [ship] if we are on a ship
                if (shipThisIsIn != null) {
                    // Put the new ship where the old ship is
                    val newPos = shipThisIsIn.transform.shipToWorld.transformPosition(attachmentLocalPos0, Vector3d())
                    newPos.sub(shipThisIsIn.transform.shipToWorldRotation.transform(attachmentOffset1, Vector3d()))
                    val newKinematics = BodyKinematicsFactory.create(
                        shipThisIsIn.velocity,
                        shipThisIsIn.angularVelocity,
                        newPos,
                        shipThisIsIn.transform.shipToWorldRotation, // Copy source ship rotation
                        ship.transform.shipToWorldScaling,
                        ship.transform.positionInShip,
                    )
                    // Update the ship transform
                    (ship as ShipDataCommon).kinematics = newKinematics
                } else {
                    val newPos = Vector3d(attachmentLocalPos0)
                    newPos.sub(attachmentOffset1)
                    val newKinematics = BodyKinematicsFactory.create(
                        ship.velocity,
                        ship.angularVelocity,
                        newPos,
                        ship.transform.shipToWorldRotation,
                        ship.transform.shipToWorldScaling,
                        ship.transform.positionInShip,
                    )
                    // Update the ship transform
                    (ship as ShipDataCommon).kinematics = newKinematics
                }

                level.setBlockAndUpdate(shipCenterPos, Blocks.IRON_BLOCK.defaultBlockState())
                blockEntity.get().otherHingePos = shipCenterPos

                val shipId0 = shipThisIsIn?.id ?: level.shipObjectWorld.dimensionToGroundBodyIdImmutable[level.dimensionId]!!
                val shipId1 = ship.id

                // Attachment constraint
                // run {
                //     // I don't recommend setting compliance lower than 1e-10 because it tends to cause instability
                //     // TODO: Investigate why small compliance cause instability
                //     val attachmentCompliance = 1e-10
                //     val attachmentMaxForce = 1e10
                //     val attachmentFixedDistance = 0.0
                //     val attachmentConstraint = VSRevoluteJoint(
                //         shipId0, shipId1, attachmentCompliance, attachmentLocalPos0, attachmentLocalPos1,
                //         attachmentMaxForce, attachmentFixedDistance
                //     )
                //     blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(attachmentConstraint)
                // }

                // Hinge constraints will attempt to align the X-axes of both bodies, so to align the Y axis we
                // apply this rotation to the X-axis
                val hingeOrientation = rotationQuaternion.mul(Quaterniond(AxisAngle4d(Math.toRadians(90.0), 0.0, 0.0, 1.0)), Quaterniond()).normalize()

                // Hinge orientation constraint
                run {
                    // I don't recommend setting compliance lower than 1e-10 because it tends to cause instability
                    val hingeOrientationCompliance = 1e-10
                    val attachmentMaxForce = 1e10
                    val hingeMaxTorque = 1e10
                    val hingeConstraint = VSRevoluteJoint(
                        shipId0, VSJointPose(attachmentLocalPos0, hingeOrientation), shipId1, VSJointPose(attachmentLocalPos1, hingeOrientation),
                        VSJointMaxForceTorque(attachmentMaxForce.toFloat(), hingeMaxTorque.toFloat())
                    )
                    blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(hingeConstraint)
                }

                // Add position damping to make the hinge more stable
                // val posDampingConstraint = VSPosDampingConstraint(shipId0, shipId1, 1e-10, attachmentLocalPos0, attachmentLocalPos1, 1e10, 1e-2)
                // blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(posDampingConstraint)

                // Add perpendicular rotation damping to make the hinge more stable
                // val perpendicularRotDampingConstraint = VSRotDampingConstraint(shipId0, shipId1, 1e-10, hingeOrientation, hingeOrientation, 1e10, 1e-2, ALL_AXES)
                // blockEntity.get().constraintId = level.shipObjectWorld.createNewConstraint(perpendicularRotDampingConstraint)

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
