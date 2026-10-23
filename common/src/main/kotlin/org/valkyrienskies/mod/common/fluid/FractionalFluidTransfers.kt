package org.valkyrienskies.mod.common.fluid

import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.material.FlowingFluid
import org.joml.Vector3d
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Amount
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Position
import java.math.BigDecimal
import java.math.RoundingMode

internal class FractionalFluidTransfers(private val sources: FractionalFluidSources) {
    data class Move(val from: Position, val to: Position?, val sourceVolume: BigDecimal,
        val destinationVolume: BigDecimal, val fallbackDimension: String, val worldPosition: Vector3d,
        val velocity: Vector3d, val copy: Boolean = false)

    private class Pending(val move: Move, val original: Amount) {
        val sourceZero = amount(move.from, original.fluid, BigDecimal.ZERO)
        val destinationZero = move.to?.let { amount(it, original.fluid, BigDecimal.ZERO) }
        val worldUnits = checked(original.units * move.sourceVolume)
        val destinationUnits = if (move.to == null) BigDecimal.ZERO else minOf(FULL,
            worldUnits.divide(move.destinationVolume, 325 - maxOf(0, move.destinationVolume.scale()), RoundingMode.DOWN))
                .stripTrailingZeros()
        val destination = move.to?.let { if (destinationUnits.compareTo(FULL) == 0) null
            else amount(it, original.fluid, destinationUnits) }
        val spill = checked(worldUnits - destinationUnits * move.destinationVolume)
        val budget = 32768L + 8L * (move.from.dimension.length + (move.to?.dimension?.length ?: 0) +
            move.fallbackDimension.length + original.fluid.length) + sourceZero.bytes + (destinationZero?.bytes ?: 0) +
            maxOf(0, original.bytes - sourceZero.bytes) + maxOf(0, (destination?.bytes ?: 0) - (destinationZero?.bytes ?: 0))
        var active = false
        var committed = false
        var metadataBytes = 0L
    }

    private var pending = LinkedHashMap<Position, Pending>()
    private var locked = HashMap<Position, Pending>()
    fun isLocked(position: Position) = locked.containsKey(position)
    fun hasActiveTransfers() = pending.values.any { it.active }

    fun begin(moves: Sequence<Move>, levels: Map<String, ServerLevel>, parcels: WorldFluidOutflowManager): FractionalFluidTransferScope? {
        val staged = ArrayList<Pending>()
        val endpoints = HashSet<Position>()
        var reserved = 0L
        try {
            for (move in moves) {
                val original = sources.amounts[move.from] ?: continue
                if (move.from == move.to) continue
                require(!isLocked(move.from) && endpoints.add(move.from)) { "Fluid source already moving" }
                move.to?.let {
                    require(!isLocked(it) && !sources.amounts.containsKey(it) && endpoints.add(it)) { "Fluid destination already owned" }
                    val level = requireNotNull(levels[it.dimension])
                    require(level.getBlockState(it.blockPos()).fluidState.isEmpty) { "Fractional fluid destination already contains fluid" }
                }
                validate(move)
                val transfer = Pending(move, original)
                val extra = transfer.budget - original.bytes
                reserve(extra)
                reserved += extra
                staged.add(transfer)
            }
            for (transfer in staged) {
                val move = transfer.move
                sources.amounts[move.from] = transfer.sourceZero
                move.to?.let { sources.amounts[it] = requireNotNull(transfer.destinationZero) }
                transfer.metadataBytes = transfer.budget - transfer.sourceZero.bytes - (transfer.destinationZero?.bytes ?: 0)
                transfer.active = true
                pending[move.from] = transfer
                locked[move.from] = transfer
                move.to?.let { locked[it] = transfer }
            }
        } catch (error: Throwable) {
            for (transfer in staged) {
                sources.amounts[transfer.move.from] = transfer.original
                transfer.move.to?.let { if (sources.amounts[it] === transfer.destinationZero) sources.amounts.remove(it) }
                pending.remove(transfer.move.from)
                locked.remove(transfer.move.from)
                transfer.move.to?.let { locked.remove(it) }
            }
            sources.retainedBytes -= reserved
            throw error
        }
        if (staged.isEmpty()) return null
        return FractionalFluidTransferScope {
            staged.forEach { it.active = false }
            recover(levels, parcels)
        }
    }

