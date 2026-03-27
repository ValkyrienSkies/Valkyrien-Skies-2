package org.valkyrienskies.mod.common.air_pockets

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.BucketPickup
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.LiquidBlockContainer
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.Fluids
import org.valkyrienskies.mod.common.config.VSGameConfig
import kotlin.math.roundToInt

internal const val FLOOD_WRITE_SETBLOCK_FLAGS: Int = 3 // UPDATE_NEIGHBORS | UPDATE_CLIENTS

private const val FLOOD_PROGRESS_BASE_RATE = 0.01
private const val FLOOD_PROGRESS_PER_CONDUCTANCE_UNIT = 0.00125
private const val FLOOD_PROGRESS_MAX_RATE = 0.35

internal enum class FloodWriteEffectKind {
    NONE,
    SOURCE,
    WATERLOG,
    CONTAINER,
    BREAK_ON_FLOOD,
}

internal data class FloodWriteApplication(
    val applied: Boolean,
    val materialized: Boolean,
    val effect: FloodWriteEffectKind,
)

internal data class FloodProgressRateModel(
    val fluidTickDelay: Int,
    val frontierBudget: Int,
    val planeDeltaPerTick: Double,
)

internal fun floodCanonicalSource(fluid: Fluid): Fluid {
    return if (fluid is FlowingFluid) fluid.source else fluid
}

internal fun isWaterloggableFloodState(state: BlockState, floodFluid: Fluid): Boolean {
    return floodCanonicalSource(floodFluid) == Fluids.WATER && state.hasProperty(BlockStateProperties.WATERLOGGED)
}

internal fun isMaterializedFloodState(state: BlockState, floodFluid: Fluid): Boolean {
    val currentFluid = state.fluidState
    if (currentFluid.isEmpty) return false
    if (floodCanonicalSource(currentFluid.type) != floodCanonicalSource(floodFluid)) return false
    if (state.block is LiquidBlock) return currentFluid.isSource
    return isWaterloggableFloodState(state, floodFluid) && state.getValue(BlockStateProperties.WATERLOGGED)
}

internal fun computeFloodProgressRateModel(
    level: Level,
    floodFluid: Fluid,
    openingConductanceUnits: Int,
    openingCount: Int,
): FloodProgressRateModel {
    val fluidTickDelay = (floodCanonicalSource(floodFluid) as? FlowingFluid)?.getTickDelay(level)?.coerceAtLeast(1) ?: 1
    val conductanceUnits = openingConductanceUnits.coerceAtLeast(1)
    val normalizedOpenings = openingCount.coerceAtLeast(1)
    val floodRateMultiplier = VSGameConfig.COMMON.shipPocketFloodRateMultiplier.coerceIn(0.05, 5.0)

    val frontierBudget = (conductanceUnits.toDouble() * floodRateMultiplier).roundToInt()
        .coerceAtLeast(normalizedOpenings)
        .coerceIn(1, 64)

    val planeDeltaPerTick = ((FLOOD_PROGRESS_BASE_RATE +
        conductanceUnits.toDouble() * FLOOD_PROGRESS_PER_CONDUCTANCE_UNIT)
        .coerceAtMost(FLOOD_PROGRESS_MAX_RATE)) * floodRateMultiplier / fluidTickDelay.toDouble()

    return FloodProgressRateModel(
        fluidTickDelay = fluidTickDelay,
        frontierBudget = frontierBudget,
        planeDeltaPerTick = planeDeltaPerTick,
    )
}

private fun tryPlaceFloodFluidInContainer(
    level: ServerLevel,
    pos: BlockPos.MutableBlockPos,
    current: BlockState,
    floodFluid: Fluid,
): Boolean {
    val canonical = floodCanonicalSource(floodFluid)
    val flowing = canonical as? FlowingFluid ?: return false
    val block = current.block
    if (block !is LiquidBlockContainer) return false
    return try {
        if (!block.canPlaceLiquid(level, pos, current, canonical)) return false
        if (!block.placeLiquid(level, pos, current, flowing.source.defaultFluidState())) return false
        level.scheduleTick(pos, canonical, 1)
        true
    } catch (_: Throwable) {
        false
    }
}

private fun tryDrainFloodFluidFromContainer(
    level: ServerLevel,
    pos: BlockPos.MutableBlockPos,
    current: BlockState,
    floodFluid: Fluid,
): Boolean {
    val canonical = floodCanonicalSource(floodFluid)
    val currentFluid = current.fluidState
    if (currentFluid.isEmpty || floodCanonicalSource(currentFluid.type) != canonical) return false
    val block = current.block
    if (block !is BucketPickup) return false
    return try {
        val picked = block.pickupBlock(level, pos, current)
        if (picked.isEmpty) return false
        true
    } catch (_: Throwable) {
        false
    }
}

