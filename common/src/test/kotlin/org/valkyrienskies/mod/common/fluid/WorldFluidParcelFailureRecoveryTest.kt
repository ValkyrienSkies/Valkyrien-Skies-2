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

class WorldFluidParcelFailureRecoveryTest {
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
    private val scheduled = mutableSetOf<Pair<BlockPos, net.minecraft.world.level.material.Fluid>>()
    private val tickQueue = mockk<net.minecraft.world.ticks.LevelTicks<net.minecraft.world.level.material.Fluid>>(relaxed = true)
    private val levels get() = mapOf(DIMENSION to level)

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { MassDatapackResolver.getFlowingFluid(18) } returns Fluids.LAVA
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } answers { loaded(firstArg()) }
        every { level.getBlockState(any()) } answers { blocks[firstArg<BlockPos>()] ?: terrain(firstArg()) }
        every { level.players() } returns emptyList()
        every { (level as net.minecraft.world.level.Level).getFluidTicks() } returns tickQueue
        assertSame(tickQueue, level.getFluidTicks(), "Concrete getter must expose the queue used by the manager")
        every { tickQueue.hasScheduledTick(any(), any()) } answers { scheduled.contains(firstArg<BlockPos>() to secondArg<net.minecraft.world.level.material.Fluid>()) }
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

    @Test fun `unknown block payload is budgeted before copy and survives two independent reloads`() {
        val manager = WorldFluidOutflowManager(4, 4)
        val empty = manager.parcelStorageBytes()
        manager.enqueue(level, outflow(510))
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("hold journal")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val saved = manager.save()
        val intent = saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement")
        val originalBefore = intent.getCompound("before")
        var copies = 0
        val before = object : CompoundTag() {
            override fun copy(): CompoundTag { copies++; return super.copy() }
        }
        for (key in originalBefore.allKeys) before.put(key, requireNotNull(originalBefore.get(key)))
        intent.put("before", before)
        before.putString("Name", "missing:state")
        before.put("nested", net.minecraft.nbt.ListTag().apply {
            add(CompoundTag().apply {
                putByteArray("bytes", ByteArray(8192) { it.toByte() })
                putIntArray("ints", IntArray(4096) { it })
                putLongArray("longs", LongArray(2048) { it.toLong() })
                putString("text", "variable".repeat(1024))
            })
        })
        val prior = manager.save()
        val retained = manager.parcelStorageBytes()
        manager.maxParcelStorageBytes = retained + 32768
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { manager.load(saved) }
        assertEquals(0, copies, "Capacity must be checked before copying the variable payload")
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(prior, manager.save())
        var restored = WorldFluidOutflowManager(4, 4)
        restored.load(saved)
        assertEquals(1, copies)
        val withPayload = restored.parcelStorageBytes()
        assertTrue(withPayload > retained + 40000)
        repeat(2) {
            val snapshot = restored.save()
            val independent = snapshot.copy()
            restored = WorldFluidOutflowManager(4, 4).also { it.load(snapshot) }
            snapshot.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
                .getCompound("placement").getCompound("before").getList("nested", Tag.TAG_COMPOUND.toInt())
                .getCompound(0).getLongArray("longs")[0] = -999L
            assertEquals(independent, restored.save())
            assertEquals(withPayload, restored.parcelStorageBytes())
            restored.tick(levels)
            assertEquals(2.0, pending(restored))
        }
        restored.load(WorldFluidOutflowManager().save())
        assertEquals(empty, restored.parcelStorageBytes())
    }

    @Test fun `overdeep block NBT is rejected before recursive copy without changing ownership`() {
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(510))
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("hold journal")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val original = manager.save()
        val retained = manager.parcelStorageBytes()
        val invalid = original.copy()
        var node = invalid.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
            .getCompound("placement").getCompound("before")
        node.putString("Name", "missing:deep")
        repeat(514) { node = CompoundTag().also { child -> node.put("child", child) } }
        assertThrows(IllegalArgumentException::class.java) { manager.load(invalid) }
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(original, manager.save())
    }

    @Test fun `custom placement encoding is admitted before world writes and frozen for retry`() {
        val property = mockk<net.minecraft.world.level.block.state.properties.Property<String>>()
        val secondProperty = mockk<net.minecraft.world.level.block.state.properties.Property<String>>()
        val state = mockk<BlockState>()
        every { state.block } returns Blocks.GRASS
        every { state.fluidState } returns Fluids.EMPTY.defaultFluidState()
        every { state.canBeReplaced() } returns true
        every { state.properties } returns listOf(property, secondProperty)
        every { property.name } returns "custom"
        every { secondProperty.name } returns "second"
        every { state.getValue(property) } returns "value"
        every { state.getValue(secondProperty) } returns "value"
        var serialized = "x".repeat(8192)
        every { property.getName("value") } answers { serialized }
        every { secondProperty.getName("value") } answers { serialized }
        blocks[BlockPos(0, 1, 0)] = state
        val manager = WorldFluidOutflowManager(1, 1)
        val empty = manager.parcelStorageBytes()
        manager.enqueue(level, outflow())
        val retained = manager.parcelStorageBytes()
        manager.maxParcelStorageBytes = retained
        assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { manager.tick(levels) }
        assertEquals(0, writes)
        assertEquals(1.0, pending(manager))
        assertEquals(retained, manager.parcelStorageBytes(), "Uncommitted scratch must be refunded")
        manager.maxParcelStorageBytes = retained + 80000
        repeat(2) {
            assertThrows(WorldFluidOutflowManager.StorageCapacityException::class.java) { manager.tick(levels) }
            assertEquals(0, writes)
            assertEquals(retained, manager.parcelStorageBytes())
        }
        assertTrue(manager.peakParcelStorageBytes() > retained, "The first property must exercise partial admission")
        manager.maxParcelStorageBytes = retained + 262144
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("hold custom intent")
        val writeFailure = assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        assertEquals("hold custom intent", writeFailure.message)
        val charged = manager.parcelStorageBytes()
        assertTrue(charged > retained)
        val saved = manager.save()
        assertEquals(serialized, saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
            .getCompound("placement").getCompound("before").getCompound("Properties").getString("custom"))
        serialized = "changed after admission"
        assertEquals(saved, manager.save(), "Save must use admitted, frozen state data")
        every { level.setBlock(any(), any(), any()) } answers {
            blocks[firstArg<BlockPos>().immutable()] = secondArg(); true
        }
        manager.tick(levels)
        assertEquals(1, sources())
        assertEquals(empty, manager.parcelStorageBytes())
    }

    @Test fun `visuals skip at capacity but partial delivery retains prepaid restoration storage`() {
        val manager = WorldFluidOutflowManager(8, 8)
        manager.enqueue(level, outflow(y = 100.5))
        val retained = manager.parcelStorageBytes()
        var sent = 0
        var fail = false
        val clients = listOf(mutableMapOf<BlockPos, BlockState>(), mutableMapOf<BlockPos, BlockState>())
        visualClients { index, packet ->
            sent++
            clients[index][packet.pos] = packet.blockState
            if (fail && index == 1) throw IllegalStateException("partial visual")
            if (index == 0 && !packet.blockState.fluidState.isEmpty) {
                assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow()))
            }
        }
        manager.maxParcelStorageBytes = retained
        manager.tick(levels)
        assertEquals(0, sent)
        assertEquals(1.0, pending(manager))
        assertEquals(retained, manager.parcelStorageBytes())
        manager.maxParcelStorageBytes += 768 // Two recipients plus fixed packet/collection scratch.
        fail = true
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        assertEquals(manager.maxParcelStorageBytes, manager.parcelStorageBytes())
        assertThrows(IllegalStateException::class.java) { manager.clearVisuals(levels) }
        assertEquals(manager.maxParcelStorageBytes, manager.parcelStorageBytes())
        fail = false
        manager.clearVisuals(levels)
        assertEquals(retained, manager.parcelStorageBytes())
        assertEquals(1.0, pending(manager))
        assertTrue(clients.all { client -> client.isNotEmpty() && client.values.all { it.fluidState.isEmpty } })
        assertTrue(manager.peakParcelStorageBytes() <= manager.maxParcelStorageBytes)
    }

    @Test fun `visual restoration and redisplay cannot hide exhausted source storage`() {
        val manager = WorldFluidOutflowManager(2, 2, 1L)
        manager.enqueue(level, outflow(510))
        manager.enqueue(level, outflow(y = 100.5))
        loaded = { it.y != 99 } // Airborne owner pauses, but its current cell can show an overlay.
        var packets = 0
        visualClients { _, _ -> packets++ }
        repeat(3) {
            val previousPackets = packets
            assertThrows(WorldFluidOutflowManager.SourceStorageCapacityException::class.java) { manager.tick(levels) }
            assertTrue(packets > previousPackets)
            assertEquals(3.0, pending(manager))
            assertEquals(0, writes)
        }
        manager.clearVisuals(levels)
        manager.maxSourceStorageBytes = 48
        repeat(2) { manager.tick(levels) }
        assertEquals(2, sources())
        assertEquals(1.0, pending(manager))
    }


    private fun restore(saved: CompoundTag): WorldFluidOutflowManager =
        WorldFluidOutflowManager(1, 1) { 0L }.also { it.load(saved) }

    @Test fun `placement exceptions recover before and after application across a real save load`() {
        for (pressure in listOf(false, true)) for (applied in listOf(false, true)) {
            blocks.clear(); loaded = { true }; writes = 0
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow(510))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            every { level.setBlock(any(), any(), any()) } answers {
                if (applied) { blocks[firstArg<BlockPos>().immutable()] = secondArg(); writes++ }
                throw IllegalStateException("placement")
            }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            assertEquals(1, manager.activeParcelCount())
            assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow(x = 8.5)))
            assertTrue(entries(manager).getCompound(0).contains("placement"))
            assertEquals(2.0, pending(manager))
            every { level.setBlock(any(), any(), any()) } answers {
                blocks[firstArg<BlockPos>().immutable()] = secondArg(); writes++; true
            }
            val restored = restore(manager.save())
            restored.servicePressure(levels)
            assertEquals(1, sources()); assertEquals(1.0, pending(restored)); assertEquals(1, writes)
            assertFalse(entries(restored).getCompound(0).contains("placement"))
            restored.tick(levels)
            assertEquals(2, sources()); assertEquals(0.0, pending(restored)); assertEquals(2, writes)
        }
    }

    @Test fun `scheduling exceptions retain a zero volume receipt and never repeat placement or scheduling`() {
        for (pressure in listOf(false, true)) for (applied in listOf(false, true)) {
            blocks.clear(); scheduled.clear(); loaded = { true }; writes = 0; fluidTicks = 0
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow())
            val initial = manager.save()
            initial.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putString("volume", "0.5")
            manager.load(initial)
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            every { level.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } answers {
                if (applied) { scheduled.add(firstArg<BlockPos>().immutable() to secondArg()); fluidTicks++ }
                throw IllegalStateException("scheduling")
            }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            assertEquals(1, manager.activeParcelCount()); assertEquals(0.0, pending(manager))
            assertTrue(entries(manager).getCompound(0).getCompound("placement").getBoolean("scheduled_only"))
            assertEquals(4, blocks[BlockPos(0, 1, 0)]!!.fluidState.amount)
            every { level.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } answers {
                scheduled.add(firstArg<BlockPos>().immutable() to secondArg()); fluidTicks++
            }
            val restored = restore(manager.save())
            restored.servicePressure(levels)
            assertEquals(0, restored.activeParcelCount()); assertEquals(1, writes); assertEquals(1, fluidTicks)
            assertEquals(4, blocks[BlockPos(0, 1, 0)]!!.fluidState.amount)
        }
    }

    @Test fun `interrupted pure motion rolls back and catches up exactly after save load`() {
        for (pressure in listOf(false, true)) {
            terrain = { Blocks.AIR.defaultBlockState() }; loaded = { true }
            val reference = WorldFluidOutflowManager(1, 1)
            reference.enqueue(level, outflow(y = 100.5, vx = 20.0)); reference.tick(levels)
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow(y = 100.5, vx = 20.0))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            loaded = { if (it.x >= 1) throw IllegalStateException("motion") else true }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            val partial = entries(manager).getCompound(0)
            assertEquals(100.5, partial.getDouble("y")); assertEquals(0.0, partial.getDouble("vy"))
            assertEquals(1L, partial.getLong("lag"))
            loaded = { true }
            val restored = restore(manager.save()); restored.servicePressure(levels)
            assertEquals(reference.save(), restored.save())
        }
    }

    @Test fun `later catch-up failure commits preceding completed ticks but not the interrupted tick`() {
        for (pressure in listOf(false, true)) {
            terrain = { Blocks.AIR.defaultBlockState() }; loaded = { true }
            val reference = WorldFluidOutflowManager(1, 1)
            reference.enqueue(level, outflow(y = 100.5)); repeat(3) { reference.tick(levels) }
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow(y = 100.5))
            if (pressure) { manager.servicePressure(levels); now += 150_000_000L }
            else {
                val initial = manager.save(); initial.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).putLong("lag", 2L)
                manager.load(initial)
            }
            var reads = 0
            terrain = { if (++reads == 3) throw IllegalStateException("catch-up") else Blocks.AIR.defaultBlockState() }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            val partial = entries(manager).getCompound(0)
            assertEquals(100.5 - 0.024525, partial.getDouble("y"), 1e-12)
            assertEquals(-0.4905, partial.getDouble("vy"), 1e-12); assertEquals(2L, partial.getLong("lag"))
            terrain = { Blocks.AIR.defaultBlockState() }
            val restored = restore(manager.save()); restored.servicePressure(levels)
            assertEquals(reference.save(), restored.save())
        }
    }

    @Test fun `callback reentry cannot steal capacity replace ownership or advance time twice`() {
        for (pressure in listOf(false, true)) {
            loaded = { true }; terrain = { Blocks.AIR.defaultBlockState() }
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow(510, y = 100.5))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            var snapshot: CompoundTag? = null
            loaded = {
                if (snapshot == null) {
                    snapshot = manager.save()
                    assertEquals(2.0, pending(manager)); assertEquals(1, manager.activeParcelCount())
                    assertEquals(VsiFluidOutflowResult.RETRY_LATER, manager.enqueue(level, outflow(x = 8.5)))
                    assertThrows(IllegalStateException::class.java) { manager.load(snapshot!!) }
                    manager.tick(levels); assertFalse(manager.servicePressure(levels))
                }
                true
            }
            if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            val completed = manager.save()
            loaded = { true }
            val restored = restore(snapshot!!); restored.servicePressure(levels)
            assertEquals(completed, restored.save())
        }
    }

    @Test fun `nested saves retain placement intent before and after a successful write`() {
        for (pressure in listOf(false, true)) for (after in listOf(false, true)) {
            blocks.clear(); loaded = { true }; writes = 0
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            manager.enqueue(level, outflow(510))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            var snapshot: CompoundTag? = null
            every { level.setBlock(any(), any(), any()) } answers {
                if (!after) snapshot = manager.save()
                blocks[firstArg<BlockPos>().immutable()] = secondArg(); writes++
                if (after) snapshot = manager.save()
                true
            }
            if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            val completed = manager.save()
            assertTrue(snapshot!!.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).contains("placement"))
            val restored = restore(snapshot!!); restored.servicePressure(levels)
            assertEquals(completed, restored.save()); assertEquals(1, writes)
        }
    }

    @Test fun `unavailable or conflicting placement stays owned and malformed intent load is atomic`() {
        val manager = WorldFluidOutflowManager(1, 1)
        manager.enqueue(level, outflow(510))
        every { level.setBlock(any(), any(), any()) } throws IllegalStateException("placement")
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val saved = manager.save()
        val bad = saved.copy()
        bad.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement").putString("deposited", "-1")
        assertThrows(IllegalArgumentException::class.java) { manager.load(bad) }
        assertEquals(saved, manager.save())
        val wrongBlock = saved.copy()
        wrongBlock.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement")
            .put("after", net.minecraft.nbt.NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()))
        assertThrows(IllegalArgumentException::class.java) { manager.load(wrongBlock) }
        assertEquals(saved, manager.save())
        val restored = restore(saved)
        loaded = { false }; restored.servicePressure(levels)
        assertEquals(saved, restored.save()); assertEquals(2.0, pending(restored))
        loaded = { true }; blocks[BlockPos(0, 1, 0)] = Blocks.STONE.defaultBlockState()
        restored.servicePressure(levels)
        assertEquals(saved, restored.save()); assertEquals(2.0, pending(restored))
        blocks.clear()
        every { level.setBlock(any(), any(), any()) } answers { blocks[firstArg<BlockPos>().immutable()] = secondArg(); true }
        restored.servicePressure(levels)
        assertEquals(1, sources()); assertEquals(1.0, pending(restored))
    }

    @Test fun `shared staging preserves each owner when a later parcel callback fails`() {
        for (pressure in listOf(false, true)) {
            terrain = { Blocks.AIR.defaultBlockState() }; loaded = { true }
            val reference = WorldFluidOutflowManager(2, 2)
            for (x in listOf(0.5, 8.5)) reference.enqueue(level, outflow(x = x, y = 100.5))
            reference.tick(levels)
            var now = 0L
            val manager = WorldFluidOutflowManager(2, 2) { now }
            for (x in listOf(0.5, 8.5)) manager.enqueue(level, outflow(x = x, y = 100.5))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            loaded = { if (it.x >= 8) throw IllegalStateException("second parcel") else true }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            val snapshot = manager.save()
            val partial = entries(manager).map { it as CompoundTag }.sortedBy { it.getDouble("x") }
            assertEquals(2, partial.size)
            assertEquals(100.5 - 0.024525, partial[0].getDouble("y"), 1e-12)
            assertEquals(0L, partial[0].getLong("lag"))
            assertEquals(100.5, partial[1].getDouble("y")); assertEquals(0.0, partial[1].getDouble("vy"))
            assertEquals(1L, partial[1].getLong("lag"))
            loaded = { true }
            val restored = WorldFluidOutflowManager(2, 2) { 0L }.also { it.load(snapshot) }
            manager.servicePressure(levels); restored.servicePressure(levels)
            val expected = entries(reference).map { it as CompoundTag }.sortedBy { it.getDouble("x") }
            assertEquals(expected, entries(manager).map { it as CompoundTag }.sortedBy { it.getDouble("x") })
            assertEquals(expected, entries(restored).map { it as CompoundTag }.sortedBy { it.getDouble("x") })
        }
    }

    @Test fun `unknown placement block states retain original serialized ownership`() {
        for (key in listOf("before", "after")) {
            blocks.clear(); loaded = { true }; writes = 0
            val manager = WorldFluidOutflowManager(1, 1)
            manager.enqueue(level, outflow(510))
            every { level.setBlock(any(), any(), any()) } throws IllegalStateException("placement")
            assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
            val saved = manager.save()
            saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
                .getCompound("placement").getCompound(key).putString("Name", "missing:parcel_block")
            val restored = restore(saved)
            assertEquals(saved, restored.save())
            repeat(3) { restored.servicePressure(levels) }
            assertEquals(saved, restored.save()); assertEquals(2.0, pending(restored)); assertEquals(0, writes)
            val independent = restored.save()
            saved.getList("parcels", Tag.TAG_COMPOUND.toInt()).getCompound(0)
                .getCompound("placement").getCompound(key).putString("Name", "missing:other")
            assertEquals(independent, restored.save(), "Loaded unknown tags must own their saved state")
        }
    }
    @Test fun `placement does not reuse an observation across a later world callback`() {
        for (pressure in listOf(false, true)) {
            blocks.clear()
            val target = BlockPos(0, 1, 0)
            var now = 0L
            val manager = WorldFluidOutflowManager(1, 1) { now }
            assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, outflow()))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            var edited = false
            every { level.getBlockState(any()) } answers {
                val pos = firstArg<BlockPos>()
                if (pos == target.below() && !edited) {
                    edited = true
                    blocks[target] = Blocks.STONE.defaultBlockState()
                }
                blocks[pos] ?: terrain(pos)
            }
            if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            assertTrue(edited)
            assertEquals(Blocks.STONE.defaultBlockState(), blocks[target], "The later callback's terrain edit must survive")
            assertEquals(1.0, pending(manager), "Unplaced water remains owned")
        }
    }

    private fun visualClients(onSend: (Int, net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket) -> Unit) {
        val players = (0..1).map { index ->
            val player = mockk<net.minecraft.server.level.ServerPlayer>()
            val connection = mockk<net.minecraft.server.network.ServerGamePacketListenerImpl>()
            player.connection = connection
            every { player.getUUID() } returns java.util.UUID(0L,index.toLong()+1L)
            every { player.distanceToSqr(any<Double>(), any<Double>(), any<Double>()) } returns 0.0
            every { connection.send(any<net.minecraft.network.protocol.Packet<*>>()) } answers {
                onSend(index, firstArg() as net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket)
            }
            player
        }
        every { level.players() } returns players
    }

    @Test fun `partial visual delivery remains recoverable before and after the second client applies it`() {
        for (pressure in listOf(false,true)) for (applied in listOf(false,true)) {
            val clients = listOf(mutableMapOf<BlockPos,BlockState>(), mutableMapOf<BlockPos,BlockState>())
            var fail = true
            visualClients { i, packet ->
                if (i == 1 && fail) {
                    if (applied) clients[i][packet.pos] = packet.blockState
                    throw IllegalStateException("visual delivery")
                }
                clients[i][packet.pos] = packet.blockState
            }
            var now = 0L
            val manager = WorldFluidOutflowManager(1,1) { now }
            manager.enqueue(level, outflow(y=100.5))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L }
            assertThrows(IllegalStateException::class.java) {
                if (pressure) manager.servicePressure(levels) else manager.tick(levels)
            }
            assertEquals(1, entries(manager).size, "Delivery failure cannot discard the fluid owner")
            assertTrue(clients[0].values.any { !it.fluidState.isEmpty })
            fail = false
            manager.clearVisuals(levels)
            for (client in clients) assertTrue(client.values.all { it.fluidState.isEmpty }, "Every possibly delivered overlay is restored")
        }
    }

    @Test fun `partial visual restoration retries all intended recipients`() {
        for (applied in listOf(false,true)) {
            val clients = listOf(mutableMapOf<BlockPos,BlockState>(), mutableMapOf<BlockPos,BlockState>())
            var fail = false
            visualClients { i, packet ->
                if (i == 1 && fail) {
                    if (applied) clients[i][packet.pos] = packet.blockState
                    throw IllegalStateException("visual restoration")
                }
                clients[i][packet.pos] = packet.blockState
            }
            val manager = WorldFluidOutflowManager(1,1)
            manager.enqueue(level, outflow(y=100.5)); manager.tick(levels)
            fail = true
            assertThrows(IllegalStateException::class.java) { manager.clearVisuals(levels) }
            assertEquals(1, entries(manager).size)
            fail = false; manager.clearVisuals(levels)
            for (client in clients) assertTrue(client.values.all { it.fluidState.isEmpty })
        }
    }

    @Test fun `clear requested inside packet delivery takes effect after the in flight send completes`() {
        for (pressure in listOf(false,true)) {
            val clients = listOf(mutableMapOf<BlockPos,BlockState>(), mutableMapOf<BlockPos,BlockState>())
            var now = 0L
            val manager = WorldFluidOutflowManager(1,1) { now }
            visualClients { i, packet ->
                clients[i][packet.pos] = packet.blockState
                if (i == 0 && !packet.blockState.fluidState.isEmpty) manager.clearVisuals(levels)
            }
            manager.enqueue(level, outflow(y=100.5))
            if (pressure) { manager.servicePressure(levels); now += 50_000_000L; manager.servicePressure(levels) }
            else manager.tick(levels)
            assertEquals(1, entries(manager).size)
            for (client in clients) assertTrue(client.isNotEmpty() && client.values.all { it.fluidState.isEmpty })
        }
    }

    @Test fun `visual cleanup callbacks cannot replace parcel ownership`() {
        val manager = WorldFluidOutflowManager(1,1)
        val empty = WorldFluidOutflowManager().save()
        var attempted = false
        visualClients { i, packet ->
            if (i == 0 && packet.blockState.fluidState.isEmpty) {
                attempted = true
                assertThrows(IllegalStateException::class.java) { manager.load(empty) }
            }
        }
        manager.enqueue(level, outflow(y=100.5)); manager.tick(levels)
        manager.clearVisuals(levels)
        assertTrue(attempted); assertEquals(1, entries(manager).size)
    }

    @Test fun `legacy scheduling receipts without an attempt marker still avoid duplicate enqueue`() {
        val manager = WorldFluidOutflowManager(1,1)
        manager.enqueue(level, outflow(fill=128))
        every { level.scheduleTick(any<BlockPos>(),any<net.minecraft.world.level.material.Fluid>(),any<Int>()) } answers {
            scheduled.add(firstArg<BlockPos>().immutable() to secondArg()); fluidTicks++
            throw IllegalStateException("applied scheduling")
        }
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        val saved = manager.save()
        val intent = saved.getList("parcels",Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement")
        assertTrue(intent.getBoolean("schedule_attempted")); intent.remove("schedule_attempted")
        every { level.scheduleTick(any<BlockPos>(),any<net.minecraft.world.level.material.Fluid>(),any<Int>()) } answers { fluidTicks++ }
        val restored = restore(saved)
        restored.servicePressure(levels)
        assertEquals(1,fluidTicks); assertEquals(0,restored.activeParcelCount())
    }

    @Test fun `deferred cleanup preserves the primary exception and can retry its own failure`() {
        val clients = listOf(mutableMapOf<BlockPos,BlockState>(),mutableMapOf<BlockPos,BlockState>())
        val manager = WorldFluidOutflowManager(1,1)
        val primary = IllegalStateException("delivery primary")
        val cleanup = IllegalStateException("cleanup secondary")
        var fail = true
        visualClients { i, packet ->
            clients[i][packet.pos] = packet.blockState
            if (fail && i == 0 && !packet.blockState.fluidState.isEmpty) {
                manager.clearVisuals(levels); throw primary
            }
            if (fail && i == 1 && packet.blockState.fluidState.isEmpty) throw cleanup
        }
        manager.enqueue(level,outflow(y=100.5))
        val actual = assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        assertSame(primary,actual); assertEquals(listOf(cleanup),actual.suppressed.toList())
        assertEquals(1,entries(manager).size)
        fail = false; manager.clearVisuals(levels)
        assertTrue(clients.all { client -> client.values.all { it.fluidState.isEmpty } })
    }

    @Test fun `placement journal preserves full width coordinates across failed writes and restore`() {
        for (x in listOf(Int.MIN_VALUE,Int.MAX_VALUE)) for (z in listOf(Int.MIN_VALUE,Int.MAX_VALUE)) {
            blocks.clear();scheduled.clear();writes=0
            val pos=BlockPos(x,1,z)
            val event=VsiFluidOutflow(1,1,1,DIMENSION,7,x.toDouble()+0.5,1.5,z.toDouble()+0.5,
                0.0,-1.0,0.0,0.0,0.0,0.0,17,255)
            val manager=WorldFluidOutflowManager(1,1)
            manager.enqueue(level,event)
            every { level.setBlock(any(),any(),any()) } answers {
                assertEquals(pos,firstArg<BlockPos>())
                blocks[firstArg<BlockPos>().immutable()]=secondArg();writes++
                throw IllegalStateException("applied full width placement")
            }
            assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
            val saved=manager.save()
            val intent=saved.getList("parcels",Tag.TAG_COMPOUND.toInt()).getCompound(0).getCompound("placement")
            assertArrayEquals(intArrayOf(x,1,z),intent.getIntArray("pos"))
            val restored=restore(saved)
            restored.servicePressure(levels)
            assertEquals(1,writes);assertEquals(0,restored.activeParcelCount())
            assertTrue(blocks[pos]!!.fluidState.isSource)
        }
    }

}
