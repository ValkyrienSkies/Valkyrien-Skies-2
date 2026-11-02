package org.valkyrienskies.mod.common.fluid

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.config.VSGameConfig
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

    internal data class Splash(val tick: Long, val level: Int, val pending: Boolean = false)
    internal data class Amount(val fluid: String, val units: BigDecimal, val bytes: Long, val splash: Splash? = null)
    internal var amounts = TreeMap<Position, Amount>()
    internal val transfers = FractionalFluidTransfers(this)
    var retainedBytes = 0L
        internal set
    val size: Int get() = amounts.size
    internal var splashClock = 0L
        private set
    private var splashCursor: Position? = null

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
        val splash = previous?.takeIf { it.fluid == fluid && units < splashThreshold() }?.splash
        val bytes = amountBytes(position, fluid, normalized, splash)
        val additional = bytes - (previous?.bytes ?: 0L)
        if (additional > maximumBytes - retainedBytes) return false
        amounts[position] = Amount(fluid, normalized, bytes, splash)
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
        before: BigDecimal, after: BigDecimal, splashLevel: Int? = null): PreparedChange? {
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
        val splash = if (fluid == "minecraft:water" && splashLevel != null && splashLevel in 1..15 &&
            (previous?.splash != null || previous == null && before.signum() == 0) &&
            after < splashThreshold()) Splash(splashClock, splashLevel) else null
        val reservedSplash = (previous?.splash ?: splash)?.copy(pending = true)
        val replacement = Amount(fluid, normalizedAfter, amountBytes(position, fluid, normalizedAfter, splash), splash)
        val reserved = Amount(fluid, normalizedBefore,
            maxOf(amountBytes(position, fluid, normalizedBefore, reservedSplash), replacement.bytes), reservedSplash)
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

    fun tickSplashes(levels: Map<String, ServerLevel>) {
        val lifetime = VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds.coerceIn(0, 86400) * 20L
        if (lifetime == 0L || amounts.isEmpty()) return
        splashClock++
        repeat(minOf(SPLASH_SCAN_LIMIT, amounts.size)) {
            if (amounts.isEmpty()) return
            val position = splashCursor?.let { amounts.higherKey(it) } ?: amounts.firstKey()
            splashCursor = position
            val amount = amounts[position] ?: return@repeat
            val splash = amount.splash ?: return@repeat
            if (splash.pending || transfers.isLocked(position) || splashClock - splash.tick < lifetime) return@repeat
            val level = levels[position.dimension] ?: return@repeat
            val pos = BlockPos(position.x, position.y, position.z)
            if (!level.hasChunkAt(pos)) return@repeat
            val state = level.getBlockState(pos)
            val expected = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, splash.level)
            if (state != expected) {
                reconcileFluid(position.dimension, position.x, position.y, position.z, fluidName(state))
                if (amounts[position] === amount) keepPuddle(position, amount)
                return@repeat
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            if (amounts[position] === amount && level.getBlockState(pos).isAir) {
                amounts.remove(position)
                retainedBytes -= amount.bytes
            }
        }
    }

    private fun keepPuddle(position: Position, amount: Amount) {
        val bytes = amountBytes(position, amount.fluid, amount.units, null)
        amounts[position] = amount.copy(bytes = bytes, splash = null)
        retainedBytes += bytes - amount.bytes
    }

    internal fun saveSplash(tag: CompoundTag, amount: Amount) {
        amount.splash?.let {
            tag.putLong("splash_tick", it.tick)
            tag.putInt("splash_level", it.level)
            tag.putBoolean("splash_pending", it.pending)
        }
    }

    internal fun loadSplash(tag: CompoundTag, position: Position, amount: Amount): Amount {
        if (!tag.contains("splash_tick")) return amount
        require(tag.contains("splash_tick", Tag.TAG_LONG.toInt()) && tag.getLong("splash_tick") in 0..splashClock)
        require(tag.contains("splash_level", Tag.TAG_INT.toInt()) && tag.getInt("splash_level") in 1..15)
        require(tag.contains("splash_pending", Tag.TAG_BYTE.toInt()) && tag.getByte("splash_pending").toInt() in 0..1)
        require(amount.fluid == "minecraft:water" && amount.units < FULL_SOURCE)
        val splash = Splash(tag.getLong("splash_tick"), tag.getInt("splash_level"), tag.getBoolean("splash_pending"))
        return amount.copy(bytes = amountBytes(position, amount.fluid, amount.units, splash), splash = splash)
    }

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
        putInt("version", 5)
        putLong("splash_clock", splashClock)
        put("sources", ListTag().also { sources ->
            for ((position, amount) in amounts) sources.add(CompoundTag().apply {
                putString("dimension", position.dimension)
                putIntArray("position", intArrayOf(position.x, position.y, position.z))
                putString("fluid", amount.fluid)
                putString("units", amount.units.toString())
                saveSplash(this, amount)
            })
        })
        put("transfers", transfers.save())
    }

    fun load(tag: CompoundTag) {
        check(!transfers.hasActiveTransfers())
        require(tag.contains("version", Tag.TAG_INT.toInt()) && tag.getInt("version") in 1..5)
        require(tag.contains("sources", Tag.TAG_LIST.toInt()))
        val sources = tag.get("sources") as ListTag
        require(sources.isEmpty() || sources.elementType == Tag.TAG_COMPOUND)
        require(sources.size.toLong() <= maximumBytes / 512)
        val restored = FractionalFluidSources(maximumBytes)
        if (tag.getInt("version") >= 5) {
            require(tag.contains("splash_clock", Tag.TAG_LONG.toInt()) && tag.getLong("splash_clock") in 0..Long.MAX_VALUE / 2)
            restored.splashClock = tag.getLong("splash_clock")
        }
        for (index in 0 until sources.size) {
            val entry = sources.getCompound(index)
            require(entry.contains("dimension", Tag.TAG_STRING.toInt()) && entry.contains("fluid", Tag.TAG_STRING.toInt()))
            require(entry.contains("position", Tag.TAG_INT_ARRAY.toInt()) && entry.contains("units", Tag.TAG_STRING.toInt()))
            val position = entry.getIntArray("position")
            require(position.size == 3)
            val dimension = entry.getString("dimension")
            val fluid = canonicalFluidName(entry.getString("fluid"))
            val encoded = entry.getString("units")
            require(encoded.length in 1..640)
            val units = encoded.toBigDecimal()
            require(units < FULL_SOURCE || tag.getInt("version") >= 4 && units.compareTo(FULL_SOURCE) == 0)
            require(!restored.amounts.containsKey(Position(dimension, position[0], position[1], position[2])))
            require(restored.setAmount(dimension, position[0], position[1], position[2], fluid, units)) {
                "Fractional fluid sources exceed their storage allowance"
            }
            if (tag.getInt("version") >= 5) {
                val key = Position(dimension, position[0], position[1], position[2])
                val original = restored.amounts.getValue(key)
                val amount = restored.loadSplash(entry, key, original)
                val additional = amount.bytes - original.bytes
                require(additional <= maximumBytes - restored.retainedBytes)
                restored.amounts[key] = amount
                restored.retainedBytes += additional
            }
        }
        if (tag.getInt("version") >= 2) {
            require(tag.contains("transfers", Tag.TAG_LIST.toInt()))
            restored.transfers.load(tag.get("transfers") as ListTag, tag.getInt("version"))
        }
        amounts = restored.amounts
        transfers.adopt(restored.transfers)
        retainedBytes = restored.retainedBytes
        splashClock = restored.splashClock
        splashCursor = null
    }

    companion object {
        val FULL_SOURCE: BigDecimal = BigDecimal.valueOf(255)
        internal const val SPLASH_SCAN_LIMIT = 256

        private fun amountBytes(position: Position, fluid: String, units: BigDecimal, splash: Splash?) =
            512L + (if (splash == null) 0L else 256L) + 4L * (position.dimension.length + fluid.length + units.toString().length)

        private fun splashThreshold(): BigDecimal {
            val volume = VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold
            return BigDecimal.valueOf(if (volume.isFinite()) volume.coerceIn(0.0, 1.0) else 0.0) * FULL_SOURCE
        }

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
            return if (type is FlowingFluid) fluidName(type) else BuiltInRegistries.FLUID.getKey(type).toString()
        }

        fun fluidName(fluid: FlowingFluid): String = BuiltInRegistries.FLUID.getKey(fluid.source).toString()

        internal fun canonicalFluidName(name: String): String {
            require(name.length in 1..4096)
            val key = ResourceLocation.tryParse(name) ?: return name
            val fluid = BuiltInRegistries.FLUID.getOptional(key).orElse(null) as? FlowingFluid ?: return name
            return fluidName(fluid)
        }
    }
}