    fun recover(levels: Map<String, ServerLevel>, parcels: WorldFluidOutflowManager) {
        for (transfer in pending.values.take(64)) {
            val move = transfer.move
            pending.remove(move.from)
            pending[move.from] = transfer
            if (transfer.active) continue
            val sourceLevel = levels[move.from.dimension] ?: continue
            val sourcePos = move.from.blockPos()
            val sourceLoaded = sourceLevel.hasChunkAt(sourcePos)
            val targetLevel = if (move.to == null) null else levels[move.to.dimension] ?: continue
            val targetPos = move.to?.blockPos()
            if (move.to != null && (!sourceLoaded || !requireNotNull(targetLevel).hasChunkAt(requireNotNull(targetPos)))) continue
            if (!transfer.committed) {
                val sourcePresent = sourceLoaded && FractionalFluidSources.fluidName(sourceLevel.getBlockState(sourcePos)) == transfer.original.fluid
                val targetPresent = targetLevel != null && FractionalFluidSources.fluidName(targetLevel.getBlockState(targetPos!!)) == transfer.original.fluid
                val credit = when {
                    targetPresent -> transfer.spill
                    move.to != null && sourcePresent -> BigDecimal.ZERO
                    else -> transfer.worldUnits
                }
                if (credit.signum() > 0) {
                    if (levels[move.fallbackDimension] == null || parcels.enqueueExact(move.fallbackDimension,
                            transfer.original.fluid, move.worldPosition, move.velocity, credit) != VsiFluidOutflowResult.APPLIED) continue
                }
                val sourceValue = if (sourcePresent && ((targetPresent && move.copy) || (!targetPresent && move.to != null)))
                    transfer.original else if (sourcePresent || !sourceLoaded) transfer.sourceZero else null
                replace(move.from, sourceValue)
                move.to?.let { replace(it, if (targetPresent) transfer.destination else null) }
                transfer.metadataBytes = transfer.budget - mapBytes(transfer)
                transfer.committed = true
            }
            if (sourceLoaded) publish(sourceLevel, move.from)
            if (targetLevel != null) publish(targetLevel, requireNotNull(move.to))
            if (targetLevel != null && transfer.destinationUnits.compareTo(FULL) == 0) {
                val state = targetLevel.getBlockState(requireNotNull(targetPos))
                val fluid = state.fluidState.type as? FlowingFluid
                if (fluid != null && FractionalFluidSources.fluidName(state) == transfer.original.fluid)
                    targetLevel.scheduleTick(targetPos, fluid, fluid.getTickDelay(targetLevel))
            }
            pending.remove(move.from)
            locked.remove(move.from)
            move.to?.let { locked.remove(it) }
            sources.retainedBytes -= transfer.metadataBytes
            if (sourceLoaded) sources.reconcileFluid(move.from.dimension, move.from.x, move.from.y, move.from.z,
                FractionalFluidSources.fluidName(sourceLevel.getBlockState(sourcePos)))
            if (targetLevel != null) sources.reconcileFluid(move.to!!.dimension, move.to.x, move.to.y, move.to.z,
                FractionalFluidSources.fluidName(targetLevel.getBlockState(targetPos!!)))
        }
    }

    private fun replace(position: Position, amount: Amount?) {
        if (amount == null) sources.amounts.remove(position) else sources.amounts[position] = amount
    }

    private fun publish(level: ServerLevel, position: Position) {
        val state = level.getBlockState(position.blockPos())
        val fluid = FractionalFluidSources.fluidName(state)
        val value = sources.amounts[position]?.takeIf { it.fluid == fluid }?.units?.toInt() ?: 255
        BlockStateInfo.onSourceAmountChanged(level, position.blockPos(), state, value)
    }

    private fun mapBytes(transfer: Pending) = (sources.amounts[transfer.move.from]?.bytes ?: 0) +
        (transfer.move.to?.let { sources.amounts[it]?.bytes } ?: 0)

    private fun reserve(bytes: Long) {
        if (bytes > sources.maximumBytes - sources.retainedBytes) throw FractionalFluidSources.StorageCapacityException(
            sources.retainedBytes, bytes, sources.maximumBytes)
        sources.retainedBytes += bytes
    }

    fun save(): ListTag = ListTag().also { list ->
        for (transfer in pending.values) list.add(CompoundTag().apply {
            val move = transfer.move
            put("from", positionTag(move.from))
            move.to?.let { put("to", positionTag(it)) }
            putString("fluid", transfer.original.fluid)
            putString("units", transfer.original.units.toString())
            putString("source_volume", move.sourceVolume.toString())
            putString("destination_volume", move.destinationVolume.toString())
            putString("fallback_dimension", move.fallbackDimension)
            putDouble("x", move.worldPosition.x); putDouble("y", move.worldPosition.y); putDouble("z", move.worldPosition.z)
            putDouble("vx", move.velocity.x); putDouble("vy", move.velocity.y); putDouble("vz", move.velocity.z)
            putBoolean("copy", move.copy); putBoolean("committed", transfer.committed)
        })
    }

