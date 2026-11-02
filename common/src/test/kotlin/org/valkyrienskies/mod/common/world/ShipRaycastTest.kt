package org.valkyrienskies.mod.common.world

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.chunk.ChunkSource
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.shipObjectWorld

class ShipRaycastTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private val level = mockk<Level>(relaxed = true)
    private val ship = mockk<Ship>()
    private val chunk = mockk<LevelChunk>()
    private val chunkSource = mockk<ChunkSource>()
    private val queried = mutableListOf<BlockPos>()
    private val chunks = mutableListOf<Pair<Int, Int>>()
    private var loaded = true

    @BeforeEach fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.shipObjectWorld } returns mockk(relaxed = true)
        every { level.getShipsIntersecting(any<AABBdc>()) } returns listOf(ship)
        every { ship.id } returns 1L
        every { ship.worldAABB } returns AABBd(100.0, 0.0, 0.0, 101.0, 1.0, 1.0)
        every { ship.worldToShip } returns Matrix4d().translation(-85.0, 0.0, 0.0)
        every { ship.shipToWorld } returns Matrix4d().translation(85.0, 0.0, 0.0)
        every { level.chunkSource } returns chunkSource
        every { chunkSource.getChunk(any(), any(), ChunkStatus.FULL, false) } answers {
            val x = firstArg<Int>(); val z = secondArg<Int>()
            chunks += x to z
            if (loaded && x == 0 && z == 0) chunk else null
        }
        every { level.getBlockState(any()) } answers {
            val pos = firstArg<BlockPos>()
            if (!loaded || pos.x shr 4 != 0 || pos.z shr 4 != 0) error("Chunk not there when requested")
            chunk.getBlockState(pos)
        }
        every { chunk.getBlockState(any()) } answers {
            val pos = firstArg<BlockPos>().immutable()
            queried += pos
            if (pos == BlockPos(15, 0, 0)) Blocks.STONE.defaultBlockState() else Blocks.AIR.defaultBlockState()
        }
        every { level.getFluidState(any()) } answers { level.getBlockState(firstArg()).fluidState }
        every { chunk.getFluidState(any()) } answers { chunk.getBlockState(firstArg()).fluidState }
        every { level.clipWithInteractionOverride(any(), any(), any(), any(), any()) } answers {
            arg<VoxelShape>(3).clip(arg(0), arg(1), arg(2))
        }
    }

    @AfterEach fun cleanup() { unmockkAll() }

    private fun ray(start: Double, end: Double, transformed: Boolean = true) = level.clipIncludeShips(
        ClipContext(Vec3(start, 0.5, 0.5), Vec3(end, 0.5, 0.5), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, mockk<Entity>(relaxed = true)), shouldTransformHitPos = transformed, skipWorld = true)

    @Test fun `a ship hit across an unloaded padded chunk does not request missing terrain`() {
        val hit = ray(110.0, 90.0)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(BlockPos(15, 0, 0), hit.blockPos)
        assertEquals(101.0, hit.location.x, 1e-8)
        verify(exactly = 0) { level.getBlockState(any()) }
        verify(exactly = 0) { level.getFluidState(any()) }
    }

    @Test fun `long rays only scan the segment intersecting the ship bounds`() {
        assertEquals(HitResult.Type.BLOCK, ray(1000.0, -1000.0).type)
        assertTrue(chunks.all { it.first in 0..1 && it.second == 0 })
        assertTrue(queried.all { it.x in 14..15 })
    }

    @Test fun `unloaded ship terrain produces a miss without loading chunks`() {
        loaded = false
        assertEquals(HitResult.Type.MISS, ray(110.0, 90.0).type)
        assertTrue(queried.isEmpty())
    }

    @Test fun `rays beginning inside bounds preserve local hit coordinates when requested`() {
        val hit = ray(99.5, 110.0, false)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(15.0, hit.location.x, 1e-8)
    }

    @Test fun `clipping preserves fractional coordinates far from world origin`() {
        val offset = 30_000_000.25
        every { ship.worldAABB } returns AABBd(100.0 + offset, 0.0, 0.0, 101.0 + offset, 1.0, 1.0)
        every { ship.worldToShip } returns Matrix4d().translation(-85.0 - offset, 0.0, 0.0)
        every { ship.shipToWorld } returns Matrix4d().translation(85.0 + offset, 0.0, 0.0)
        val hit = ray(110.0 + offset, 90.0 + offset)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(101.0 + offset, hit.location.x, 1e-8)
    }

    @Test fun `rotated ship ray keeps the local block and world hit position`() {
        val toWorld = Matrix4d().translation(100.0, 0.0, 16.0).rotateY(Math.PI / 2)
        every { ship.worldAABB } returns AABBd(100.0, 0.0, 0.0, 101.0, 1.0, 1.0)
        every { ship.worldToShip } returns toWorld.invert(Matrix4d())
        every { ship.shipToWorld } returns toWorld
        val hit = level.clipIncludeShips(ClipContext(Vec3(100.5, 0.5, -10.0), Vec3(100.5, 0.5, 10.0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mockk<Entity>(relaxed = true)), skipWorld = true)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(BlockPos(15, 0, 0), hit.blockPos)
        assertEquals(0.0, hit.location.z, 1e-8)
    }

    @Test fun `water surface before a submerged slab wins the same cell hit comparison`() {
        val slab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)
        every { chunk.getBlockState(BlockPos(15, 0, 0)) } returns slab
        val hit = level.clipIncludeShips(ClipContext(Vec3(100.5, 4.0, 0.5), Vec3(100.5, -4.0, 0.5),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mockk<Entity>(relaxed = true)), skipWorld = true)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(BlockPos(15, 0, 0), hit.blockPos)
        assertEquals(8.0 / 9.0, hit.location.y, 1e-7)
    }

    @Test fun `client clipping uses the same interpolated bounds as its ray transform`() {
        val clientShip = mockk<ClientShip>()
        every { level.getShipsIntersecting(any<AABBdc>()) } returns listOf(clientShip)
        every { clientShip.id } returns 1
        every { clientShip.worldAABB } returns AABBd(100.0, 0.0, 0.0, 101.0, 1.0, 1.0)
        every { clientShip.renderAABB } returns AABBd(120.0, 0.0, 0.0, 121.0, 1.0, 1.0)
        every { clientShip.renderTransform.worldToShip } returns Matrix4d().translation(-105.0, 0.0, 0.0)
        every { clientShip.renderTransform.shipToWorld } returns Matrix4d().translation(105.0, 0.0, 0.0)
        val hit = ray(130.0, 90.0)
        assertEquals(HitResult.Type.BLOCK, hit.type)
        assertEquals(121.0, hit.location.x, 1e-8)
    }
}
