package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidOutflow
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.*
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import java.math.BigDecimal

class WorldFluidFeedbackTest {
    companion object {
        private const val DIMENSION = "minecraft:overworld"
        private const val WATER = "minecraft:water"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>(relaxed = true)
    private val blocks = mutableMapOf<BlockPos, BlockState>()
    private var sources = FractionalFluidSources()
    private var manager = WorldFluidOutflowManager()
    private var vanillaQuery = false
    private val waterType = mockk<VsiBlockType>()
    private val airType = mockk<VsiBlockType>()
    private val pos = BlockPos(0, 1, 0)

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver, BlockStateInfo)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { level.server } returns server
        every { level.dimensionId } returns DIMENSION
        every { level.getShipManagingPos(any<BlockPos>()) } returns null
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } returns true
        every { level.players() } returns emptyList()
        every { level.getBlockState(any()) } answers {
            val at = firstArg<BlockPos>()
            val state = blocks[at] ?: Blocks.AIR.defaultBlockState()
            if (vanillaQuery) FractionalFluidSources.vanillaFlowState(level, at, state) else state
        }
        every { level.getFluidState(any()) } answers { level.getBlockState(firstArg()).fluidState }
        every { level.setBlock(any(), any(), any()) } answers {
            val at = firstArg<BlockPos>().immutable()
            val state = secondArg<BlockState>()
            blocks[at] = state
            sources.reconcileFluid(DIMENSION, at.x, at.y, at.z, FractionalFluidSources.fluidName(state))
            true
        }
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.WATER
        every { MassDatapackResolver.getLiquidStateId(any()) } answers {
            if (firstArg<BlockState>().fluidState.isEmpty) null else 17
        }
        every { MassDatapackResolver.getBlockStateType(any()) } answers {
            if (firstArg<BlockState>().fluidState.isEmpty) airType else waterType
        }
        blocks[pos.below()] = Blocks.STONE.defaultBlockState()
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun settle(volume: Double, at: BlockPos = pos, reloadBeforeLanding: Boolean = false) {
        blocks[at.below()] = Blocks.STONE.defaultBlockState()
        val event = VsiFluidOutflow(1, 1, 1, DIMENSION, 7, at.x + 0.5, at.y + 0.5, at.z + 0.5,
            0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 17, 255).copy(worldVolume = volume)
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueue(level, event))
        if (reloadBeforeLanding) reload()
        repeat(4) { manager.tick(mapOf(DIMENSION to level)) }
        assertEquals(0, manager.activeParcelCount())
    }

    private fun reload() {
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        manager = WorldFluidOutflowManager().also { it.load(manager.save()) }
    }

    @Test fun `tiny scattered droplets do not create water blocks or saved owners`() {
        val positions = (0 until 64).map { pos.offset(it * 2, 0, 0) }
        positions.forEach { settle(0.001, it) }
        reload()
        positions.forEach { at ->
            assertTrue(level.getBlockState(at).isAir)
            assertFalse(FractionalFluidSources.owns(level, at))
        }
        assertEquals(0, sources.size)
        assertEquals(0L, sources.retainedBytes)
    }

    @Test fun `flowing water outflows settle with canonical finite ownership`() {
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.FLOWING_WATER
        settle(0.125)
        reload()
        assertEquals(BigDecimal("31.875"), sources.amount(DIMENSION, pos.x, pos.y, pos.z, WATER))
        assertEquals(31, FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z)!!.sourceFillAmount)
        blocks.remove(pos.below())
        assertTrue(newLiquidBelow().isEmpty)
    }

    @Test fun `a droplet just below the minimum survives unloaded transit then disappears on landing`() {
        val units = BigDecimal("31.87499999999999999999999")
        every { level.hasChunkAt(any()) } returns false
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(DIMENSION, WATER,
            org.joml.Vector3d(0.5, 1.5, 0.5), org.joml.Vector3d(), units))
        manager.tick(mapOf(DIMENSION to level))
        assertEquals(1, manager.activeParcelCount())
        reload()
        every { level.hasChunkAt(any()) } returns true
        repeat(4) { manager.tick(mapOf(DIMENSION to level)) }
        assertEquals(0, manager.activeParcelCount())
        assertTrue(level.getBlockState(pos).isAir)
        assertEquals(0, sources.size)
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
        reload()
        assertEquals(0, manager.activeParcelCount())
        assertEquals(0, sources.size)
    }

    @Test fun `source and flowing variants preserve partial and full deposits through parcel reload`() {
        for (fluid in listOf(Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA, Fluids.FLOWING_LAVA)) {
            for (volume in listOf(0.001, 0.1, 0.125, 0.249, 0.501, 1.0)) {
                sources = FractionalFluidSources()
                manager = WorldFluidOutflowManager()
                blocks.clear()
                every { MassDatapackResolver.getFlowingFluid(17) } returns fluid
                settle(volume, reloadBeforeLanding = true)
                reload()
                val name = if (fluid.isSame(Fluids.WATER)) WATER else "minecraft:lava"
                if (volume < 0.125) {
                    assertTrue(level.getBlockState(pos).isAir)
                    assertEquals(0, sources.size)
                    continue
                }
                val expected = BigDecimal.valueOf(volume) * BigDecimal(255)
                assertEquals(0, expected.compareTo(sources.amount(DIMENSION, pos.x, pos.y, pos.z, name)))
                assertEquals((volume * 8).toInt(), level.getBlockState(pos).fluidState.amount)
                assertEquals(expected.toInt(), FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z)!!.sourceFillAmount)
                assertTrue(FractionalFluidSources.owns(level, pos))
                blocks.remove(pos.below())
                assertTrue(newLiquidBelow(fluid).isEmpty)
            }
        }
    }

    @Test fun `fractional spray can be withdrawn and re-emitted without increasing its amount`() {
        every { MassDatapackResolver.getFlowingFluid(17) } returns Fluids.FLOWING_WATER
        val initial = BigDecimal("0.501") * BigDecimal(255)
        settle(0.501)
        repeat(16) { cycle ->
            reload()
            val observed = requireNotNull(FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z))
            assertEquals(127, observed.sourceFillAmount)
            val withdrawal = VsiFluidSourceWithdrawal(cycle + 1L, 1, DIMENSION, 9, true, 7,
                pos.x, pos.y, pos.z, 17, observed.sourceFillAmount)
            assertEquals(VsiFluidSourceWithdrawalResult.APPLIED, FluidSourceWithdrawalHandler.apply(level, withdrawal))
            assertEquals(0, FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z)!!.sourceFillAmount)
            val fluidName = if (cycle % 2 == 0) "minecraft:flowing_water" else WATER
            assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(DIMENSION, fluidName,
                org.joml.Vector3d(0.5, 1.5, 0.5), org.joml.Vector3d(), BigDecimal(127)))
            repeat(4) { manager.tick(mapOf(DIMENSION to level)) }
            assertEquals(0, manager.activeParcelCount())
            assertEquals(0, initial.compareTo(sources.amount(DIMENSION, pos.x, pos.y, pos.z, WATER)))
        }
    }

    @Test fun `a full block of settled spray must not become a replenishing vanilla source`() {
        settle(1.0)
        reload()
        blocks.remove(pos.below())
        assertTrue(newLiquidBelow().isEmpty, "Finite spray generated a new vanilla fluid cell")
        assertTrue(FractionalFluidSources.owns(level, pos))
        assertEquals(255, FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z)!!.sourceFillAmount)
    }

    @Test fun `ordinary unowned water still supplies vanilla flow`() {
        blocks[pos] = Blocks.WATER.defaultBlockState()
        blocks.remove(pos.below())
        assertFalse(newLiquidBelow().isEmpty)
        assertFalse(FractionalFluidSources.owns(level, pos))
    }

    @Test fun `accumulated full spray stays finite through repeated withdrawal and redeposit`() {
        settle(0.5)
        settle(0.5)
        repeat(8) { cycle ->
            reload()
            assertTrue(FractionalFluidSources.owns(level, pos))
            assertFalse(FractionalFluidSources.blocksPickup(level, pos))
            val observed = requireNotNull(FluidSourceWithdrawalHandler.observe(level, pos.x, pos.y, pos.z))
            assertEquals(255, observed.sourceFillAmount)
            val withdrawal = VsiFluidSourceWithdrawal(cycle + 1L, 1, DIMENSION, 9, true, 7,
                pos.x, pos.y, pos.z, 17, observed.sourceFillAmount)
            assertEquals(VsiFluidSourceWithdrawalResult.APPLIED, FluidSourceWithdrawalHandler.apply(level, withdrawal))
            assertTrue(blocks.getValue(pos).fluidState.isEmpty)
            assertFalse(FractionalFluidSources.owns(level, pos))
            settle(1.0)
            assertEquals(255, sources.sourceFillAmount(DIMENSION, pos.x, pos.y, pos.z, WATER))
        }
    }

    private fun newLiquidBelow(fluid: FlowingFluid = Fluids.WATER): FluidState {
        val getNewLiquid = FlowingFluid::class.java.getDeclaredMethod("getNewLiquid", Level::class.java,
            BlockPos::class.java, BlockState::class.java).apply { isAccessible = true }
        vanillaQuery = true
        return try {
            getNewLiquid.invoke(fluid, level, pos.below(), Blocks.AIR.defaultBlockState()) as FluidState
        } finally { vanillaQuery = false }
    }
}
