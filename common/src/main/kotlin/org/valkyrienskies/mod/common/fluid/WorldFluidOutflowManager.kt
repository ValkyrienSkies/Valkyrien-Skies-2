package org.valkyrienskies.mod.common.fluid

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import org.joml.Vector3d
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

internal enum class FluidParcelAction {
    FALL,
    MERGE,
    SETTLE,
    DISCARD,
}

internal fun chooseFluidParcelAction(
    currentReplaceable: Boolean,
    currentIsSameSource: Boolean,
    belowReplaceable: Boolean,
    belowIsSameSource: Boolean,
): FluidParcelAction = when {
    currentIsSameSource -> FluidParcelAction.MERGE
    !currentReplaceable -> FluidParcelAction.DISCARD
    belowIsSameSource -> FluidParcelAction.MERGE
    belowReplaceable -> FluidParcelAction.FALL
    else -> FluidParcelAction.SETTLE
}

internal fun fillAmountToVanillaLevel(fillAmount: Int): Int =
    ((fillAmount.coerceIn(1, FULL_VOXEL_FILL) * 8 + FULL_VOXEL_FILL - 1) / FULL_VOXEL_FILL)
        .coerceIn(1, 8)

internal fun fluidParcelTickDisplacement(velocity: Vector3d): Vector3d {
    if (!velocity.x.isFinite() || !velocity.y.isFinite() || !velocity.z.isFinite()) {
        velocity.zero()
    }
    velocity.y += FLUID_PARCEL_GRAVITY * FLUID_PARCEL_TIME_STEP
    val speed = velocity.length()
    if (speed > MAX_FLUID_PARCEL_SPEED) {
        velocity.mul(MAX_FLUID_PARCEL_SPEED / speed)
    }
    return Vector3d(velocity).mul(FLUID_PARCEL_TIME_STEP)
}

internal fun fluidParcelMotionSubsteps(displacement: Vector3d): Int {
    val maximumComponent = max(abs(displacement.x), max(abs(displacement.y), abs(displacement.z)))
    return ceil(maximumComponent / MAX_FLUID_PARCEL_SUBSTEP_DISTANCE)
        .toInt()
        .coerceIn(1, MAX_FLUID_PARCEL_SUBSTEPS)
}

