package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.internal.physics.VsiFluidSourceWithdrawal
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.mod.common.config.MassDatapackResolver
import org.valkyrienskies.mod.common.getShipManagingPos

class FluidSourceObservationTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val level = mockk<ServerLevel>()
    private val event = VsiFluidSourceWithdrawal(1, 1, "minecraft:overworld", 7, false, 8, -17, 4, 3, 11, 255)
    private val pos = BlockPos(-17, 4, 3)

    @BeforeEach fun setup() {
        mockkObject(MassDatapackResolver)
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
    }
    @AfterEach fun cleanup() { unmockkAll() }

    @Test fun `missing and unloaded chunks never become air observations`() {
        assertNull(FluidSourceWithdrawalHandler.observe(null, event))
        every { level.hasChunkAt(pos) } returns false
        assertNull(FluidSourceWithdrawalHandler.observe(level, event))
        verify(exactly = 0) { level.getBlockState(any()) }
        verify(exactly = 1) { level.hasChunkAt(pos) }
        confirmVerified(level)
    }

    @Test fun `unregistered block is unknown and cannot authorize publication`() {
        every { level.hasChunkAt(pos) } returns true
        every { level.getBlockState(pos) } returns Blocks.STONE.defaultBlockState()
        every { MassDatapackResolver.getBlockStateType(Blocks.STONE.defaultBlockState()) } returns null
        assertNull(FluidSourceWithdrawalHandler.observe(level, event))
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }

    @Test fun `observer returns the current owner and exact block type without changing the source`() {
        val ship = mockk<ServerShip>()
        every { ship.id } returns 27L
        every { level.hasChunkAt(pos) } returns true
        val air = mockk<VsiBlockType>()
        val stone = mockk<VsiBlockType>()
        every { level.getBlockState(pos) } returnsMany listOf(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState())
        every { MassDatapackResolver.getBlockStateType(Blocks.AIR.defaultBlockState()) } returns air
        every { MassDatapackResolver.getBlockStateType(Blocks.STONE.defaultBlockState()) } returns stone
        every { level.getShipManagingPos(pos) } returnsMany listOf(null, ship)
        val first = FluidSourceWithdrawalHandler.observe(level, event)!!
        assertNull(first.ownerBodyId)
        assertSame(air, first.blockType)
        val second = FluidSourceWithdrawalHandler.observe(level, event.sourcePositionX, event.sourcePositionY, event.sourcePositionZ)!!
        assertEquals(27L, second.ownerBodyId)
        assertSame(stone, second.blockType)
        verify(exactly = 0) { level.setBlock(any(), any(), any()) }
    }
}