    fun load(list: ListTag) {
        require(list.isEmpty() || list.elementType == Tag.TAG_COMPOUND)
        require(list.size.toLong() <= sources.maximumBytes / 32768)
        for (index in 0 until list.size) {
            val tag = list.getCompound(index)
            val from = readPosition(tag, "from")
            val to = if (tag.contains("to")) readPosition(tag, "to") else null
            val fluid = text(tag, "fluid")
            val move = Move(from, to, decimal(tag, "source_volume"), decimal(tag, "destination_volume"),
                text(tag, "fallback_dimension"), vector(tag, "x", "y", "z"), vector(tag, "vx", "vy", "vz"),
                boolean(tag, "copy"))
            validate(move)
            require(from != to && !isLocked(from) && (to == null || !isLocked(to)))
            val units = decimal(tag, "units")
            require(units.signum() >= 0 && units < FULL)
            val transfer = Pending(move, amount(from, fluid, units))
            transfer.committed = boolean(tag, "committed")
            for (position in listOfNotNull(from, to)) {
                val value = sources.amounts[position]
                if (!transfer.committed) require(value?.fluid == fluid && value.units.signum() == 0)
                else require(value == null || value.fluid == fluid && (value.units.signum() == 0 ||
                    value.units.compareTo(if (position == from) units else transfer.destinationUnits) == 0))
            }
            transfer.metadataBytes = transfer.budget - mapBytes(transfer)
            reserve(transfer.metadataBytes)
            pending[from] = transfer
            locked[from] = transfer
            to?.let { locked[it] = transfer }
        }
    }

    fun adopt(other: FractionalFluidTransfers) { pending = other.pending; locked = other.locked }

    private companion object {
        val FULL = FractionalFluidSources.FULL_SOURCE
        val MAX_UNITS = BigDecimal.valueOf(Double.MAX_VALUE) * FULL
        fun Position.blockPos() = BlockPos(x, y, z)
        fun amount(position: Position, fluid: String, units: BigDecimal): Amount {
            val normalized = units.stripTrailingZeros()
            return Amount(fluid, normalized, 512L + 4L * (position.dimension.length + fluid.length + normalized.toString().length))
        }
        fun checked(value: BigDecimal): BigDecimal {
            require(value.signum() >= 0 && value <= MAX_UNITS && value.scale() in -308..325 && value.precision() <= 640)
            return value
        }
        fun validate(move: Move) {
            require(move.from.dimension.length in 1..4096 && (move.to == null || move.to.dimension.length in 1..4096))
            require(move.fallbackDimension.length in 1..4096 && (!move.copy || move.to != null))
            require(checked(move.sourceVolume).signum() > 0 && checked(move.destinationVolume).signum() > 0)
            for (vector in listOf(move.worldPosition, move.velocity)) require(vector.x.isFinite() && vector.y.isFinite() && vector.z.isFinite())
        }
        fun positionTag(position: Position) = CompoundTag().apply {
            putString("dimension", position.dimension); putIntArray("position", intArrayOf(position.x, position.y, position.z))
        }
        fun readPosition(tag: CompoundTag, key: String): Position {
            require(tag.contains(key, Tag.TAG_COMPOUND.toInt()))
            val value = tag.getCompound(key)
            require(value.contains("position", Tag.TAG_INT_ARRAY.toInt()))
            val coordinates = value.getIntArray("position"); require(coordinates.size == 3)
            return Position(text(value, "dimension"), coordinates[0], coordinates[1], coordinates[2])
        }
        fun text(tag: CompoundTag, key: String): String {
            require(tag.contains(key, Tag.TAG_STRING.toInt()))
            return tag.getString(key).also { require(it.length in 1..4096) }
        }
        fun decimal(tag: CompoundTag, key: String): BigDecimal {
            val value = text(tag, key); require(value.length <= 640)
            return checked(value.toBigDecimal())
        }
        fun boolean(tag: CompoundTag, key: String): Boolean {
            require(tag.contains(key, Tag.TAG_BYTE.toInt())); return tag.getBoolean(key)
        }
        fun vector(tag: CompoundTag, x: String, y: String, z: String): Vector3d {
            for (key in listOf(x, y, z)) require(tag.contains(key, Tag.TAG_DOUBLE.toInt()) && tag.getDouble(key).isFinite())
            return Vector3d(tag.getDouble(x), tag.getDouble(y), tag.getDouble(z))
        }
    }
}
