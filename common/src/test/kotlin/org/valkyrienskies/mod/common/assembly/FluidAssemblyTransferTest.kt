package org.valkyrienskies.mod.common.assembly

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3i
import org.joml.Vector3ic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.api.dimensionId
import org.valkyrienskies.mod.api.shipWorld

@OptIn(GameTickOnly::class)
class FluidAssemblyTransferTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @Test
    fun `component ownership follows exact shipyard translation and world destination`() {
        mockkStatic("org.valkyrienskies.mod.api.ValkyrienSkies")
        try {
            val level = mockk<ServerLevel>()
            val world = mockk<VsiServerShipWorld>(relaxed = true)
            val source = mockk<ServerShip> { every { id } returns 12L }
            val target = mockk<ServerShip> { every { id } returns 13L }
            every { level.shipWorld } returns world
            every { level.dimensionId } returns "alpha"
            every { world.dimensionToGroundBodyIdImmutable } returns mapOf("alpha" to 90L)
            val cells = slot<IntArray>()
            val translation = slot<Vector3ic>()
            every { world.moveFluidWithBlocks(12L, any(), "alpha", capture(cells), capture(translation)) } just Runs
            val blocks = linkedSetOf(BlockPos(10000003, 7, -10000004), BlockPos(10000005, 8, -10000002))
            for (destination in listOf(target, source, null)) {
                val destinationId = destination?.id ?: 90L
                ShipAssembler.transferRetainedWater(
                    level, source, destination, blocks,
                    BlockPos(10000003, 7, -10000004), BlockPos(-9999990, 19, 10000010)
                )

                assertArrayEquals(
                    intArrayOf(10000003, 7, -10000004, 10000005, 8, -10000002),
                    cells.captured
                )
                assertEquals(Vector3i(-19999993, 12, 20000014), translation.captured)

                verify(exactly = 1) { world.moveFluidWithBlocks(12L, destinationId, "alpha", any(), any()) }
            }
            // A world source carries its Minecraft liquid blocks through the block mover itself.
            ShipAssembler.transferRetainedWater(level, null, target, blocks, BlockPos.ZERO, BlockPos.ZERO)
            verify(exactly = 3) { world.moveFluidWithBlocks(any(), any(), any(), any(), any()) }
        } finally { unmockkStatic("org.valkyrienskies.mod.api.ValkyrienSkies") }
    }
}
