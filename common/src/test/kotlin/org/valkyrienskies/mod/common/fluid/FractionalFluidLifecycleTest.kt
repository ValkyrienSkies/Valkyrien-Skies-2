package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.joml.Matrix4d
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.mod.common.BlockStateInfo
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.common.fluid.FractionalFluidSources.Position
import org.valkyrienskies.mod.util.relocateBlock
import java.math.BigDecimal

class FractionalFluidLifecycleTest {
    companion object {
        private const val DIM = "minecraft:overworld"
        private const val OTHER = "minecraft:the_nether"
        private const val WATER = "minecraft:water"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>()
    private val other = mockk<ServerLevel>()
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val from = Position(DIM, 1, 2, 3)
    private val to = Position(DIM, 33, 2, 3)
    private val blocks = mutableMapOf<Position, BlockState>()
    private var sources = FractionalFluidSources()
    private var manager = WorldFluidOutflowManager(8, 8)
    private val levels get() = mapOf(DIM to level, OTHER to other)

    @BeforeEach fun setup() {
        mockkObject(BlockStateInfo)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { (server as IShipObjectWorldServerProvider).fluidOutflowManager } answers { manager }
        for ((dimension, world) in levels) {
            every { world.server } returns server
            every { world.dimensionId } returns dimension
            every { world.hasChunkAt(any()) } returns true
            every { world.getShipManagingPos(any<BlockPos>()) } returns null
            every { world.minBuildHeight } returns -64
            every { world.maxBuildHeight } returns 320
            every { world.dimensionType().ultraWarm() } returns false
            every { world.players() } returns emptyList()
            every { world.getBlockState(any()) } answers {
                val p = firstArg<BlockPos>()
                blocks[Position(dimension, p.x, p.y, p.z)] ?: if (p.y <= 0) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
            }
            every { world.setBlock(any(), any(), any()) } answers {
                val p = firstArg<BlockPos>()
                blocks[Position(dimension, p.x, p.y, p.z)] = secondArg()
                true
            }
            every { world.scheduleTick(any<BlockPos>(), any<net.minecraft.world.level.material.Fluid>(), any<Int>()) } just Runs
        }
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun seed(position: Position = from, units: String = "29") {
        assertTrue(sources.setAmount(position.dimension, position.x, position.y, position.z, WATER, BigDecimal(units)))
        blocks[position] = Blocks.WATER.defaultBlockState()
    }
    private fun amount(position: Position) = sources.amount(position.dimension, position.x, position.y, position.z, WATER)
    private fun assertAmount(expected: String?, position: Position = from) {
        if (expected == null) assertNull(amount(position))
        else assertEquals(0, BigDecimal(expected).compareTo(requireNotNull(amount(position))))
    }
    private fun move(destination: Position? = to, sourceVolume: String = "1", destinationVolume: String = "1", copy: Boolean = false) =
        FractionalFluidTransfers.Move(from, destination, BigDecimal(sourceVolume), BigDecimal(destinationVolume),
            DIM, Vector3d(1.5, 2.5, 3.5), Vector3d(2.0, 3.0, 4.0), copy)
    private fun begin(move: FractionalFluidTransfers.Move = move()) =
        requireNotNull(sources.transfers.begin(sequenceOf(move), levels, manager))
    private fun remove(position: Position) {
        blocks.remove(position)
        sources.reconcileFluid(position.dimension, position.x, position.y, position.z, null)
    }
    private fun reload() {
        val bytes = sources.retainedBytes
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        assertEquals(bytes, sources.retainedBytes)
        manager = WorldFluidOutflowManager(8, 8).also { it.load(manager.save()) }
    }
    private fun pendingUnits(): BigDecimal = manager.save().getList("parcels", Tag.TAG_COMPOUND.toInt())
        .fold(BigDecimal.ZERO) { total, entry ->
            val tag = entry as CompoundTag
            total + if (tag.contains("volume_units")) tag.getString("volume_units").toBigDecimal()
                else tag.getString("volume").toBigDecimal() * BigDecimal(255)
        }
    private fun recover() = sources.recoverTransfers(levels, manager)

    @Test fun `finite carriers move without a transient whole-source owner`() {
        for (units in listOf("0", "29", "127.000000000000000001", "255")) {
            sources = FractionalFluidSources(); blocks.clear()
            seed(units = units)
            val scope = begin()
            assertAmount("0"); assertAmount("0", to)
            assertFalse(sources.setAmount(DIM, from.x, from.y, from.z, WATER, BigDecimal(12)))
            remove(from); blocks[to] = Blocks.WATER.defaultBlockState()
            scope.close(); scope.close()
            assertAmount(null); assertAmount(units, to)
            assertEquals(0, manager.activeParcelCount())
            reload(); recover(); assertAmount(units, to)
        }
    }

    @Test fun `interrupted moves recover source destination or exported ownership once`() {
        for (units in listOf("29", "255")) for (sourcePresent in listOf(false, true)) for (targetPresent in listOf(false, true)) {
            sources = FractionalFluidSources(); manager = WorldFluidOutflowManager(8, 8); blocks.clear()
            seed(units = units); begin()
            if (!sourcePresent) remove(from)
            if (targetPresent) blocks[to] = Blocks.WATER.defaultBlockState()
            reload(); recover()
            assertAmount(if (sourcePresent) if (targetPresent) "0" else units else null)
            assertAmount(if (targetPresent) units else null, to)
            val expected = if (!sourcePresent && !targetPresent) BigDecimal(units) else BigDecimal.ZERO
            assertEquals(0, pendingUnits().compareTo(expected))
            reload(); recover()
            assertEquals(0, pendingUnits().compareTo(expected))
            assertTrue(sources.save().getList("transfers", 10).isEmpty())
        }
    }

    @Test fun `scaled move keeps physical volume including nonterminating conversion remainder`() {
        for ((src, dst) in listOf("8" to "0.5", "1" to "3", "0.125" to "8")) {
            sources = FractionalFluidSources(); manager = WorldFluidOutflowManager(8, 8); blocks.clear()
            seed(units = "127.25")
            val scope = begin(move(sourceVolume = src, destinationVolume = dst))
            remove(from); blocks[to] = Blocks.WATER.defaultBlockState(); scope.close()
            val retained = (amount(to) ?: BigDecimal(255)) * BigDecimal(dst)
            if (amount(to) == null) verify(exactly = 1) {
                level.scheduleTick(BlockPos(to.x, to.y, to.z), net.minecraft.world.level.material.Fluids.WATER, any<Int>())
            }
            assertEquals(0, (retained + pendingUnits()).compareTo(BigDecimal("127.25") * BigDecimal(src)))
            reload(); recover()
            assertEquals(0, ((amount(to) ?: BigDecimal(255)) * BigDecimal(dst) + pendingUnits())
                .compareTo(BigDecimal("127.25") * BigDecimal(src)))
        }
    }

    @Test fun `copy preserves the original and credits the scaled destination exactly once`() {
        seed()
        val scope = begin(move(destinationVolume = "8", copy = true))
        blocks[to] = Blocks.WATER.defaultBlockState(); scope.close()
        assertAmount("29"); assertAmount("3.625", to)
        reload(); recover(); assertAmount("29"); assertAmount("3.625", to)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `interrupted copy with both endpoints absent exports the saved original once`() {
        seed()
        begin(move(copy = true))
        remove(from)
        reload(); recover()
        assertAmount(null); assertAmount(null, to)
        assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
        reload(); recover()
        assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
    }

    @Test fun `assembly translation carries waterlogged amounts at the destination scale`() {
        seed()
        blocks[from] = Blocks.OAK_SLAB.defaultBlockState()
            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true)
        val target = mockk<ServerShip>()
        every { target.transform.scaling } returns Vector3d(2.0)
        every { target.shipToWorld } returns Matrix4d().scale(2.0)
        every { target.transform.positionInWorld } returns Vector3d()
        every { target.velocity } returns Vector3d()
        every { target.angularVelocity } returns Vector3d()
        val scope = FractionalFluidLifecycle.beginMove(level, setOf(BlockPos(from.x, from.y, from.z)),
            null, target, BlockPos(from.x, from.y, from.z), BlockPos(to.x, to.y, to.z), false)
        blocks[to] = blocks.getValue(from)
        remove(from); scope!!.close()
        assertAmount(null); assertAmount("3.625", to)
        reload(); recover(); assertAmount("3.625", to)
    }

    @Test fun `bulk clear visits its column and preserves other dimensions and chunk positions`() {
        val positions = listOf(from, from.copy(y = -32), to, from.copy(z = 19), from.copy(dimension = OTHER))
        positions.forEach { seed(it) }
        sources.clearChunk(DIM, 0, 0)
        assertAmount(null); assertAmount(null, from.copy(y = -32))
        positions.drop(2).forEach { assertAmount("29", it) }
        reload(); recover()
        positions.drop(2).forEach { assertAmount("29", it) }
    }

    @Test fun `batch capacity refusal leaves every original and destination unchanged`() {
        sources = FractionalFluidSources(40000)
        seed(); seed(from.copy(x = 2), "17")
        val before = sources.save()
        val bytes = sources.retainedBytes
        assertThrows(FractionalFluidSources.StorageCapacityException::class.java) {
            sources.transfers.begin(sequenceOf(move(), move().copy(from = from.copy(x = 2), to = to.copy(x = 34))), levels, manager)
        }
        assertEquals(before, sources.save()); assertEquals(bytes, sources.retainedBytes)
        val scope = begin(); scope.close()
        assertAmount("29"); assertAmount(null, to)
    }

    @Test fun `same-fluid destinations merge exact amounts and spill only their excess`() {
        for (prior in listOf("50.25", "255")) {
            sources = FractionalFluidSources(); manager = WorldFluidOutflowManager(8, 8); blocks.clear()
            seed(); seed(to, prior)
            val scope = begin()
            assertAmount("0"); assertAmount("0", to)
            remove(from); scope.complete(); scope.close()
            val expected = BigDecimal(29) + BigDecimal(prior)
            val retained = amount(to) ?: BigDecimal(255)
            assertEquals(0, (retained + pendingUnits()).compareTo(expected))
            assertEquals(0, retained.compareTo(minOf(BigDecimal(255), expected)))
            reload(); recover()
            assertEquals(0, ((amount(to) ?: BigDecimal(255)) + pendingUnits()).compareTo(expected))
        }
    }

    @Test fun `occupied dimension destination spills in the destination frame`() {
        seed()
        val target = to.copy(dimension = OTHER)
        seed(target, "255")
        val scope = begin(move(destination = target).copy(destinationPosition = Vector3d(101.5, 80.5, -17.5),
            destinationVelocity = Vector3d(3.0, 4.0, 5.0)))
        remove(from); scope.complete(); scope.close()
        val parcel = manager.save().getList("parcels", 10).getCompound(0)
        assertEquals(OTHER, parcel.getString("dimension"))
        assertEquals(101.5, parcel.getDouble("x"))
        assertEquals(80.5, parcel.getDouble("y"))
        assertEquals(-17.5, parcel.getDouble("z"))
        assertEquals(3.0, parcel.getDouble("vx"))
        assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
        reload(); recover(); assertEquals(1, manager.activeParcelCount())
    }

    @Test fun `legacy unoccupied transfer saves recover without a destination inventory`() {
        seed(); begin(); remove(from); blocks[to] = Blocks.WATER.defaultBlockState()
        val saved = sources.save()
        saved.putInt("version", 2)
        val transfer = saved.getList("transfers", 10).getCompound(0)
        for (key in listOf("target_x", "target_y", "target_z", "target_vx", "target_vy", "target_vz",
                "destination_before", "destination_carrier", "destination_released", "completed")) transfer.remove(key)
        sources = FractionalFluidSources().also { it.load(saved) }
        recover(); assertAmount(null); assertAmount("29", to)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `save before an occupied-destination move restores both original owners`() {
        seed()
        seed(to, "50.25")
        begin(); reload(); recover()
        assertAmount("29"); assertAmount("50.25", to)
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `legacy flowing identities recover interrupted transfers and destination ownership`() {
        for (complete in listOf(false, true)) {
            sources = FractionalFluidSources(); blocks.clear()
            seed(); seed(to, "50.25")
            val scope = begin()
            if (complete) { remove(from); scope.complete() }
            val saved = sources.save()
            for (entry in saved.getList("sources", 10)) {
                (entry as CompoundTag).putString("fluid", "minecraft:flowing_water")
            }
            val transfer = saved.getList("transfers", 10).getCompound(0)
            transfer.putString("fluid", "minecraft:flowing_water")
            transfer.getCompound("destination_before").putString("fluid", "minecraft:flowing_water")
            sources = FractionalFluidSources().also { it.load(saved) }
            recover()
            assertAmount(if (complete) null else "29")
            assertAmount(if (complete) "79.25" else "50.25", to)
            assertEquals(0, manager.activeParcelCount())
            reload(); recover()
            assertAmount(if (complete) null else "29")
            assertAmount(if (complete) "79.25" else "50.25", to)
        }
    }

    @Test fun `copy completion is saved independently of unchanged destination block state`() {
        for (complete in listOf(false, true)) {
            sources = FractionalFluidSources(); blocks.clear()
            seed(); seed(to, "50.25")
            val scope = begin(move(copy = true))
            if (complete) scope.complete()
            reload(); recover()
            assertAmount("29"); assertAmount(if (complete) "79.25" else "50.25", to)
            assertEquals(0, manager.activeParcelCount())
        }
    }

    @Test fun `different fluids keep separate ownership through single-parcel capacity and reload`() {
        manager = WorldFluidOutflowManager(1, 8)
        seed(units = "127.25")
        val lava = "minecraft:lava"
        blocks[to] = Blocks.LAVA.defaultBlockState()
        assertTrue(sources.setAmount(DIM, to.x, to.y, to.z, lava, BigDecimal(100)))
        val scope = begin(move(sourceVolume = "8").copy(destinationPosition = Vector3d(10.5, 1.5, 0.5), destinationVelocity = Vector3d()))
        assertEquals(0, sources.sourceFillAmount(DIM, to.x, to.y, to.z, lava))
        remove(from); blocks[to] = Blocks.WATER.defaultBlockState()
        assertAmount("0", to)
        scope.complete(); scope.close()
        assertAmount("0", to)
        assertEquals(1, manager.activeParcelCount())
        assertEquals(lava, manager.save().getList("parcels", 10).getCompound(0).getString("fluid"))
        val saved = sources.save()
        assertTrue(saved.getList("transfers", 10).getCompound(0).getBoolean("destination_released"))
        sources = FractionalFluidSources().also { it.load(saved) }
        manager = WorldFluidOutflowManager(1, 8).also { it.load(manager.save()) }
        recover(); assertEquals(1, manager.activeParcelCount())
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        recover()
        assertAmount("255", to)
        assertEquals(0, pendingUnits().compareTo(BigDecimal(763)))
        assertEquals(0, requireNotNull(sources.amount(DIM, 10, 1, 0, lava)).compareTo(BigDecimal(100)))
        reload(); recover()
        assertEquals(0, pendingUnits().compareTo(BigDecimal(763)))
    }

    @Test fun `failed occupied move keeps incoming source and exports a displaced different fluid`() {
        seed()
        blocks[to] = Blocks.LAVA.defaultBlockState()
        val scope = begin(move().copy(destinationPosition = Vector3d(33.5, 2.5, 3.5)))
        blocks.remove(to)
        scope.close()
        assertAmount("29"); assertAmount(null, to)
        val parcel = manager.save().getList("parcels", 10).getCompound(0)
        assertEquals("minecraft:lava", parcel.getString("fluid"))
        assertEquals(33.5, parcel.getDouble("x"))
        assertEquals(0, pendingUnits().compareTo(BigDecimal(255)))
        reload(); recover(); assertEquals(1, manager.activeParcelCount())
    }

    @Test fun `parcel capacity refusal retains deletion ownership across reload until queue drains`() {
        manager = WorldFluidOutflowManager(1, 8)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(DIM, WATER, Vector3d(10.5, 1.5, 0.5), Vector3d(), BigDecimal(255)))
        seed(); begin(move(destination = null)).close()
        assertAmount("0"); assertEquals(1, manager.activeParcelCount())
        val saved = sources.save()
        sources = FractionalFluidSources().also { it.load(saved) }
        recover(); assertEquals(1, manager.activeParcelCount())
        manager.tick(levels)
        assertEquals(0, manager.activeParcelCount())
        recover(); assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
        reload(); recover(); assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
    }

    @Test fun `full moved sources cannot be picked up before publication completes`() {
        seed(units = "255")
        val scope = begin()
        remove(from); blocks[to] = Blocks.WATER.defaultBlockState(); scope.complete()
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } throws IllegalStateException("publication")
        assertThrows(IllegalStateException::class.java) { scope.close() }
        assertAmount("255", to)
        val pos = BlockPos(to.x, to.y, to.z)
        assertTrue(FractionalFluidSources.blocksPickup(level, pos))
        reload()
        assertTrue(FractionalFluidSources.blocksPickup(level, pos))
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        recover()
        assertAmount("255", to)
        assertFalse(FractionalFluidSources.blocksPickup(level, pos))
    }

    @Test fun `publication failure after export retains a receipt without exporting twice`() {
        seed()
        val scope = begin(move(destination = null))
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } throws IllegalStateException("publication")
        assertThrows(IllegalStateException::class.java) { scope.close() }
        assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
        reload()
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        recover(); recover()
        assertEquals(0, pendingUnits().compareTo(BigDecimal(29)))
        assertAmount("0")
        assertTrue(sources.save().getList("transfers", 10).isEmpty())
        sources.clearChunk(DIM, 0, 0)
        assertEquals(0, sources.size); assertEquals(0L, sources.retainedBytes)
    }

    @Test fun `dimension copy and raw source clear preserve the locked quantity`() {
        seed()
        val scope = FractionalFluidLifecycle.beginDimensionMove(level, other, mockk {
            every { contains(0, 0) } returns true
        })
        assertNotNull(scope)
        sources.clearChunk(DIM, 0, 0)
        assertAmount("0")
        remove(from); blocks[from.copy(dimension = OTHER)] = Blocks.WATER.defaultBlockState()
        scope!!.close()
        assertAmount(null); assertAmount("29", from.copy(dimension = OTHER))
        reload(); recover(); assertAmount("29", from.copy(dimension = OTHER))
    }

    @Test fun `unloaded transfer endpoints wait without loading chunks or losing saved ownership`() {
        seed(); begin(); remove(from); blocks[to] = Blocks.WATER.defaultBlockState(); reload()
        every { level.hasChunkAt(BlockPos(to.x, to.y, to.z)) } returns false
        val before = sources.save(); recover(); assertEquals(before, sources.save())
        every { level.hasChunkAt(any()) } returns true
        recover(); assertAmount("29", to)
    }

    @Test fun `completed migration resolves after its old chunks unload and copies retain their original`() {
        for (copy in listOf(false, true)) {
            sources = FractionalFluidSources(); blocks.clear()
            seed(); seed(to, "50")
            val scope = begin(move(copy = copy))
            if (!copy) remove(from)
            scope.complete()
            every { level.hasChunkAt(BlockPos(from.x, from.y, from.z)) } returns false
            scope.close()
            assertAmount(if (copy) "29" else "0")
            assertAmount("79", to)
            assertEquals(0, manager.activeParcelCount())
            reload(); recover(); assertAmount("79", to)
            every { level.hasChunkAt(any()) } returns true
        }
        verify(exactly = 0) { level.getBlockState(BlockPos(from.x, from.y, from.z)) }
    }

    @Test fun `legacy block relocation carries exact amounts through ordinary removal callbacks`() {
        seed()
        val sourceChunk = mockk<LevelChunk>()
        val targetChunk = mockk<LevelChunk>()
        every { sourceChunk.level } returns level; every { targetChunk.level } returns level
        every { sourceChunk.pos } returns ChunkPos(0, 0); every { targetChunk.pos } returns ChunkPos(2, 0)
        val sourcePos = BlockPos(from.x, from.y, from.z)
        val targetPos = BlockPos(to.x, to.y, to.z)
        every { sourceChunk.getBlockState(sourcePos) } returns Blocks.WATER.defaultBlockState()
        every { sourceChunk.getBlockEntity(sourcePos) } returns null
        every { sourceChunk.setBlockState(sourcePos, any(), false) } answers { remove(from); Blocks.WATER.defaultBlockState() }
        every { targetChunk.setBlockState(targetPos, any(), false) } answers { blocks[to] = secondArg(); Blocks.AIR.defaultBlockState() }
        relocateBlock(sourceChunk, sourcePos, targetChunk, targetPos, false, null)
        assertAmount(null); assertAmount("29", to)
        verify { BlockStateInfo.onSourceAmountChanged(level, targetPos, Blocks.WATER.defaultBlockState(), 29) }
    }

    @Test fun `deletion captures the ship frame and can wait for an unavailable dimension`() {
        seed()
        val ship = mockk<ServerShip>()
        every { ship.chunkClaimDimension } returns DIM
        every { ship.activeChunksSet.contains(0, 0) } returns true
        every { ship.transform.scaling } returns Vector3d(2.0)
        every { ship.transform.positionInWorld } returns Vector3d(100.0, 0.0, 0.0)
        every { ship.shipToWorld } returns Matrix4d().translation(100.0, 0.0, 0.0).scale(2.0)
        every { ship.velocity } returns Vector3d(3.0, 0.0, 0.0)
        every { ship.angularVelocity } returns Vector3d(0.0, 1.0, 0.0)
        FractionalFluidLifecycle.releaseShip(sources, emptyMap(), manager, ship)
        assertAmount("0"); assertEquals(0, manager.activeParcelCount())
        reload(); recover()
        assertEquals(0, pendingUnits().compareTo(BigDecimal(232)))
        val parcel = manager.save().getList("parcels", 10).getCompound(0)
        assertEquals(103.0, parcel.getDouble("x"))
        assertEquals(5.0, parcel.getDouble("y"))
        assertEquals(7.0, parcel.getDouble("z"))
        FractionalFluidLifecycle.releaseShip(sources, levels, manager, ship)
        assertEquals(1, manager.activeParcelCount())
    }
}
