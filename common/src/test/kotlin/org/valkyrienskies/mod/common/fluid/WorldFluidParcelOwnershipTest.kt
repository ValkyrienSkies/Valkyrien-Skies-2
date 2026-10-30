package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.VsiCore
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.core.internal.world.VsiPipeline
import org.valkyrienskies.mod.common.ShipSavedData
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.vsCore

class WorldFluidParcelOwnershipTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>()
    private val blocks = mutableMapOf<BlockPos, BlockState>()
    private var terrain: (BlockPos) -> BlockState = { if (it.y <= 0) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState() }
    private var loaded: (BlockPos) -> Boolean = { true }
    private var acceptWrites = true
    private var returnWriteSuccess = true
    private var writes = 0
    private var fluidTicks = 0
    private val levels get() = mapOf(DIMENSION to level)

    @BeforeEach fun setup() {
        every { level.server } returns mockk<net.minecraft.server.MinecraftServer>()
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { MassDatapackResolver.getFlowingFluid(18) } returns Fluids.LAVA
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } answers { loaded(firstArg()) }
        every { level.getBlockState(any()) } answers { blocks[firstArg<BlockPos>()] ?: terrain(firstArg()) }
        every { level.players() } returns emptyList()
        every { level.setBlock(any(), any(), any()) } answers {
            writes++
            if (acceptWrites) blocks[firstArg<BlockPos>().immutable()] = secondArg()
            acceptWrites && returnWriteSuccess
        }
        every { level.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } answers { fluidTicks++ }
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun outflow(fill: Int = 255, x: Double = 0.5, y: Double = 1.5, dimension: String = DIMENSION,
        vx: Double = 0.0, fluid: Int = 17) = VsiFluidOutflow(1, 1, 1, dimension, 7, x, y, 0.5,
        0.0, -1.0, 0.0, vx, 0.0, 0.0, fluid, fill)

    private fun entries(manager: WorldFluidOutflowManager) = manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt())
    private fun pending(manager: WorldFluidOutflowManager) = entries(manager).sumOf { (it as CompoundTag).getString("volume").toDouble() }
    private fun sources() = blocks.values.count { it.fluidState.isSource }

    @Test fun `aggregate admission shares metadata and source growth without losing water`() {
        val manager = WorldFluidOutflowManager(8, 8)
        val empty = manager.parcelStorageBytes()
        manager.enqueue(level, outflow().copy(worldVolume = 3.25))
        val ownerBytes = manager.parcelStorageBytes() - empty
        manager.maxParcelStorageBytes = manager.parcelStorageBytes() + 48
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow(x = 10.5)))
        manager.tick(levels)
        assertEquals(manager.maxParcelStorageBytes, manager.parcelStorageBytes())
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) {
            repeat(8) { manager.tick(levels) }
        }
        assertEquals(3.25, sources() + pending(manager))
        val saved = manager.save()
        var restored = WorldFluidOutflowManager(8, 8)
        restored.load(saved)
        repeat(2) {
            val checkpoint = restored.save()
            restored = WorldFluidOutflowManager(8, 8).also { it.load(checkpoint) }
            assertEquals(checkpoint, restored.save())
            assertEquals(manager.parcelStorageBytes(), restored.parcelStorageBytes())
        }
        repeat(8) { restored.tick(levels) }
        assertEquals(0, restored.activeParcelCount())
        assertEquals(empty, restored.parcelStorageBytes())
        assertEquals(3, sources())
        assertEquals(listOf(2), blocks.values.filter { !it.fluidState.isEmpty && !it.fluidState.isSource }
            .map { it.fluidState.amount })
        assertTrue(manager.peakParcelStorageBytes() <= manager.maxParcelStorageBytes)
        // Capacity is independent of the count cap and becomes reusable after owners drain.
        restored.maxParcelStorageBytes = empty + ownerBytes
        assertEquals(VsiFluidOutflowResult.APPLIED, restored.enqueue(level, outflow(x = 20.5)))
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, restored.enqueue(level, outflow(x = 30.5)))
    }

    @Test fun `variable names are admitted before ownership and oversized owners fail explicitly`() {
        val manager = WorldFluidOutflowManager(8, 8)
        val empty = manager.parcelStorageBytes()
        manager.enqueue(level, outflow())
        val fixed = manager.parcelStorageBytes() - empty
        val longDimension = "missing:" + "x".repeat(4096)
        manager.maxParcelStorageBytes = empty + fixed * 2
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) {
            manager.enqueue(level, outflow(dimension = longDimension))
        }
        assertEquals(1.0, pending(manager))
        assertEquals(empty + fixed, manager.parcelStorageBytes())
        manager.maxParcelStorageBytes = empty + fixed * 8
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(dimension = longDimension)))
        assertTrue(manager.parcelStorageBytes() > empty + fixed * 2)
        val saved = manager.save()
        val entries = saved.getList("parcels", Tag.TAG_COMPOUND.toInt())
        entries.getCompound(1).putString("fluid", "missing:" + "y".repeat(4096))
        val fresh = WorldFluidOutflowManager(8, 8)
        fresh.maxParcelStorageBytes = empty + fixed * 3
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { fresh.load(saved) }
        assertEquals(empty, fresh.parcelStorageBytes())
        fresh.maxParcelStorageBytes = empty + fixed * 16
        fresh.load(saved)
        assertEquals(saved, fresh.save())
    }

    @Test fun `restore reserves overlapping metadata and rolls back a malformed decoded suffix`() {
        val manager = WorldFluidOutflowManager(8, 8)
        val empty = manager.parcelStorageBytes()
        repeat(2) { manager.enqueue(level, outflow(x = it * 10.0 + 0.5)) }
        val retained = manager.parcelStorageBytes()
        val saved = manager.save()
        manager.maxParcelStorageBytes = retained
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { manager.load(saved) }
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(saved, manager.save())
        manager.maxParcelStorageBytes = empty + (retained - empty) * 2
        val invalid = saved.copy()
        invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(1).putDouble("x", Double.NaN)
        assertThrows(IllegalArgumentException::class.java) { manager.load(invalid) }
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(saved, manager.save())
        manager.load(saved)
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(manager.maxParcelStorageBytes, manager.peakParcelStorageBytes())
        manager.tick(levels)
        assertEquals(empty, manager.parcelStorageBytes())
    }

    @Test fun `a full parcel owner frees capacity during pressure without a normal world tick`() {
        var now = 0L
        val manager = WorldFluidOutflowManager(1, 1) { now }
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(510)))
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow()))
        manager.servicePressure(levels)
        repeat(2) { step ->
            now += 50_000_000L
            manager.servicePressure(levels)
            assertEquals(step + 1, sources())
            assertEquals(2.0, sources() + pending(manager))
            val checkpoint = manager.save()
            repeat(20) { manager.servicePressure(levels) }
            assertEquals(checkpoint, manager.save(), "Retries do not earn more parcel time")
        }
        assertEquals(0, manager.activeParcelCount())
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(x = 8.5)))
        val justAccepted = manager.save()
        manager.tick(levels)
        assertEquals(justAccepted, manager.save(), "The normal game tail cannot double-step pressure time")
        assertEquals(2, sources())
        manager.tick(levels)
        assertEquals(3, sources())
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `pressure clock retains fractional time and matches ordinary collision sampled motion`() {
        terrain = { if (it.x >= 2) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState() }
        var now = 7L
        val manager = WorldFluidOutflowManager(1, 1) { now }
        val reference = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(y = 100.5, vx = 20.0))
        reference.enqueue(level, outflow(y = 100.5, vx = 20.0))
        manager.servicePressure(levels)
        val initial = manager.save()
        now += 49_999_999L
        repeat(20) { manager.servicePressure(levels) }
        assertEquals(initial, manager.save())
        now++
        manager.servicePressure(levels)
        reference.tick(levels)
        assertEquals(reference.save(), manager.save())
        now += 150_000_001L
        manager.servicePressure(levels)
        repeat(3) { reference.tick(levels) }
        assertEquals(reference.save(), manager.save())
        repeat(20) { manager.servicePressure(levels) }
        manager.tick(levels)
        assertEquals(reference.save(), manager.save())
    }

    @Test fun `due motion requests another bounded pass without adding a 50 ms sleep per batch`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        var now = 0L
        val manager = WorldFluidOutflowManager(5, 2) { now }
        repeat(5) { manager.enqueue(level, outflow(y = 100.5)) }
        assertFalse(manager.servicePressure(levels))
        now += 50_000_000L
        assertTrue(manager.servicePressure(levels))
        assertTrue(manager.servicePressure(levels))
        assertFalse(manager.servicePressure(levels))
        for (parcel in entries(manager)) {
            assertEquals(100.5 - 0.024525, (parcel as CompoundTag).getDouble("y"), 1e-12)
        }
        repeat(20) { assertFalse(manager.servicePressure(levels)) }
        val saved = manager.save()
        manager.tick(levels)
        assertEquals(saved, manager.save())
    }

    @Test fun `pressure rotates the bounded parcel budget with the same 20 Hz trajectories`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        var now = 0L
        val manager = WorldFluidOutflowManager(32, 2) { now }
        val reference = WorldFluidOutflowManager(32, 2)
        repeat(32) {
            manager.enqueue(level, outflow(y = 100.5, vx = 2.0))
            reference.enqueue(level, outflow(y = 100.5, vx = 2.0))
        }
        manager.servicePressure(levels)
        repeat(16) {
            now += 50_000_000L
            manager.servicePressure(levels)
            reference.tick(levels)
        }
        assertEquals(reference.save(), manager.save())
    }

    @Test fun `pressure catch-up has a motion budget and preserves remaining debt across save load`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        val manager = WorldFluidOutflowManager(2, 2) { 0L }
        manager.enqueue(level, outflow(y = 1_000_000.5))
        manager.enqueue(level, outflow(x = 5.5, y = 100.5))
        val initial = manager.save()
        val parcels = initial.getList("parcels", Tag.TAG_COMPOUND.toInt())
        parcels.getCompound(0).putLong("lag", 10_000L)
        parcels.getCompound(1).putLong("lag", 1L)
        manager.load(initial)
        manager.servicePressure(levels)
        assertEquals(5904L, entries(manager).getCompound(1).getLong("lag"))
        val saved = manager.save()
        val restored = WorldFluidOutflowManager(2, 2) { 0L }
        restored.load(saved)
        assertEquals(saved, restored.save())
        restored.servicePressure(levels)
        val after = entries(restored)
        assertEquals(100.5 - 0.024525, after.getCompound(0).getDouble("y"), 1e-12,
            "A large catch-up cannot permanently starve the next parcel")
        assertEquals(1809L, after.getCompound(1).getLong("lag"))
        restored.tick(levels)
        assertEquals(0L, entries(restored).getCompound(1).getLong("lag"))
        assertEquals(2.0, pending(restored))
    }

    @Test fun `unloaded pressure parcels retain water but do not accumulate motion through unavailable terrain`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        var now = 0L
        val manager = WorldFluidOutflowManager(1, 1) { now }
        manager.enqueue(level, outflow(y = 100.5, vx = 20.0))
        manager.servicePressure(levels)
        loaded = { false }
        now += 5_000_000_000L
        manager.servicePressure(levels)
        assertEquals(100.5, entries(manager).getCompound(0).getDouble("y"))
        assertEquals(1.0, pending(manager))
        loaded = { true }
        now += 50_000_000L
        manager.servicePressure(levels)
        assertEquals(100.5 - 0.024525, entries(manager).getCompound(0).getDouble("y"), 1e-12)
        assertEquals(1.5, entries(manager).getCompound(0).getDouble("x"), 1e-12)
    }

    @Test fun `large parcel materializes every full voxel without requiring more queue capacity`() {
        val manager = WorldFluidOutflowManager(1, 1)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(65535)))
        repeat(257) {
            val previousWrites = writes
            manager.tick(levels)
            assertTrue(writes - previousWrites <= 1)
            assertEquals(257.0, sources() + pending(manager), 1e-12)
        }
        assertEquals(257, sources())
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `fractional arrival adds to existing flowing liquid`() {
        val pos = BlockPos(0, 1, 0)
        blocks[pos] = Fluids.WATER.getFlowing(4, false).createLegacyBlock()
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(63))
        manager.tick(levels)
        assertEquals(5, blocks.getValue(pos).fluidState.amount)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(1, fluidTicks)
    }

    @Test fun `scaled physical quantities survive rejected writes and saves then settle at world scale`() {
        for ((volume, fullBlocks, flowingLevel) in listOf(
            Triple(0.125, 0, 1), Triple(0.249, 0, 1), Triple(8.0, 8, 0), Triple(4.75, 4, 6),
        )) {
            blocks.clear()
            acceptWrites = false
            val manager = WorldFluidOutflowManager(1, 1)
            assertEquals(VsiFluidOutflowResult.APPLIED,
                manager.enqueue(level, outflow().copy(worldVolume = volume)))
            assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow()))
            repeat(3) { manager.tick(levels) }
            assertEquals(volume, pending(manager))
            val restored = WorldFluidOutflowManager(1, 1)
            restored.load(manager.save())
            assertEquals(manager.save(), restored.save())
            acceptWrites = true
            repeat(fullBlocks + 2) { restored.tick(levels) }
            assertEquals(fullBlocks, sources(), "volume=$volume")
            assertEquals(0, restored.activeParcelCount())
            val flowing = blocks.values.filter { !it.fluidState.isEmpty && !it.fluidState.isSource }
            assertEquals(if (flowingLevel == 0) emptyList<Int>() else listOf(flowingLevel),
                flowing.map { it.fluidState.amount }, "volume=$volume")
        }
    }

    @Test fun `volume above double unit precision debits every placed block across reloads`() {
        var manager = WorldFluidOutflowManager(1, 1)
        val original = java.math.BigDecimal("1.0E20")
        manager.enqueue(level, outflow().copy(worldVolume = 1e20))
        repeat(4) { index ->
            val previousWrites = writes
            manager.tick(levels)
            assertEquals(1, writes - previousWrites)
            val saved = manager.save()
            val remaining = saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
                .getString("volume").toBigDecimal()
            assertEquals(0, original.compareTo(remaining + (index + 1).toBigDecimal()))
            manager = WorldFluidOutflowManager(1, 1).also { it.load(saved) }
        }
        assertEquals(4, sources())
    }

    @Test fun `legacy integer fill checkpoints migrate to physical volume without requiring scale metadata`() {
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(511))
        val legacy = manager.save().apply { putInt("version", 1) }
        legacy.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).apply {
            remove("volume")
            putInt("fill", 511)
        }
        val restored = WorldFluidOutflowManager()
        restored.load(legacy)
        assertEquals(511 / 255.0, pending(restored))
        val migrated = restored.save()
        assertEquals(5, migrated.getInt("version"))
        // The current journal format must preserve migrated legacy ownership on another load.
        restored.load(migrated)
        assertEquals(511 / 255.0, pending(restored))
        repeat(3) { restored.tick(levels) }
        assertEquals(2, sources())
        assertEquals(0, restored.activeParcelCount())
    }

    @Test fun `invalid physical quantities retain upstream ownership and cannot replace a saved owner`() {
        val manager = WorldFluidOutflowManager()
        for (volume in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0)) {
            assertEquals(VsiFluidOutflowResult.RETRY_LATER,
                manager.enqueue(level, outflow().copy(worldVolume = volume)))
        }
        assertEquals(0, manager.activeParcelCount())
        manager.enqueue(level, outflow().copy(worldVolume = 0.125))
        val before = manager.save()
        for (invalid in listOf("NaN", "Infinity", "0", "-1", "1E400", "nonsense")) {
            val bad = before.copy()
            bad.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("volume", invalid)
            assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
            assertEquals(before, manager.save())
        }
    }

    @Test fun `saved decimal scale and size are rejected atomically`() {
        val manager = WorldFluidOutflowManager(2, 2)
        manager.enqueue(level, outflow(510))
        manager.enqueue(level, outflow(x = 8.5))
        val original = manager.save()
        for (invalid in listOf("1E-2147483647", "1E-326", "1E+2147483647",
            "0".repeat(641) + "1", "0." + "1".repeat(641))) {
            val bad = original.copy()
            val list = bad.getList("parcels", Tag.TAG_COMPOUND.toInt())
            list.getCompound(0).putString("volume", "3") // A prior valid owner cannot publish early.
            list.getCompound(1).putString("volume", invalid)
            // Only load: never tick or perform arithmetic on an unbounded test decimal.
            assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
            assertEquals(original, manager.save())
        }
    }

    @Test fun `saved deposited decimal bounds also apply to unavailable fluids`() {
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(510))
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("retain intent")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val original = manager.save()
        for (invalid in listOf("1E-2147483647", "1E-326", "0." + "1".repeat(641),
            "0".repeat(641) + "1")) {
            val bad = original.copy()
            val owner = bad.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
            owner.putString("fluid", "missing:liquid")
            owner.getCompound("placement").putString("deposited", invalid)
            assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
            assertEquals(original, manager.save())
        }
    }

    @Test fun `finite double extremes preserve exact parcel ownership on repeated reload`() {
        for (volume in listOf(Double.MIN_VALUE, java.lang.Double.MIN_NORMAL, 1e-308, 0.125, 1e308, Double.MAX_VALUE)) {
            var manager = WorldFluidOutflowManager(1, 1)
            assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow().copy(worldVolume = volume)))
            val saved = manager.save()
            repeat(2) {
                manager = WorldFluidOutflowManager(1, 1).also { it.load(saved) }
                assertEquals(saved, manager.save(), "volume=$volume")
            }
        }
    }

    @Test fun `source history capacity is shared and returned when owners finish`() {
        val manager = WorldFluidOutflowManager(100, 100, 96L)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(x = 0.5).copy(worldVolume = 2.0)))
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(x = 10.5).copy(worldVolume = 2.0)))
        manager.tick(levels)
        assertEquals(96L, manager.sourceStorageBytes())
        assertEquals(2, manager.activeParcelCount())
        assertEquals(manager.allocatedSourceHistoryBytes() * 2, manager.sourceStorageBytes())
        // An empty history does not reserve hypothetical future placements. Its first actual
        // allocation still shares the same bound and must precede any world mutation.
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(x = 20.5).copy(worldVolume = 2.0)))
        loaded = { it.x >= 20 }
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { manager.tick(levels) }
        assertEquals(2, sources())
        assertEquals(6.0, sources() + pending(manager))
        loaded = { true }
        repeat(3) { manager.tick(levels) }
        assertEquals(6, sources())
        assertEquals(0L, manager.sourceStorageBytes())
        assertEquals(0L, manager.allocatedSourceHistoryBytes())
        assertTrue(manager.peakSourceStorageBytes() <= manager.maxSourceStorageBytes)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(x = 20.5)))
    }

    @Test fun `compact and single block parcels do not allocate hypothetical source history`() {
        val manager = WorldFluidOutflowManager(2, 2, 1L)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow()))
        manager.tick(levels)
        assertEquals(1, sources())
        assertEquals(0L, manager.sourceStorageBytes())
        assertEquals(VsiFluidOutflowResult.APPLIED,
            manager.enqueue(level, outflow(x = 10.5).copy(worldVolume = Double.MAX_VALUE)))
        val saved = manager.save()
        val restored = WorldFluidOutflowManager(2, 2, 1L).also { it.load(saved) }
        assertEquals(saved, restored.save())
        assertEquals(0L, restored.sourceStorageBytes())
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { restored.tick(levels) }
        assertEquals(1, sources())
    }

    @Test fun `history growth refuses before a write and preserves water through two cold reloads`() {
        var manager = WorldFluidOutflowManager(4, 4, 256L)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow().copy(worldVolume = 13.25)))
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) {
            repeat(20) { manager.tick(levels) }
        }
        assertTrue(sources() > 1)
        assertEquals(13.25, sources() + pending(manager))
        assertTrue(manager.peakSourceStorageBytes() <= manager.maxSourceStorageBytes)
        assertEquals(manager.allocatedSourceHistoryBytes() * 2, manager.sourceStorageBytes())
        val written = writes
        var saved = manager.save()
        val owner = saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
        assertTrue(owner.contains("placement"))
        assertEquals(sources(), owner.getLongArray("settled_sources").size)
        repeat(2) {
            manager = WorldFluidOutflowManager(4, 4, 256L).also { it.load(saved) }
            assertEquals(saved, manager.save())
            assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { manager.tick(levels) }
            assertEquals(written, writes, "No write may precede history admission")
            assertEquals(13.25, sources() + pending(manager))
            saved = manager.save()
        }
        manager.maxSourceStorageBytes = 4096
        repeat(25) { manager.tick(levels) }
        assertEquals(0, manager.activeParcelCount())
        assertEquals(0L, manager.sourceStorageBytes())
        assertEquals(13, sources())
        assertEquals(listOf(2), blocks.values.filter { !it.fluidState.isEmpty && !it.fluidState.isSource }
            .map { it.fluidState.amount })
    }

    @Test fun `capacity servicing lets a later owner release storage for a blocked history`() {
        for (perPass in listOf(1, 8)) {
            blocks.clear()
            val manager = WorldFluidOutflowManager(8, perPass, 128L)
            manager.enqueue(level, outflow().copy(worldVolume = 2.25))
            manager.enqueue(level, outflow(x = 10.5).copy(worldVolume = 1.5))
            repeat(if (perPass == 1) 2 else 1) { manager.tick(levels) }
            // The first owner needs to grow while both histories are retained. The second can finish.
            assertDoesNotThrow { repeat(2) { manager.tick(levels) } }
            assertEquals(1, manager.activeParcelCount())
            repeat(4) { manager.tick(levels) }
            assertEquals(0, manager.activeParcelCount())
            assertEquals(3, sources())
            assertEquals(listOf(2, 4), blocks.values.filter { !it.fluidState.isEmpty && !it.fluidState.isSource }
                .map { it.fluidState.amount }.sorted())
            assertTrue(manager.peakSourceStorageBytes() <= manager.maxSourceStorageBytes)
        }
    }

    @Test fun `bounded pressure passes report exhaustion after every blocked owner has a turn`() {
        var now = 0L
        val manager = WorldFluidOutflowManager(8, 1, 96L) { now }
        manager.enqueue(level, outflow().copy(worldVolume = 4.0))
        manager.enqueue(level, outflow(x = 10.5).copy(worldVolume = 4.0))
        manager.servicePressure(levels)
        now += 50_000_000L
        assertTrue(manager.servicePressure(levels))
        assertFalse(manager.servicePressure(levels))
        assertEquals(2, sources())
        now += 50_000_000L
        assertTrue(manager.servicePressure(levels), "The other owner still needs its bounded turn")
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) {
            manager.servicePressure(levels)
        }
        assertEquals(2, sources())
        assertEquals(8.0, sources() + pending(manager))
        assertTrue(manager.peakSourceStorageBytes() <= manager.maxSourceStorageBytes)
    }

    @Test fun `source restore admits old and new capacity atomically and refunds failed validation`() {
        val manager = WorldFluidOutflowManager(8, 8, 512L)
        manager.enqueue(level, outflow().copy(worldVolume = Double.MAX_VALUE))
        manager.tick(levels)
        val before = manager.save()
        val used = manager.sourceStorageBytes()
        val tooLarge = before.copy()
        tooLarge.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
            .putLongArray("settled_sources", LongArray(64) { it.toLong() })
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { manager.load(tooLarge) }
        assertEquals(before, manager.save())
        assertEquals(used, manager.sourceStorageBytes())

        val invalid = before.copy()
        invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("volume", "bad")
        assertThrows(IllegalArgumentException::class.java) { manager.load(invalid) }
        assertEquals(before, manager.save())
        assertEquals(used, manager.sourceStorageBytes())
        val invalidSuffix = before.copy()
        invalidSuffix.getList("parcels", Tag.TAG_COMPOUND.toInt()).add(
            invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).copy())
        assertThrows(IllegalArgumentException::class.java) { manager.load(invalidSuffix) }
        assertEquals(before, manager.save(), "A decoded prefix cannot replace any old owner on suffix failure")
        assertEquals(used, manager.sourceStorageBytes())
        assertThrows(IllegalArgumentException::class.java) { manager.maxSourceStorageBytes = used - 1 }

        manager.maxSourceStorageBytes = used
        assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { manager.load(before) }
        assertEquals(before, manager.save(), "A fitting replacement must also admit its overlap")
        manager.maxSourceStorageBytes = used * 2
        manager.load(before)
        assertEquals(used, manager.sourceStorageBytes())
        assertEquals(before, manager.save())
        manager.load(WorldFluidOutflowManager().save())
        assertEquals(0L, manager.sourceStorageBytes())
    }

    @Test fun `large history saves in its prepaid array without a second sorting allocation`() {
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow().copy(worldVolume = Double.MAX_VALUE))
        val saved = manager.save()
        val count = 100_000
        saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
            .putLongArray("settled_sources", LongArray(count) { (count - it).toLong() })
        manager.load(saved)
        manager.maxSourceStorageBytes = manager.sourceStorageBytes()
        val bean = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        Assumptions.assumeTrue(bean != null && bean.isThreadAllocatedMemorySupported)
        bean!!.isThreadAllocatedMemoryEnabled = true
        repeat(5) { manager.save() }
        val thread = Thread.currentThread().id
        val beforeBytes = bean.getThreadAllocatedBytes(thread)
        val output = manager.save()
        val allocatedBytes = bean.getThreadAllocatedBytes(thread) - beforeBytes
        assertTrue(allocatedBytes <= count.toLong() * 8 + 8192,
            "Save allocated $allocatedBytes bytes for a ${count * 8}-byte array and fixed NBT metadata")
        assertArrayEquals(LongArray(count) { it.toLong() + 1 },
            output.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).getLongArray("settled_sources"))
        assertEquals(manager.allocatedSourceHistoryBytes() * 2, manager.sourceStorageBytes())
    }

    @Test fun `large exact fractional debits and longest bounded coefficient survive reload`() {
        val maximum = java.math.BigDecimal.valueOf(Double.MAX_VALUE)
        for (original in listOf(maximum, maximum.setScale(325))) {
            blocks.clear()
            var manager = WorldFluidOutflowManager(1, 1)
            manager.enqueue(level, outflow().copy(worldVolume = Double.MAX_VALUE))
            val seed = manager.save()
            seed.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("volume", original.toString())
            manager.load(seed)
            blocks[BlockPos(0, 1, 0)] = Fluids.WATER.getFlowing(7, false).createLegacyBlock()
            manager.tick(levels)
            assertTrue(blocks.getValue(BlockPos(0, 1, 0)).fluidState.isSource)
            val saved = manager.save()
            val remaining = saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
                .getString("volume").toBigDecimal()
            assertEquals(0, original.compareTo(remaining + java.math.BigDecimal("0.125")))
            repeat(2) {
                manager = WorldFluidOutflowManager(1, 1).also { it.load(saved) }
                assertEquals(saved, manager.save())
            }
        }
    }

    @Test fun `smallest parcel merging into existing water keeps its exact deposit and scheduling receipt`() {
        blocks[BlockPos(0, 1, 0)] = Fluids.WATER.getFlowing(1, false).createLegacyBlock()
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow().copy(worldVolume = Double.MIN_VALUE))
        every { level.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } throws
            IllegalStateException("retain scheduling receipt")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val saved = manager.save()
        val owner = saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
        assertEquals(java.math.BigDecimal.ZERO.setScale(325).toString(), owner.getString("volume"))
        assertEquals(java.math.BigDecimal.valueOf(Double.MIN_VALUE).toString(), owner.getCompound("placement").getString("deposited"))
        assertTrue(owner.getCompound("placement").getBoolean("scheduled_only"))
        repeat(2) {
            val restored = WorldFluidOutflowManager(1, 1).also { it.load(saved) }
            assertEquals(saved, restored.save())
        }
        val bad = saved.copy()
        bad.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("volume", "0E-326")
        assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
        assertEquals(saved, manager.save())
    }

    @Test fun `settlement rounds fractional tails down and discards tails below one layer`() {
        for (amount in listOf(1, 128, 254, 255, 256, 510, 511, 765)) {
            blocks.clear()
            val manager = WorldFluidOutflowManager(1, 1)
            manager.enqueue(level, outflow(amount))
            repeat(amount / 255 + 2) { manager.tick(levels) }
            assertEquals(0, manager.activeParcelCount(), "amount=$amount")
            assertEquals(amount / 255, sources(), "amount=$amount")
            val flowing = blocks.values.filter { !it.fluidState.isEmpty && !it.fluidState.isSource }
            if (amount % 255 < 32) assertTrue(flowing.isEmpty())
            else assertEquals(listOf(settledFluidLevel((amount % 255).toBigDecimal()).coerceAtMost(7)),
                flowing.map { it.fluidState.amount })
        }
    }

    @Test fun `existing flowing volume fills a source before its undersized remainder lands`() {
        blocks[BlockPos(0, 1, 0)] = Fluids.WATER.getFlowing(4, false).createLegacyBlock()
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(128))
        manager.tick(levels)
        assertEquals(1, sources())
        assertEquals(128 / 255.0 - 0.5, pending(manager), 1e-16)
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(0, blocks.values.count { !it.fluidState.isEmpty && !it.fluidState.isSource })
    }

    @Test fun `same quantized flowing level is acknowledged without a failing identical write`() {
        val pos = BlockPos(0, 1, 0)
        blocks[pos] = Fluids.WATER.getFlowing(7, false).createLegacyBlock()
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(1))
        manager.tick(levels)
        assertEquals(7, blocks.getValue(pos).fluidState.amount)
        assertEquals(0, writes)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(1, fluidTicks)
    }

    @Test fun `full falling flow cannot create a source without a positive deposit`() {
        val pos = BlockPos(0, 1, 0)
        val falling = Fluids.WATER.getFlowing(8, true).createLegacyBlock()
        blocks[pos] = falling
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(1))
        manager.tick(levels)
        assertEquals(1 / 255.0, pending(manager))
        assertEquals(0, writes)
        val restored = WorldFluidOutflowManager()
        restored.load(manager.save())
        restored.tick(levels)
        assertEquals(0, restored.activeParcelCount())
        assertEquals(falling, blocks[pos])
        assertEquals(0, sources())
        assertEquals(0, writes)
    }

    @Test fun `rejected placement preserves volume and success debits it once`() {
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(510))
        acceptWrites = false
        repeat(3) { manager.tick(levels) }
        assertEquals(2.0, pending(manager))
        assertEquals(0, sources())
        acceptWrites = true
        returnWriteSuccess = false // Another hook has installed the target despite the false return value.
        manager.tick(levels)
        assertEquals(1.0, pending(manager))
        assertEquals(1, sources())
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(2, sources())
    }

    @Test fun `preexisting source still absorbs a newly arriving parcel`() {
        blocks[BlockPos(0, 0, 0)] = Fluids.WATER.getSource(false).createLegacyBlock()
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(510))
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(0, writes)
    }

    @Test fun `remainder can merge into an unrelated source but never its own deposit`() {
        blocks[BlockPos(0, 0, -1)] = Fluids.WATER.getSource(false).createLegacyBlock()
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(510))
        manager.tick(levels)
        assertEquals(1.0, pending(manager))
        assertEquals(1, writes)
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(1, writes, "The existing source absorbs the remainder without another source placement")
    }

    @Test fun `blocked outlet retains accepted ownership without replacing the block`() {
        val pos = BlockPos(0, 1, 0)
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow())
        blocks[pos] = Blocks.STONE.defaultBlockState()
        repeat(3) { manager.tick(levels) }
        assertEquals(1.0, pending(manager))
        assertEquals(0, writes)
    }

    @Test fun `waterlogged block can absorb fluid without being replaced`() {
        val pos = BlockPos(0, 1, 0)
        val slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        blocks[pos] = slab
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow())
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        assertEquals(slab, blocks[pos])
        assertEquals(0, writes)
    }

    @Test fun `settlement remainder survives a full cavity and checkpoint before continuing`() {
        val pos = BlockPos(0, 1, 0)
        Direction.values().forEach { blocks[pos.relative(it)] = Blocks.STONE.defaultBlockState() }
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(510))
        repeat(4) { manager.tick(levels) }
        assertEquals(1, sources())
        assertEquals(1.0, pending(manager))
        val saved = manager.save()
        val restored = WorldFluidOutflowManager(1, 1)
        restored.load(saved)
        assertEquals(saved, restored.save())
        blocks[pos.north()] = Blocks.AIR.defaultBlockState()
        restored.tick(levels)
        assertEquals(2, sources())
        assertEquals(0, restored.activeParcelCount())
    }

    @Test fun `full scheduling rotation catches up to unbudgeted 20 Hz motion including collisions`() {
        terrain = { if (it.x >= 2) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState() }
        val reference = WorldFluidOutflowManager(1, 1)
        val budgeted = WorldFluidOutflowManager(32, 2)
        reference.enqueue(level, outflow(y = 100.5, vx = 20.0))
        repeat(32) { budgeted.enqueue(level, outflow(y = 100.5, vx = 20.0)) }
        repeat(16) { reference.tick(levels); budgeted.tick(levels) }
        val expected = entries(reference).getCompound(0)
        val actual = entries(budgeted).getCompound(31)
        for (field in listOf("x", "y", "z", "vx", "vy", "vz")) {
            assertEquals(expected.getDouble(field), actual.getDouble(field), 1e-12, field)
        }
        assertTrue(actual.getDouble("x") < 2.0, "Catch-up must not tunnel through the wall")
        assertTrue(actual.getDouble("y") < 99.0)
    }

    @Test fun `unavailable chunks pause motion without accumulating a large catch-up`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(y = 100.5, vx = 20.0))
        loaded = { false }
        repeat(100) { manager.tick(levels) }
        assertEquals(100.5, entries(manager).getCompound(0).getDouble("y"))
        loaded = { true }
        manager.tick(levels)
        assertEquals(100.5 - 0.024525, entries(manager).getCompound(0).getDouble("y"), 1e-12)
        assertEquals(1.5, entries(manager).getCompound(0).getDouble("x"), 1e-12)
    }

    @Test fun `catch-up cannot cross an unloaded chunk and restores its attempted step velocity`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        loaded = { it.x < 1 }
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(y = 100.5, vx = 20.0))
        repeat(4) { manager.tick(levels) }
        val saved = entries(manager).getCompound(0)
        assertEquals(0.5, saved.getDouble("x"))
        assertEquals(100.5, saved.getDouble("y"))
        assertEquals(20.0, saved.getDouble("vx"))
        assertEquals(0.0, saved.getDouble("vy"))
        loaded = { true }
        manager.tick(levels)
        assertEquals(1.5, entries(manager).getCompound(0).getDouble("x"), 1e-12)
    }

    @Test fun `capacity and unsupported input retain upstream ownership`() {
        val manager = WorldFluidOutflowManager(1, 1)
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(null, outflow()))
        every { MassDatapackResolver.getFlowingFluid(19) } returns null
        assertEquals(VsiFluidOutflowResult.UNSUPPORTED_FLUID, manager.enqueue(level, outflow(fluid = 19)))
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(510)))
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow()))
        assertEquals(2.0, pending(manager))
    }

    @Test fun `every accepted parcel is saveable before its first motion step`() {
        val manager = WorldFluidOutflowManager()
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow(x = Double.NaN)))
        assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow(0)))
        assertEquals(0, manager.activeParcelCount())
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow(vx = Double.NaN)))
        val restored = WorldFluidOutflowManager()
        restored.load(manager.save())
        assertEquals(manager.save(), restored.save())
        assertEquals(0.0, entries(restored).getCompound(0).getDouble("vx"))
    }

    @Test fun `checkpoint retains names dimensions scheduling lag and motion without runtime fluid ids`() {
        terrain = { Blocks.AIR.defaultBlockState() }
        val manager = WorldFluidOutflowManager(4, 1)
        manager.enqueue(level, outflow(510, y = 100.5, vx = 3.0))
        manager.enqueue(level, outflow(128, y = 50.0, dimension = "mod:another", fluid = 18))
        manager.tick(levels)
        val checkpoint = manager.save()
        val restored = WorldFluidOutflowManager(4, 1)
        restored.load(checkpoint)
        assertEquals(checkpoint, restored.save())
        val list = entries(restored)
        assertEquals("minecraft:lava", list.getCompound(0).getString("fluid"))
        assertEquals("mod:another", list.getCompound(0).getString("dimension"))
        assertEquals(1L, list.getCompound(0).getLong("lag"))
        repeat(4) { manager.tick(levels); restored.tick(levels) }
        assertEquals(manager.save(), restored.save())
        restored.clearVisuals(levels)
        assertEquals(manager.save(), restored.save(), "Teardown must retain saveable parcel ownership")
    }

    @Test fun `missing mod fluid remains paused and survives repeated saves`() {
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(510))
        val saved = manager.save()
        saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("fluid", "missing:liquid")
        manager.load(saved)
        repeat(4) { manager.tick(levels) }
        assertEquals(saved, manager.save())
        assertEquals(2.0, pending(manager))
        assertEquals(0, writes)
    }

    @Test fun `malformed checkpoint is rejected atomically`() {
        val manager = WorldFluidOutflowManager()
        manager.enqueue(level, outflow(510))
        val before = manager.save()
        val bad = before.copy()
        bad.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putDouble("x", Double.NaN)
        assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
        assertEquals(before, manager.save())
        bad.putInt("version", 99)
        assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
        assertEquals(before, manager.save())
    }

    @Test fun `ShipSavedData serializes and restores the actual parcel owner beside pipeline data`() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        val core = mockk<VsiCore>()
        val pipeline = mockk<VsiPipeline>()
        every { vsCore } returns core
        every { core.serializePipeline(pipeline) } returns byteArrayOf(1, 2, 3)
        every { core.newPipeline(any<ByteArray>()) } returns pipeline
        val data = ShipSavedData().apply { this.pipeline = pipeline }
        data.fluidOutflowManager.enqueue(level, outflow(510))
        val saved = data.save(CompoundTag())
        assertArrayEquals(byteArrayOf(1, 2, 3), saved.getByteArray("vs_pipeline"))
        val restored = ShipSavedData.load(saved)
        assertNull(restored.loadingException)
        assertSame(pipeline, restored.pipeline)
        assertEquals(2.0, pending(restored.fluidOutflowManager))
        repeat(2) { restored.fluidOutflowManager.tick(levels) }
        assertEquals(2, sources())
        val secondSave = restored.save(CompoundTag())
        assertEquals(0, ShipSavedData.load(secondSave).fluidOutflowManager.activeParcelCount())
        val legacy = saved.copy().apply { remove("vs_fluid_parcels") }
        assertEquals(0, ShipSavedData.load(legacy).fluidOutflowManager.activeParcelCount())
    }
}