internal fun shouldBreakOnFloodState(
    level: Level,
    pos: BlockPos,
    current: BlockState,
    floodFluid: Fluid,
): Boolean {
    if (current.isAir) return false
    if (current.block is LiquidBlock) return false
    if (!current.fluidState.isEmpty) return false
    if (current.block is LiquidBlockContainer) return false
    if (isWaterloggableFloodState(current, floodFluid)) return false

    val block = current.block
    if (block is DoorBlock || block is TrapDoorBlock || block is FenceGateBlock) return false

    val destroySpeed = current.getDestroySpeed(level, pos)
    if (!destroySpeed.isFinite() || destroySpeed < 0.0f) return false
    if (current.canBeReplaced()) return true

    val collisionShape = current.getCollisionShape(level, pos)
    if (collisionShape.isEmpty) return true

    return !current.isCollisionShapeFullBlock(level, pos) && !current.canOcclude() && destroySpeed <= 1.0f
}

internal fun applyFloodBlockWrite(
    level: ServerLevel,
    pos: BlockPos.MutableBlockPos,
    current: BlockState,
    floodFluid: Fluid,
    toWater: Boolean,
    dropOnBreak: Boolean = true,
    setBlockFlags: Int = FLOOD_WRITE_SETBLOCK_FLAGS,
): FloodWriteApplication {
    val canonical = floodCanonicalSource(floodFluid)
    val currentFluid = current.fluidState
    val isFlowingFloodFluid =
        !currentFluid.isEmpty && floodCanonicalSource(currentFluid.type) == canonical && !currentFluid.isSource

    if (toWater) {
        if (isMaterializedFloodState(current, canonical)) {
            return FloodWriteApplication(
                applied = true,
                materialized = true,
                effect = if (current.block is LiquidBlock) {
                    FloodWriteEffectKind.SOURCE
                } else if (isWaterloggableFloodState(current, canonical)) {
                    FloodWriteEffectKind.WATERLOG
                } else {
                    FloodWriteEffectKind.CONTAINER
                },
            )
        }

        if (current.isAir || isFlowingFloodFluid) {
            level.setBlock(pos, canonical.defaultFluidState().createLegacyBlock(), setBlockFlags)
            level.scheduleTick(pos, canonical, 1)
            return FloodWriteApplication(true, true, FloodWriteEffectKind.SOURCE)
        }

        if (isWaterloggableFloodState(current, canonical)) {
            if (!current.getValue(BlockStateProperties.WATERLOGGED)) {
                level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, true), setBlockFlags)
                level.scheduleTick(pos, Fluids.WATER, 1)
            }
            return FloodWriteApplication(true, true, FloodWriteEffectKind.WATERLOG)
        }

        if (tryPlaceFloodFluidInContainer(level, pos, current, canonical)) {
            return FloodWriteApplication(true, true, FloodWriteEffectKind.CONTAINER)
        }

        if (shouldBreakOnFloodState(level, pos, current, canonical)) {
            val destroyed = level.destroyBlock(pos, dropOnBreak)
            val afterDestroy = level.getBlockState(pos)
            if (!destroyed && !afterDestroy.isAir) {
                return FloodWriteApplication(false, false, FloodWriteEffectKind.NONE)
            }

            level.setBlock(pos, canonical.defaultFluidState().createLegacyBlock(), setBlockFlags)
            level.scheduleTick(pos, canonical, 1)
            return FloodWriteApplication(true, true, FloodWriteEffectKind.BREAK_ON_FLOOD)
        }

        return FloodWriteApplication(false, false, FloodWriteEffectKind.NONE)
    }

    if (current.block is LiquidBlock &&
        !currentFluid.isEmpty &&
        floodCanonicalSource(currentFluid.type) == canonical
    ) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), setBlockFlags)
        return FloodWriteApplication(true, false, FloodWriteEffectKind.SOURCE)
    }

    if (isWaterloggableFloodState(current, canonical) &&
        current.getValue(BlockStateProperties.WATERLOGGED)
    ) {
        level.setBlock(pos, current.setValue(BlockStateProperties.WATERLOGGED, false), setBlockFlags)
        return FloodWriteApplication(true, false, FloodWriteEffectKind.WATERLOG)
    }

    if (tryDrainFloodFluidFromContainer(level, pos, current, canonical)) {
        return FloodWriteApplication(true, false, FloodWriteEffectKind.CONTAINER)
    }

    return FloodWriteApplication(false, false, FloodWriteEffectKind.NONE)
}
