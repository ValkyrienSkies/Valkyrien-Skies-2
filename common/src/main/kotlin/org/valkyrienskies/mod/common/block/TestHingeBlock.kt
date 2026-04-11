package org.valkyrienskies.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.DOWN
import net.minecraft.core.Direction.EAST
import net.minecraft.core.Direction.NORTH
import net.minecraft.core.Direction.SOUTH
import net.minecraft.core.Direction.UP
import net.minecraft.core.Direction.WEST
import net.minecraft.nbt.CompoundTag
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
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.AxisAngle4d
import org.joml.Quaterniond
import org.joml.Vector3d
import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.core.internal.joints.VSRevoluteJoint
import org.valkyrienskies.core.internal.ships.VsiShip
import org.valkyrienskies.mod.api.vsApi
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ICopyableBlock
import org.valkyrienskies.mod.common.blockentity.TestHingeBlockEntity
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.toJOML
import org.valkyrienskies.mod.util.getVector3d
import org.valkyrienskies.mod.util.putVector3d
import kotlin.math.roundToInt

object TestHingeBlock :
    DirectionalBlock(
        Properties.of().strength(10.0f, 1200.0f).sound(SoundType.METAL)
    ), EntityBlock, ICopyableBlock {

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
    ): VoxelShape = when (state.getValue(FACING)) {
            DOWN -> DOWN_AABB
            NORTH -> NORTH_AABB
            SOUTH -> SOUTH_AABB
            WEST -> WEST_AABB
            EAST -> EAST_AABB
            UP -> UP_AABB
            else -> {
                // This should be impossible, but have this here just in case
                 UP_AABB
            }
        }

    @OptIn(GameTickOnly::class)
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

        if (!blockEntity.isPresent) {return InteractionResult.CONSUME }
        if (blockEntity.get().otherHingePos != null) { return InteractionResult.CONSUME }

        // The ship that owns [pos]
        val shipThisIsIn = level.getShipManagingPos(pos)

        // Create an empty ship
        val ship = (level as ServerLevel).shipObjectWorld.createNewShipAtBlock(
            pos.offset(0, 1, 0).toJOML(), false, 1.0, level.dimensionId
        )
        // Mark as recently spawned so players aren't frozen while chunks load
        org.valkyrienskies.mod.common.util.EntityShipCollisionUtils.markShipAsRecentlySpawned(
            ship.id, level.server.tickCount.toLong()
        )
        val shipCenterPos = BlockPos(
            (ship.transform.positionInShip.x() - 0.5).roundToInt(),
            (ship.transform.positionInShip.y() - 0.5).roundToInt(),
            (ship.transform.positionInShip.z() - 0.5).roundToInt()
        )

        // The rotation we apply to different face values. The code below is set up to create Y-hinges by
        // default, and [rotationQuaternion] converts them to other types of hinges
        val rotationQuaternion = when (state.getValue(FACING)) {
            DOWN ->  Quaterniond(AxisAngle4d(Math.PI, Vector3d(1.0, 0.0, 0.0)))
            NORTH -> Quaterniond(AxisAngle4d(Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
            EAST ->  Quaterniond(AxisAngle4d(0.5 * Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
            SOUTH -> Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0))).normalize()
            WEST ->  Quaterniond(AxisAngle4d(1.5 * Math.PI, Vector3d(0.0, 1.0, 0.0))).mul(Quaterniond(AxisAngle4d(Math.PI / 2.0, Vector3d(1.0, 0.0, 0.0)))).normalize()
            UP -> Quaterniond()
        }

        // Extra height added to the hinge to keep the top ship slightly above the bottom ship
        val extraHeight = 0.0
        // The positions the hinge attaches relative to the center of mass
        val attachmentOffset0: Vector3dc = rotationQuaternion.transform(Vector3d(0.0, 0.5 + extraHeight, 0.0))
        val attachmentOffset1: Vector3dc = rotationQuaternion.transform(Vector3d(0.0, -0.5, 0.0))

        val attachmentLocalPos0: Vector3dc = Vector3d(pos.x.toDouble() + 0.5, pos.y.toDouble() + 0.5, pos.z.toDouble() + 0.5).add(attachmentOffset0)
        val attachmentLocalPos1: Vector3dc =
            Vector3d(shipCenterPos.x.toDouble() + 0.5, shipCenterPos.y.toDouble() + 0.5, shipCenterPos.z.toDouble() + 0.5).add(attachmentOffset1)

        // Move [ship] if we are on a ship
        if (shipThisIsIn != null) {
            // Put the new ship where the old ship is
            val newPos = shipThisIsIn.transform.shipToWorld.transformPosition(attachmentLocalPos0, Vector3d())
            newPos.sub(shipThisIsIn.transform.shipToWorldRotation.transform(attachmentOffset1, Vector3d()))
            val newKinematics = vsApi.newBodyKinematics(
                shipThisIsIn.velocity,
                shipThisIsIn.angularVelocity,
                newPos,
                shipThisIsIn.transform.shipToWorldRotation, // Copy source ship rotation
                ship.transform.shipToWorldScaling,
                ship.transform.positionInShip,
            )
            // Update the ship transform
            (ship as VsiShip).unsafeSetKinematics(newKinematics)
        } else {
            val newPos = Vector3d(attachmentLocalPos0)
            newPos.sub(attachmentOffset1)
            val newKinematics = vsApi.newBodyKinematics(
                ship.velocity,
                ship.angularVelocity,
                newPos,
                ship.transform.shipToWorldRotation,
                ship.transform.shipToWorldScaling,
                ship.transform.positionInShip,
            )
            // Update the ship transform
            (ship as VsiShip).unsafeSetKinematics(newKinematics)
        }

        level.setBlockAndUpdate(shipCenterPos, Blocks.IRON_BLOCK.defaultBlockState())
        blockEntity.get().otherHingePos = shipCenterPos

        val shipId0 = shipThisIsIn?.id
        val shipId1 = ship.id

        // Hinge constraints will attempt to align the X-axes of both bodies, so to align the Y axis we
        // apply this rotation to the X-axis
        val hingeOrientation = rotationQuaternion.mul(Quaterniond(AxisAngle4d(Math.toRadians(90.0), 0.0, 0.0, 1.0)), Quaterniond()).normalize()

        blockEntity.get().hingeConstraint = VSRevoluteJoint(
            shipId0, VSJointPose(attachmentLocalPos0, hingeOrientation),
            shipId1, VSJointPose(attachmentLocalPos1, hingeOrientation),
            maxForceTorque = null, driveFreeSpin = true
        )
        ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId).addJoint(blockEntity.get().hingeConstraint!!, delay = 4) { t ->
            blockEntity.get().constraintId = t
        }

        return InteractionResult.CONSUME
    }

    override fun onRemove(
        blockState: BlockState, level: Level, blockPos: BlockPos, blockState2: BlockState, bl: Boolean
    ) {
        if (level is ServerLevel) run {
            val be = level.getBlockEntity(blockPos) as? TestHingeBlockEntity ?: return@run
            ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId).removeJoint(be.constraintId ?: return@run)
        }

        super.onRemove(blockState, level, blockPos, blockState2, bl)
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState): TestHingeBlockEntity = TestHingeBlockEntity(blockPos, blockState)

    override fun onCopy(
        level: ServerLevel, pos: BlockPos,
        state: BlockState,
        be: BlockEntity?,
        shipsBeingCopied: List<ServerShip>,
        centerPositions: Map<Long, Vector3dc>
    ): CompoundTag? = null

    override fun onPaste(
        level: ServerLevel, pos: BlockPos,
        state: BlockState,
        oldShipIdToNewId: Map<Long, Long>,
        centerPositions: Map<Long, Pair<Vector3dc, Vector3dc>>,
        tag: CompoundTag?
    ): CompoundTag? {
        val tag = tag ?: return null

        tag.putVector3d("pos0", centerPositions[tag.getLong("shipId0")]!!.let { (old, new) -> tag.getVector3d("pos0")!!.sub(old).add(new) })
        tag.putVector3d("pos1", centerPositions[tag.getLong("shipId1")]!!.let { (old, new) -> tag.getVector3d("pos1")!!.sub(old).add(new) })
        tag.putLong("shipId0", oldShipIdToNewId[tag.getLong("shipId0")]!!)
        tag.putLong("shipId1", oldShipIdToNewId[tag.getLong("shipId1")]!!)

        return tag
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = BlockEntityTicker { _, _, _, blockEntity ->
        if (level.isClientSide) return@BlockEntityTicker
        if (blockEntity is TestHingeBlockEntity) {
            blockEntity.tick()
        }
    }
}
