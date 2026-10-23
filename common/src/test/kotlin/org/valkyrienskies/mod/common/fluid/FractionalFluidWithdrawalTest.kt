package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.item.Items
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawalResult.*
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipManagingPos
import org.valkyrienskies.mod.mixin.feature.fluid_escaping_ship_config.MixinSimpleWaterloggedBlock
import java.math.BigDecimal

class FractionalFluidWithdrawalTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val level = mockk<ServerLevel>()
    private val server = mockk<MinecraftServer>(moreInterfaces = arrayOf(IShipObjectWorldServerProvider::class))
    private val pos = BlockPos(-17, 4, 3)
    private val dimension = "minecraft:overworld"
    private var sources = FractionalFluidSources()
    private var state = Blocks.WATER.defaultBlockState()
    private var allowRemoval = true
    private fun event(amount: Int) = VsiFluidSourceWithdrawal(1, 1, dimension, 7, true, 8,
        pos.x, pos.y, pos.z, 11, amount)
    private fun amount() = sources.amount(dimension, pos.x, pos.y, pos.z, "minecraft:water")

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.hasChunkAt(pos) } returns true
        every { level.getBlockState(pos) } answers { state }
        every { level.getShipManagingPos(pos) } returns null
        every { level.server } returns server
        every { level.dimensionId } returns dimension
        every { (server as IShipObjectWorldServerProvider).fractionalFluidSources } answers { sources }
        every { MassDatapackResolver.getLiquidStateId(any()) } returns 11
        every { level.setBlock(pos, any(), any()) } answers {
            if (allowRemoval) state = secondArg<BlockState>()
            allowRemoval
        }
    }
    @AfterEach fun cleanup() { unmockkAll() }

    @Test fun `partial debit saves the remainder and only final debit removes the visual source`() {
        assertEquals(APPLIED, FluidSourceWithdrawalHandler.apply(level, event(128)))
        assertEquals(BigDecimal(127), amount())
        assertEquals(Blocks.WATER.defaultBlockState(), state)
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        allowRemoval = false
        assertEquals(RETRY_LATER, FluidSourceWithdrawalHandler.apply(level, event(127)))
        assertEquals(BigDecimal(127), amount())
        allowRemoval = true
        assertEquals(APPLIED, FluidSourceWithdrawalHandler.apply(level, event(127)))
        assertTrue(state.isAir)
        assertNull(amount())
        assertEquals(0L, sources.retainedBytes)
    }

    @Test fun `sub-unit remainder survives debit and reload and cannot fund an extra native unit`() {
        sources.setAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water", BigDecimal("127.25"))
        assertEquals(APPLIED, FluidSourceWithdrawalHandler.apply(level, event(127)))
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        assertEquals(BigDecimal("0.25"), amount())
        assertEquals(0, sources.sourceFillAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water"))
        assertEquals(STALE_SOURCE, FluidSourceWithdrawalHandler.apply(level, event(1)))
        assertEquals(BigDecimal("0.25"), amount())
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }

    @Test fun `capacity refusal leaves the full source untouched`() {
        sources = FractionalFluidSources(0)
        assertEquals(RETRY_LATER, FluidSourceWithdrawalHandler.apply(level, event(1)))
        assertNull(amount())
        assertEquals(Blocks.WATER.defaultBlockState(), state)
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }

    @Test fun `partial waterlogging preserves the solid and clears only the final liquid`() {
        state = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        val solid = state
        assertEquals(APPLIED, FluidSourceWithdrawalHandler.apply(level, event(128)))
        assertEquals(solid, state)
        sources = FractionalFluidSources().also { it.load(sources.save()) }
        assertEquals(APPLIED, FluidSourceWithdrawalHandler.apply(level, event(127)))
        assertEquals(solid.setValue(BlockStateProperties.WATERLOGGED, false), state)
        assertNull(amount())
    }

    @Test fun `vanilla flow queries exclude fractional donors without erasing waterlogged solid boundaries`() {
        val water = Blocks.WATER.defaultBlockState()
        assertFalse(FractionalFluidSources.owns(level, pos))
        assertSame(water, FractionalFluidSources.vanillaFlowState(level, pos, water))
        sources.setAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water", BigDecimal("0.25"))
        assertTrue(FractionalFluidSources.owns(level, pos))
        assertTrue(FractionalFluidSources.vanillaFlowState(level, pos, water).isAir)
        val slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        assertEquals(slab.setValue(BlockStateProperties.WATERLOGGED, false),
            FractionalFluidSources.vanillaFlowState(level, pos, slab))
        assertFalse(FractionalFluidSources.owns(level, pos.east()))
        sources.setAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water", FractionalFluidSources.FULL_SOURCE)
        assertFalse(FractionalFluidSources.owns(level, pos))
        assertSame(water, FractionalFluidSources.vanillaFlowState(level, pos, water))
    }

    @Test fun `waterlogged pickup refuses a remainder and preserves ordinary full and dry behavior`() {
        val pickup = object : MixinSimpleWaterloggedBlock {}
        val wet = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        sources.setAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water", BigDecimal("127.25"))
        assertTrue(pickup.pickupBlock(level, pos, wet).isEmpty)
        assertEquals(BigDecimal("127.25"), amount())
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
        sources.setAmount(dimension, pos.x, pos.y, pos.z, "minecraft:water", FractionalFluidSources.FULL_SOURCE)
        assertTrue(pickup.pickupBlock(level, pos, wet).`is`(Items.WATER_BUCKET))
        assertEquals(wet.setValue(BlockStateProperties.WATERLOGGED, false), state)
        assertTrue(pickup.pickupBlock(level, pos, state).isEmpty)
        verify(exactly = 1) { level.setBlock(any(), any(), any()) }
    }
}
