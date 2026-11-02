package org.valkyrienskies.mod.feature.ship_water_pockets

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets.LevelChunkDuck
import org.valkyrienskies.mod.util.FluidStateManager

class FluidStateManagerQueryCacheTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun repeatedSameChunkQueryOnlyLoadsOnce() {
        val level = mockk<Level>()
        val chunk = mockk<ChunkAccess>()
        val cache = FluidStateManager.QueryCache()

        every { level.getChunk(4, 9, ChunkStatus.FULL, false) } returns chunk

        assertSame(chunk, FluidStateManager.getCachedChunk(level, 4, 9, cache))
        assertSame(chunk, FluidStateManager.getCachedChunk(level, 4, 9, cache))
        assertSame(chunk, FluidStateManager.getCachedChunk(level, 4, 9, cache))

        verify(exactly = 1) { level.getChunk(4, 9, ChunkStatus.FULL, false) }
        confirmVerified(level)
    }

    @Test
    fun neighboringChunkQueriesReuseCachedSlots() {
        val level = mockk<Level>()
        val firstChunk = mockk<ChunkAccess>()
        val secondChunk = mockk<ChunkAccess>()
        val cache = FluidStateManager.QueryCache()

        every { level.getChunk(1, 1, ChunkStatus.FULL, false) } returns firstChunk
        every { level.getChunk(2, 1, ChunkStatus.FULL, false) } returns secondChunk

        assertSame(firstChunk, FluidStateManager.getCachedChunk(level, 1, 1, cache))
        assertSame(secondChunk, FluidStateManager.getCachedChunk(level, 2, 1, cache))
        assertSame(firstChunk, FluidStateManager.getCachedChunk(level, 1, 1, cache))
        assertSame(secondChunk, FluidStateManager.getCachedChunk(level, 2, 1, cache))

        verify(exactly = 1) { level.getChunk(1, 1, ChunkStatus.FULL, false) }
        verify(exactly = 1) { level.getChunk(2, 1, ChunkStatus.FULL, false) }
        confirmVerified(level)
    }

    @Test
    fun missingChunkLookupIsCachedForThePass() {
        val level = mockk<Level>()
        val cache = FluidStateManager.QueryCache()

        every { level.getChunk(-3, 7, ChunkStatus.FULL, false) } returns null

        assertNull(FluidStateManager.getCachedChunk(level, -3, 7, cache))
        assertNull(FluidStateManager.getCachedChunk(level, -3, 7, cache))

        verify(exactly = 1) { level.getChunk(-3, 7, ChunkStatus.FULL, false) }
        confirmVerified(level)
    }

    @Test
    fun fluidDataReportsColumnTopYAndSurfaceState() {
        val level = mockk<Level>()
        val chunk = mockk<ChunkAccess>(moreInterfaces = arrayOf(LevelChunkDuck::class))
        val fluidData = FluidStateManager.ChunkFluidData()
        val cache = FluidStateManager.QueryCache()
        val bottomPos = BlockPos(3, 10, 5)
        val topPos = BlockPos(3, 12, 5)

        fluidData.setFluidState(bottomPos, Fluids.WATER.defaultFluidState())
        fluidData.setFluidState(BlockPos(3, 11, 5), Fluids.WATER.defaultFluidState())
        fluidData.setFluidState(topPos, Fluids.FLOWING_WATER.defaultFluidState())

        every { level.minBuildHeight } returns -64
        every { level.maxBuildHeight } returns 320
        every { level.getChunk(0, 0, ChunkStatus.FULL, false) } returns chunk
        every { (chunk as LevelChunkDuck).`vs$getFluidData`() } returns fluidData

        val belowSurface = FluidStateManager.getFluidData(level, bottomPos, cache)
        assertNotNull(belowSurface)
        assertEquals(12, belowSurface!!.topY())
        assertSame(Fluids.WATER, belowSurface.sourceFluid())
        assertFalse(belowSurface.isSurface())
        assertEquals(1.0f, belowSurface.height())

        val surface = FluidStateManager.getFluidData(level, topPos, cache)
        assertNotNull(surface)
        assertEquals(12, surface!!.topY())
        assertTrue(surface.isSurface())
        assertSame(Fluids.FLOWING_WATER.defaultFluidState().type, surface.surface().type)
        assertEquals(surface.surface().ownHeight, surface.height())
    }
}
