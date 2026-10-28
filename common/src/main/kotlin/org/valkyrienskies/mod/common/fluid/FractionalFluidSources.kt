package org.valkyrienskies.mod.common.fluid

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.dimensionId
import java.math.BigDecimal
import java.util.TreeMap

class FractionalFluidSources(internal val maximumBytes: Long = 16L * 1024 * 1024) {
    internal data class Position(val dimension: String, val x: Int, val y: Int, val z: Int) : Comparable<Position> {
        override fun compareTo(other: Position): Int {
            var order = dimension.compareTo(other.dimension)
            if (order == 0) order = (x shr 4).compareTo(other.x shr 4)
            if (order == 0) order = (y shr 4).compareTo(other.y shr 4)
            if (order == 0) order = (z shr 4).compareTo(other.z shr 4)
            if (order == 0) order = x.compareTo(other.x)
            if (order == 0) order = y.compareTo(other.y)
            if (order == 0) order = z.compareTo(other.z)
            return order
        }
    }

    internal data class Amount(val fluid: String, val units: BigDecimal, val bytes: Long)
    internal var amounts = TreeMap<Position, Amount>()
    internal val transfers = FractionalFluidTransfers(this)
    var retainedBytes = 0L
        internal set
    val size: Int get() = amounts.size

    init { require(maximumBytes >= 0) }

    fun hasChunk(dimension: String, x: Int, y: Int, z: Int): Boolean {
        if (amounts.isEmpty() || x !in -134217728..134217727 || y !in -134217728..134217727 ||
            z !in -134217728..134217727) return false
        val next = amounts.ceilingKey(Position(dimension, x shl 4, y shl 4, z shl 4)) ?: return false
        return next.dimension == dimension && next.x shr 4 == x && next.y shr 4 == y && next.z shr 4 == z
    }

    fun amount(dimension: String, x: Int, y: Int, z: Int, fluid: String): BigDecimal? {
        if (amounts.isEmpty()) return null
        val position = Position(dimension, x, y, z)
        if (transfers.isUncommitted(position)) return BigDecimal.ZERO
        val value = amounts[position] ?: return null
        return value.units.takeIf { value.fluid == fluid }
    }

    fun sourceFillAmount(dimension: String, x: Int, y: Int, z: Int, fluid: String): Int =
        amount(dimension, x, y, z, fluid)?.toInt() ?: 255

    fun setAmount(dimension: String, x: Int, y: Int, z: Int, fluid: String, units: BigDecimal): Boolean {
        require(dimension.length in 1..4096 && fluid.length in 1..4096)
        require(units.signum() >= 0 && units <= FULL_SOURCE)
        require(units.scale() in -308..325 && units.precision() <= 640)
        val position = Position(dimension, x, y, z)
        if (transfers.isLocked(position)) return false
        val previous = amounts[position]
        val normalized = units.stripTrailingZeros()
        val bytes = 512L + 4L * (dimension.length + fluid.length + normalized.toString().length)
        val additional = bytes - (previous?.bytes ?: 0L)
        if (additional > maximumBytes - retainedBytes) return false
        amounts[position] = Amount(fluid, normalized, bytes)
        retainedBytes += additional
        return true
    }

    fun reconcileFluid(dimension: String, x: Int, y: Int, z: Int, fluid: String?) {
        if (amounts.isEmpty()) return
        val position = Position(dimension, x, y, z)
        if (transfers.isLocked(position)) return
        val previous = amounts[position] ?: return
        if (previous.fluid != fluid) {
            amounts.remove(position)
            retainedBytes -= previous.bytes
        }
    }

    internal class PreparedChange(private val applyChange: () -> Boolean, private val cancelChange: () -> Unit) {
        fun commit(): Boolean = applyChange()
        fun cancel() = cancelChange()
    }

    class StorageCapacityException(val retainedBytes: Long, val requestedBytes: Long, val limitBytes: Long) :
        IllegalStateException("Fractional fluid storage needs $requestedBytes additional bytes; $retainedBytes/$limitBytes retained")

    internal fun prepareChange(dimension: String, x: Int, y: Int, z: Int, fluid: String,
        before: BigDecimal, after: BigDecimal): PreparedChange? {
        require(dimension.length in 1..4096 && fluid.length in 1..4096)
        for (units in listOf(before, after)) {
            require(units.signum() >= 0 && units <= FULL_SOURCE)
            require(units.scale() in -308..325 && units.precision() <= 640)
        }
        val position = Position(dimension, x, y, z)
        if (transfers.isLocked(position)) return null
        val previous = amounts[position]
        if (previous != null && (previous.fluid != fluid || previous.units.compareTo(before) != 0)) return null
        val normalizedBefore = before.stripTrailingZeros()
        val normalizedAfter = after.stripTrailingZeros()
        fun bytes(units: BigDecimal) = 512L + 4L * (dimension.length + fluid.length + units.toString().length)
        val replacement = Amount(fluid, normalizedAfter, bytes(normalizedAfter))
        val reserved = Amount(fluid, normalizedBefore, maxOf(bytes(normalizedBefore), replacement.bytes))
        val additional = reserved.bytes - (previous?.bytes ?: 0L)
        if (additional > maximumBytes - retainedBytes) throw StorageCapacityException(retainedBytes, additional, maximumBytes)
        val change = PreparedChange({
            if (amounts[position] !== reserved) false else {
                amounts[position] = replacement
                retainedBytes += replacement.bytes - reserved.bytes
                true
            }
        }, {
            if (amounts[position] === reserved) {
                if (previous == null) amounts.remove(position) else amounts[position] = previous
                retainedBytes -= additional
            }
        })
        amounts[position] = reserved
        retainedBytes += additional
        return change
    }

