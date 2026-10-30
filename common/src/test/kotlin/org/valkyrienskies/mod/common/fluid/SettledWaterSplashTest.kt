package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluids
import org.joml.Vector3d
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidOutflowResult
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.*
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.config.VSGameConfig
import java.math.BigDecimal

class SettledWaterSplashTest {
    companion object {
        private const val DIM = "minecraft:overworld"
        private const val WATER = "minecraft:water"
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<ServerLevel>(relaxed = true)
    private val blocks = mutableMapOf<BlockPos, BlockState>()
    private var sources = FractionalFluidSources()
    private var manager = WorldFluidOutflowManager()
    private val levels get() = mapOf(DIM to level)
    private val pos = BlockPos(0, 1, 0)
    private var oldLifetime = 0
    private var oldThreshold = 0.0

    @BeforeEach fun setup() {
        oldLifetime = VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds
        oldThreshold = VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold
        VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds = 1
        VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold = 0.25
        mockkObject(MassDatapackResolver, BlockStateInfo)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { level.server } returns server
        every { level.dimensionId } returns DIM
        every { level.getShipManagingPos(any<BlockPos>()) } returns null
        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.hasChunkAt(any()) } returns true
        every { level.players() } returns emptyList()
        every { level.getBlockState(any()) } answers {
            val at = firstArg<BlockPos>()
            blocks[at] ?: if (at.y <= 0) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
        }
        every { level.getFluidState(any()) } answers { level.getBlockState(firstArg()).fluidState }
        ordinaryWrites()
        every { BlockStateInfo.onSourceAmountChanged(any(), any(), any(), any()) } just Runs
        val waterType = mockk<VsiBlockType>()
        every { MassDatapackResolver.getLiquidStateId(any()) } answers {
            if (firstArg<BlockState>().fluidState.isEmpty) null else 17
        }
        every { MassDatapackResolver.getBlockStateType(any()) } returns waterType
    }

    @AfterEach fun cleanup() {
        VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds = oldLifetime
        VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold = oldThreshold
        unmockkAll()
    }

    private fun ordinaryWrites() {
        every { level.setBlock(any(), any(), any()) } answers {
            val at = firstArg<BlockPos>().immutable()
            val state = secondArg<BlockState>()
            blocks[at] = state
            sources.reconcileFluid(DIM, at.x, at.y, at.z, FractionalFluidSources.fluidName(state))
            true
        }
    }