class WorldFluidOutflowManager @JvmOverloads constructor(
    private val maxActiveParcels: Int = DEFAULT_MAX_ACTIVE_PARCELS,
    private val maxParcelsPerTick: Int = DEFAULT_MAX_PARCELS_PER_TICK,
) {
    private data class Parcel(
        val dimensionId: String,
        val worldPosition: Vector3d,
        val velocity: Vector3d,
        val fluid: FlowingFluid,
        val fillAmount: Int,
        var visualPos: BlockPos? = null,
        var visualRecipients: Set<UUID> = emptySet(),
    ) {
        fun blockPosition(): BlockPos =
            BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z)
    }

    private enum class ParcelMotionResult {
        MOVED,
        BLOCKED,
        MERGED,
        RETRY_LATER,
    }

    private val parcels = ArrayDeque<Parcel>()

    fun enqueue(
        level: ServerLevel?,
        outflow: VsiFluidOutflow,
    ): VsiFluidOutflowResult {
        if (level == null) {
            return VsiFluidOutflowResult.RETRY_LATER
        }
        val fluid = MassDatapackResolver.getFlowingFluid(outflow.fluidId)
            ?: return VsiFluidOutflowResult.UNSUPPORTED_FLUID
        if (parcels.size >= maxActiveParcels.coerceAtLeast(1)) {
            return VsiFluidOutflowResult.RETRY_LATER
        }

        parcels.addLast(
            Parcel(
                dimensionId = outflow.dimensionId,
                worldPosition = Vector3d(
                    outflow.worldPositionX,
                    outflow.worldPositionY,
                    outflow.worldPositionZ,
                ),
                velocity = Vector3d(
                    outflow.worldVelocityX,
                    outflow.worldVelocityY,
                    outflow.worldVelocityZ,
                ),
                fluid = fluid,
                fillAmount = outflow.fillAmount.coerceAtLeast(1),
            )
        )
        return VsiFluidOutflowResult.APPLIED
    }

    fun tick(levels: Map<String, ServerLevel>) {
        repeat(minOf(parcels.size, maxParcelsPerTick.coerceAtLeast(1))) {
            val parcel = parcels.removeFirst()
            val level = levels[parcel.dimensionId]
            if (level == null) {
                parcels.addLast(parcel)
                return@repeat
            }

            restoreVisual(level, parcel)
            if (parcel.blockPosition().y < level.minBuildHeight) {
                return@repeat
            }

            when (advanceParcel(level, parcel)) {
                ParcelMotionResult.MERGED -> return@repeat
                ParcelMotionResult.RETRY_LATER -> {
                    parcels.addLast(parcel)
                    return@repeat
                }
                ParcelMotionResult.MOVED,
                ParcelMotionResult.BLOCKED -> Unit
            }

            val currentPos = parcel.blockPosition()
            val belowPos = currentPos.below()
            if (!level.hasChunkAt(currentPos) || !level.hasChunkAt(belowPos)) {
                parcels.addLast(parcel)
                return@repeat
            }

            val currentState = level.getBlockState(currentPos)
            val belowState = level.getBlockState(belowPos)
            val currentSameFluid = parcel.fluid.isSame(currentState.fluidState.type)
            val belowSameFluid = parcel.fluid.isSame(belowState.fluidState.type)
            when (
                chooseFluidParcelAction(
                    currentReplaceable =
                        currentSameFluid || (currentState.fluidState.isEmpty && currentState.canBeReplaced()),
                    currentIsSameSource = currentSameFluid && currentState.fluidState.isSource,
                    belowReplaceable =
                        belowState.fluidState.isEmpty && belowState.canBeReplaced(),
                    belowIsSameSource = belowSameFluid && belowState.fluidState.isSource,
                )
            ) {
                FluidParcelAction.FALL -> {
                    showVisual(level, parcel)
                    parcels.addLast(parcel)
                }

                FluidParcelAction.MERGE,
                FluidParcelAction.DISCARD -> Unit

                FluidParcelAction.SETTLE -> {
                    if (parcel.velocity.y > 0.0) {
                        showVisual(level, parcel)
                        parcels.addLast(parcel)
                        return@repeat
                    }
                    val fluidState =
                        if (parcel.fillAmount >= FULL_VOXEL_FILL) {
                            parcel.fluid.getSource(false)
                        } else {
                            parcel.fluid.getFlowing(
                                fillAmountToVanillaLevel(parcel.fillAmount).coerceAtMost(7),
                                false,
                            )
                        }
                    if (level.setBlock(currentPos, fluidState.createLegacyBlock(), Block.UPDATE_ALL)) {
                        if (parcel.fillAmount < FULL_VOXEL_FILL) {
                            level.scheduleTick(
                                currentPos,
                                parcel.fluid,
                                parcel.fluid.getTickDelay(level),
                            )
                        }
                    } else {
                        parcels.addLast(parcel)
                    }
                }
            }
        }
    }

    private fun advanceParcel(
        level: ServerLevel,
        parcel: Parcel,
    ): ParcelMotionResult {
        val start = Vector3d(parcel.worldPosition)
        val startVelocity = Vector3d(parcel.velocity)
        val displacement = fluidParcelTickDisplacement(parcel.velocity)
        val substeps = fluidParcelMotionSubsteps(displacement)
        var lastFreePosition = Vector3d(start)
        var lastBlockPos = parcel.blockPosition()

        for (step in 1..substeps) {
            val fraction = step.toDouble() / substeps.toDouble()
            val samplePosition = Vector3d(displacement).mul(fraction).add(start)
            val sampleBlockPos = BlockPos.containing(
                samplePosition.x,
                samplePosition.y,
                samplePosition.z,
            )
            if (sampleBlockPos == lastBlockPos) {
                lastFreePosition.set(samplePosition)
                continue
            }
            if (!level.hasChunkAt(sampleBlockPos)) {
                parcel.velocity.set(startVelocity)
                return ParcelMotionResult.RETRY_LATER
            }

            val sampleState = level.getBlockState(sampleBlockPos)
            val sameFluid = parcel.fluid.isSame(sampleState.fluidState.type)
            if (sameFluid && sampleState.fluidState.isSource) {
                return ParcelMotionResult.MERGED
            }
            val replaceable =
                sameFluid || (sampleState.fluidState.isEmpty && sampleState.canBeReplaced())
            if (!replaceable) {
                parcel.worldPosition.set(lastFreePosition)
                parcel.velocity.x = 0.0
                parcel.velocity.z = 0.0
                if (parcel.velocity.y > 0.0) {
                    parcel.velocity.y = 0.0
                }
                return ParcelMotionResult.BLOCKED
            }

            lastFreePosition.set(samplePosition)
            lastBlockPos = sampleBlockPos
        }

        parcel.worldPosition.add(displacement)
        return ParcelMotionResult.MOVED
    }

    fun clear(levels: Map<String, ServerLevel>) {
        parcels.forEach { parcel ->
            levels[parcel.dimensionId]?.let { restoreVisual(it, parcel) }
        }
        parcels.clear()
    }

    internal fun activeParcelCount(): Int = parcels.size

    private fun showVisual(level: ServerLevel, parcel: Parcel) {
        val pos = parcel.blockPosition()
        val visualLevel = fillAmountToVanillaLevel(parcel.fillAmount).coerceAtMost(7)
        sendBlockUpdate(
            pos,
            parcel.fluid.getFlowing(visualLevel, true).createLegacyBlock(),
            level.players().filter {
                it.distanceToSqr(
                    pos.x + 0.5,
                    pos.y + 0.5,
                    pos.z + 0.5,
                ) <= VISUAL_RANGE_SQUARED
            },
        ).also { parcel.visualRecipients = it }
        parcel.visualPos = pos
    }

    private fun restoreVisual(level: ServerLevel, parcel: Parcel) {
        val visualPos = parcel.visualPos ?: return
        if (level.hasChunkAt(visualPos)) {
            val recipients = level.players().filter { it.uuid in parcel.visualRecipients }
            sendBlockUpdate(visualPos, level.getBlockState(visualPos), recipients)
        }
        parcel.visualPos = null
        parcel.visualRecipients = emptySet()
    }

    private fun sendBlockUpdate(
        pos: BlockPos,
        state: BlockState,
        recipients: Iterable<ServerPlayer>,
    ): Set<UUID> {
        val packet = ClientboundBlockUpdatePacket(pos, state)
        val recipientIds = HashSet<UUID>()
        recipients.forEach { player ->
            player.connection.send(packet)
            recipientIds += player.uuid
        }
        return recipientIds
    }

    private companion object {
        const val DEFAULT_MAX_ACTIVE_PARCELS = 4096
        const val DEFAULT_MAX_PARCELS_PER_TICK = 256
        const val VISUAL_RANGE_SQUARED = 128.0 * 128.0
    }
}

private const val FULL_VOXEL_FILL = 255
private const val FLUID_PARCEL_TIME_STEP = 1.0 / 20.0
private const val FLUID_PARCEL_GRAVITY = -9.81
private const val MAX_FLUID_PARCEL_SPEED = 64.0
private const val MAX_FLUID_PARCEL_SUBSTEP_DISTANCE = 0.45
private const val MAX_FLUID_PARCEL_SUBSTEPS = 32