    fun move(dimension: String, x: Int, y: Int, z: Int,
             destinationDimension: String, destinationX: Int, destinationY: Int, destinationZ: Int): Boolean {
        val source = Position(dimension, x, y, z)
        val previous = amounts[source] ?: return true
        val destination = Position(destinationDimension, destinationX, destinationY, destinationZ)
        if (source == destination) return true
        if (transfers.isLocked(source) || transfers.isLocked(destination)) return false
        if (amounts.containsKey(destination)) return false
        require(destinationDimension.length in 1..4096)
        val bytes = previous.bytes + 4L * (destinationDimension.length - dimension.length)
        if (bytes - previous.bytes > maximumBytes - retainedBytes) return false
        amounts[destination] = previous.copy(bytes = bytes)
        amounts.remove(source)
        retainedBytes += bytes - previous.bytes
        return true
    }

    fun recoverTransfers(levels: Map<String, ServerLevel>, parcels: WorldFluidOutflowManager) = transfers.recover(levels, parcels)

    fun clearChunk(dimension: String, x: Int, z: Int) {
        if (amounts.isEmpty() || x !in -134217728..134217727 || z !in -134217728..134217727) return
        val iterator = amounts.tailMap(Position(dimension, x shl 4, Int.MIN_VALUE, Int.MIN_VALUE)).entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.dimension != dimension || entry.key.x shr 4 != x) break
            if (entry.key.z shr 4 == z && !transfers.isLocked(entry.key)) {
                retainedBytes -= entry.value.bytes
                iterator.remove()
            }
        }
    }

    fun save(): CompoundTag = CompoundTag().apply {
        putInt("version", 4)
        put("sources", ListTag().also { sources ->
            for ((position, amount) in amounts) sources.add(CompoundTag().apply {
                putString("dimension", position.dimension)
                putIntArray("position", intArrayOf(position.x, position.y, position.z))
                putString("fluid", amount.fluid)
                putString("units", amount.units.toString())
            })
        })
        put("transfers", transfers.save())
    }

    fun load(tag: CompoundTag) {
        check(!transfers.hasActiveTransfers())
        require(tag.contains("version", Tag.TAG_INT.toInt()) && tag.getInt("version") in 1..4)
        require(tag.contains("sources", Tag.TAG_LIST.toInt()))
        val sources = tag.get("sources") as ListTag
        require(sources.isEmpty() || sources.elementType == Tag.TAG_COMPOUND)
        require(sources.size.toLong() <= maximumBytes / 512)
        val restored = FractionalFluidSources(maximumBytes)
        for (index in 0 until sources.size) {
            val entry = sources.getCompound(index)
            require(entry.contains("dimension", Tag.TAG_STRING.toInt()) && entry.contains("fluid", Tag.TAG_STRING.toInt()))
            require(entry.contains("position", Tag.TAG_INT_ARRAY.toInt()) && entry.contains("units", Tag.TAG_STRING.toInt()))
            val position = entry.getIntArray("position")
            require(position.size == 3)
            val dimension = entry.getString("dimension")
            val fluid = entry.getString("fluid")
            val encoded = entry.getString("units")
            require(encoded.length in 1..640)
            val units = encoded.toBigDecimal()
            require(units < FULL_SOURCE || tag.getInt("version") >= 4 && units.compareTo(FULL_SOURCE) == 0)
            require(!restored.amounts.containsKey(Position(dimension, position[0], position[1], position[2])))
            require(restored.setAmount(dimension, position[0], position[1], position[2], fluid, units)) {
                "Fractional fluid sources exceed their storage allowance"
            }
        }
        if (tag.getInt("version") >= 2) {
            require(tag.contains("transfers", Tag.TAG_LIST.toInt()))
            restored.transfers.load(tag.get("transfers") as ListTag, tag.getInt("version"))
        }
        amounts = restored.amounts
        transfers.adopt(restored.transfers)
        retainedBytes = restored.retainedBytes
    }

    companion object {
        val FULL_SOURCE: BigDecimal = BigDecimal.valueOf(255)

        @JvmStatic
        fun owns(level: BlockGetter, position: BlockPos): Boolean {
            if (level !is ServerLevel) return false
            val sources = forLevel(level) ?: return false
            return sources.amounts.isNotEmpty() && sources.amounts.containsKey(
                Position(level.dimensionId, position.x, position.y, position.z))
        }

        @JvmStatic
        fun blocksPickup(level: BlockGetter, position: BlockPos): Boolean {
            if (level !is ServerLevel) return false
            val sources = forLevel(level) ?: return false
            if (sources.size == 0) return false
            if (sources.transfers.isLocked(Position(level.dimensionId, position.x, position.y, position.z))) return true
            val fluid = fluidName(level.getBlockState(position)) ?: return false
            val units = sources.amount(level.dimensionId, position.x, position.y, position.z, fluid) ?: return false
            return units < FULL_SOURCE
        }

        @JvmStatic
        fun vanillaFlowState(level: BlockGetter, position: BlockPos, state: BlockState): BlockState {
            if (!owns(level, position)) return state
            return if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state.setValue(BlockStateProperties.WATERLOGGED, false)
            } else Blocks.AIR.defaultBlockState()
        }

        fun forLevel(level: ServerLevel): FractionalFluidSources? =
            (level.server as? IShipObjectWorldServerProvider)?.fractionalFluidSources

        fun fluidName(state: BlockState): String? {
            val type = state.fluidState.type
            if (state.fluidState.isEmpty) return null
            return BuiltInRegistries.FLUID.getKey(if (type is FlowingFluid) type.source else type).toString()
        }
    }
}