    private fun settle(volume: String, at: BlockPos = pos, fluid: String = WATER) {
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(DIM, fluid,
            Vector3d(at.x + 0.5, at.y + 0.5, at.z + 0.5), Vector3d(), BigDecimal(volume) * BigDecimal(255)))
        repeat(4) { manager.tick(levels) }
        assertEquals(0, manager.activeParcelCount())
    }

    private fun age(ticks: Int) = repeat(ticks) { sources.tickSplashes(levels) }
    private fun amount(at: BlockPos = pos) = sources.amount(DIM, at.x, at.y, at.z, WATER)
    private fun reload() {
        val bytes = sources.retainedBytes
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        assertEquals(bytes, sources.retainedBytes)
        manager = WorldFluidOutflowManager().also { it.load(manager.save()) }
    }

    @Test fun `small landed spray expires at its configured age across reload`() {
        settle("0.125")
        age(9)
        reload()
        age(10)
        assertEquals(BigDecimal("31.875"), amount())
        assertFalse(level.getBlockState(pos).isAir)
        age(1)
        assertTrue(level.getBlockState(pos).isAir)
        assertNull(amount())
        assertEquals(0, sources.size)
        assertEquals(0L, sources.retainedBytes)
        reload()
        assertFalse(FractionalFluidSources.owns(level, pos))
    }

    @Test fun `threshold and full deposits remain finite after reload and partial withdrawal`() {
        for ((x, volume) in listOf("0.25", "1").withIndex()) {
            val at = pos.offset(x * 10, 0, 0)
            settle(volume, at)
            reload()
            age(40)
            assertEquals(0, (BigDecimal(volume) * BigDecimal(255)).compareTo(amount(at)))
            val observation = requireNotNull(FluidSourceWithdrawalHandler.observe(level, at.x, at.y, at.z))
            val withdrawal = VsiFluidSourceWithdrawal(1, 1, DIM, 9, true, 7,
                at.x, at.y, at.z, 17, observation.sourceFillAmount - 1)
            assertEquals(VsiFluidSourceWithdrawalResult.APPLIED, FluidSourceWithdrawalHandler.apply(level, withdrawal))
            val remaining = amount(at)
            reload()
            age(40)
            assertEquals(remaining, amount(at))
            assertTrue(FractionalFluidSources.owns(level, at))
            assertTrue(FractionalFluidSources.vanillaFlowState(level, at, level.getBlockState(at)).isAir)
        }
    }

    @Test fun `fresh spray refreshes expiry and accumulation promotes a permanent finite puddle`() {
        settle("0.125")
        age(19)
        settle("0.075")
        reload()
        age(19)
        assertEquals(BigDecimal("51"), amount())
        settle("0.05")
        reload()
        age(40)
        assertEquals(BigDecimal("63.75"), amount())
    }

    @Test fun `zero lifetime pauses dissipation and cutoff zero keeps new deposits`() {
        settle("0.125")
        age(10)
        VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds = 0
        age(100)
        reload()
        VSGameConfig.SERVER.SpilledWater.splashLifetimeSeconds = 1
        age(9)
        assertNotNull(amount())
        age(1)
        assertNull(amount())
        VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold = 0.0
        settle("0.125")
        age(40)
        assertNotNull(amount())
    }

    @Test fun `configured cutoff changes which new deposits dissipate`() {
        VSGameConfig.SERVER.SpilledWater.splashVolumeThreshold = 0.5
        settle("0.3")
        age(20)
        assertNull(amount())
        assertTrue(level.getBlockState(pos).isAir)
    }

    @Test fun `unloaded splash ages without reading or loading its chunk`() {
        settle("0.125")
        every { level.hasChunkAt(pos) } returns false
        clearMocks(level, answers = false)
        age(40)
        verify(exactly = 0) { level.getBlockState(any()) }
        verify(exactly = 0) { level.getChunk(any<Int>(), any<Int>()) }
        assertNotNull(amount())
        reload()
        every { level.hasChunkAt(pos) } returns true
        age(1)
        assertNull(amount())
    }

    @Test fun `expiry retries refused and falsely acknowledged writes without releasing ownership`() {
        settle("0.125")
        every { level.setBlock(any(), any(), any()) } returns false
        age(20)
        assertEquals(BigDecimal("31.875"), amount())
        every { level.setBlock(any(), any(), any()) } returns true
        age(1)
        assertEquals(BigDecimal("31.875"), amount())
        ordinaryWrites()
        age(1)
        assertNull(amount())
        assertEquals(0L, sources.retainedBytes)
    }

    @Test fun `replacement blocks and player source refills are never removed by the old timer`() {
        for ((x, replacement) in listOf(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
            Blocks.OAK_SLAB.defaultBlockState()).withIndex()) {
            val at = pos.offset(x * 10, 0, 0)
            settle("0.125", at)
            level.setBlock(at, replacement, 3)
            age(40)
            assertEquals(replacement, level.getBlockState(at))
        }
    }

    @Test fun `ordinary water and partial natural withdrawals do not acquire splash timers`() {
        blocks[pos] = Blocks.WATER.defaultBlockState()
        val withdrawal = VsiFluidSourceWithdrawal(1, 1, DIM, 9, true, 7, pos.x, pos.y, pos.z, 17, 230)
        assertEquals(VsiFluidSourceWithdrawalResult.APPLIED, FluidSourceWithdrawalHandler.apply(level, withdrawal))
        settle("0.01")
        val remaining = amount()
        blocks[pos.east()] = Blocks.WATER.defaultBlockState()
        reload()
        age(40)
        assertEquals(remaining, amount())
        assertTrue(level.getBlockState(pos.east()).fluidState.isSource)
    }

    @Test fun `legacy owned water stays persistent and lava is not treated as spray`() {
        settle("0.125")
        val legacy = sources.save().apply {
            putInt("version", 4)
            remove("splash_clock")
            getList("sources", Tag.TAG_COMPOUND.toInt()).getCompound(0).apply {
                remove("splash_tick"); remove("splash_level"); remove("splash_pending")
            }
        }
        sources = FractionalFluidSources().also { it.load(legacy) }
        settle("0.125", pos.offset(10, 0, 0), "minecraft:lava")
        reload()
        age(40)
        assertEquals(BigDecimal("31.875"), amount())
        assertFalse(level.getBlockState(pos.offset(10, 0, 0)).fluidState.isEmpty)
    }

    @Test fun `interrupted placement cannot expire and retry retains splash classification`() {
        assertEquals(VsiFluidOutflowResult.APPLIED, manager.enqueueExact(DIM, WATER,
            Vector3d(0.5, 1.5, 0.5), Vector3d(), BigDecimal("31.875")))
        every { level.setBlock(any(), any(), any()) } answers {
            blocks[pos] = secondArg()
            throw IllegalStateException("interrupted after write")
        }
        assertThrows(IllegalStateException::class.java) { manager.tick(levels) }
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        manager = WorldFluidOutflowManager().also { it.load(manager.save()) }
        age(40)
        assertFalse(level.getBlockState(pos).isAir)
        assertEquals(0, amount()!!.signum())
        ordinaryWrites()
        manager.tick(levels)
        assertEquals(BigDecimal("31.875"), amount())
        age(20)
        assertNull(amount())
        assertEquals(0, manager.activeParcelCount())
    }

    @Test fun `active move locks expiry and a cancelled move restores the original age`() {
        settle("0.125")
        age(19)
        val from = FractionalFluidSources.Position(DIM, pos.x, pos.y, pos.z)
        val move = FractionalFluidTransfers.Move(from, from.copy(x = 10), BigDecimal.ONE, BigDecimal.ONE,
            DIM, Vector3d(0.5, 1.5, 0.5), Vector3d())
        val scope = requireNotNull(sources.transfers.begin(sequenceOf(move), levels, manager))
        age(20)
        assertFalse(level.getBlockState(pos).isAir)
        scope.close()
        reload()
        sources.recoverTransfers(levels, manager)
        age(1)
        assertNull(amount())
    }

    @Test fun `pending move reload preserves original splash age on rollback`() {
        settle("0.125")
        age(19)
        val from = FractionalFluidSources.Position(DIM, pos.x, pos.y, pos.z)
        val move = FractionalFluidTransfers.Move(from, from.copy(x = 10), BigDecimal.ONE, BigDecimal.ONE,
            DIM, Vector3d(0.5, 1.5, 0.5), Vector3d())
        sources.transfers.begin(sequenceOf(move), levels, manager)
        reload()
        sources.recoverTransfers(levels, manager)
        age(1)
        assertNull(amount())
    }

    @Test fun `cleanup work is bounded and continues to later positions`() {
        val count = FractionalFluidSources.SPLASH_SCAN_LIMIT + 1
        repeat(count) { x ->
            val at = BlockPos(x, 1, 0)
            requireNotNull(sources.prepareChange(DIM, x, 1, 0, WATER, BigDecimal.ZERO, BigDecimal.ONE, 7)).commit()
            blocks[at] = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7)
        }
        age(19)
        clearMocks(level, answers = false)
        age(1)
        assertEquals(1, sources.size)
        verify(exactly = FractionalFluidSources.SPLASH_SCAN_LIMIT) { level.setBlock(any(), any(), any()) }
        age(1)
        assertEquals(0, sources.size)
    }

    @Test fun `malformed splash checkpoint preserves existing ownership`() {
        settle("0.125")
        val saved = sources.save()
        val invalid = saved.copy()
        invalid.getList("sources", Tag.TAG_COMPOUND.toInt()).getCompound(0).putLong("splash_tick", 100)
        assertThrows(IllegalArgumentException::class.java) { sources.load(invalid) }
        assertEquals(saved, sources.save())
        assertThrows(IllegalArgumentException::class.java) { FractionalFluidSources(700).load(saved) }
    }
}
