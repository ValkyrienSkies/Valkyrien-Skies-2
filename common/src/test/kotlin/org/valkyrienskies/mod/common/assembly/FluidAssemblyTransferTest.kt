package org.valkyrienskies.mod.common.assembly

import io.mockk.*
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.joml.Vector3i
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ServerShip
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld

@OptIn(GameTickOnly::class)
class FluidAssemblyTransferTest {
    companion object {
        @JvmStatic
        @org.junit.jupiter.api.BeforeAll
        fun bootstrapMinecraft() {
            net.minecraft.SharedConstants.tryDetectVersion()
            net.minecraft.server.Bootstrap.bootStrap()
        }
    }

    @Test
    fun `component ownership follows exact shipyard translation and world destination`() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        try {
            val level = mockk<ServerLevel>()
            val world = mockk<VsiServerShipWorld>(relaxed = true)
            val source = mockk<ServerShip> { every { id } returns 12L }
            val target = mockk<ServerShip> { every { id } returns 13L }
            every { level.shipObjectWorld } returns world
            every { level.dimensionId } returns "alpha"
            every { world.dimensionToGroundBodyIdImmutable } returns mapOf("alpha" to 90L)
            val cells = slot<IntArray>()
            val translation = slot<org.joml.Vector3ic>()
            every { world.moveFluidWithBlocks(12L,any(),"alpha",capture(cells),capture(translation)) } just Runs
            val blocks = linkedSetOf(BlockPos(10000003,7,-10000004),BlockPos(10000005,8,-10000002))
            for (destination in listOf(target,source,null)) {
                val destinationId = destination?.id ?: 90L
                ShipAssembler.transferRetainedWater(level,source,destination,blocks,
                    BlockPos(10000003,7,-10000004),BlockPos(-9999990,19,10000010))
                assertArrayEquals(intArrayOf(10000003,7,-10000004,10000005,8,-10000002),cells.captured)
                assertEquals(Vector3i(-19999993,12,20000014),translation.captured)
                verify(exactly=1) { world.moveFluidWithBlocks(12L,destinationId,"alpha",any(),any()) }
            }
            // A world source carries its Minecraft liquid blocks through the block mover itself.
            ShipAssembler.transferRetainedWater(level,null,target,blocks,BlockPos.ZERO,BlockPos.ZERO)
            verify(exactly=3) { world.moveFluidWithBlocks(any(),any(),any(),any(),any()) }
        } finally { unmockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt") }
    }
}
