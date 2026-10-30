package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4d
import org.joml.primitives.AABBd
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.shipWorldNullable
import org.valkyrienskies.mod.common.config.MassDatapackResolver

class ShipFluidCandidatesTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private val level = mockk<Level>()
    private val world = mockk<VsiClientShipWorld>()
    private val ship = mockk<ClientShip>()
    private val transform = Matrix4d()
    private var topology = topologySnapshot().copy(voxels = listOf(VsiFluidTopologyVoxel(20, 0, 0, 1)))
    private var flooding = floodingSnapshot(VsiFluidFloodedVoxel(20, 0, 0, 17, 255, 0))
    private var loaded = true

    @BeforeEach fun setup() {
        mockkStatic("org.valkyrienskies.mod.common.VSGameUtilsKt")
        every { level.shipObjectWorld } returns world
        every { level.shipWorldNullable } returns world
        every { level.dimensionId } returns "test"
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        every { world.allShips.getIntersecting(any(), any()) } returns emptyList()
        every { world.loadedShips.iterator() } answers { if (loaded) listOf(ship).iterator() else emptyList<ClientShip>().iterator() }
        every { world.allShips.getById(7) } answers { if (loaded) ship else null }
        every { world.getFluidGameplaySnapshot(7) } answers { gameplaySnapshot(topology, flooding) }
        every { ship.id } returns 7
        every { ship.chunkClaimDimension } returns "test"
        every { ship.worldAABB } returns AABBd(-2.0, -2.0, -2.0, 2.0, 2.0, 2.0)
        every { ship.shipToWorld } answers { transform }
        every { ship.worldToShip } answers { transform.invert(Matrix4d()) }
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(17, true) } returns Fluids.WATER
    }
    @AfterEach fun cleanup() { ShipFluidInteraction.clear(world); unmockkAll() }

    @Test fun `child fluid outside the main hull reaches eye volume block and path queries`() {
        val point = Vec3(20.5, .5, .5)
        assertSame(Fluids.WATER, ShipFluidInteraction.samplePoint(level, point).fluid())
        assertTrue(ShipFluidStateQueries.atWorld(level, point.x, point.y, point.z)!!.fluid().type.isSame(Fluids.WATER))
        val found = mutableListOf<Double>()
        ShipFluidInteraction.prepareVolume(level, AABB(20.1, .1, .1, 20.9, .9, .9)).forEachRetained { _, depth -> found += depth }
        assertEquals(1, found.size)
        assertEquals(.8, found.single(), 1e-9)
        ShipFluidStateQueries.beginPath(level, null, emptyArray()).use {
            assertTrue(ShipFluidStateQueries.forPath(level, net.minecraft.core.BlockPos(20, 0, 0))!!.fluid().type.isSame(Fluids.WATER))
        }
        verify(exactly = 1) { world.loadedShips.iterator() }
    }

    @Test fun `candidate bounds follow body motion filter dimensions and expire on unload`() {
        val first = AABBd(20.1, .1, .1, 20.9, .9, .9)
        assertEquals(listOf(ship), ShipFluidCandidates.intersecting(world, "test", first))
        assertTrue(ShipFluidCandidates.intersecting(world, "elsewhere", first).isEmpty())
        transform.translation(10.0, 0.0, 0.0)
        assertTrue(ShipFluidCandidates.intersecting(world, "test", first).isEmpty())
        assertSame(Fluids.WATER, ShipFluidInteraction.samplePoint(level, Vec3(30.5, .5, .5)).fluid())
        loaded = false
        ShipFluidInteraction.prune(world)
        assertFalse(ShipFluidInteraction.samplePoint(level, Vec3(30.5, .5, .5)).insideDomain())
    }

    @Test fun `new child topology replaces bounds and ordinary candidates are not duplicated`() {
        val first = AABBd(20.1, .1, .1, 20.9, .9, .9)
        every { world.allShips.getIntersecting(any(), any()) } returns listOf(ship)
        assertEquals(listOf(ship), ShipFluidCandidates.intersecting(world, "test", first))
        every { world.allShips.getIntersecting(any(), any()) } returns emptyList()
        topology = topology.copy(streamSequence = topology.streamSequence + 1,
            voxels = listOf(VsiFluidTopologyVoxel(40, 0, 0, 1)))
        flooding = flooding.copy(streamSequence = flooding.streamSequence + 1,
            voxels = listOf(VsiFluidFloodedVoxel(40, 0, 0, 17, 255, 0)))
        ShipFluidInteraction.prune(world)
        assertTrue(ShipFluidCandidates.intersecting(world, "test", first).isEmpty())
        assertSame(Fluids.WATER, ShipFluidInteraction.samplePoint(level, Vec3(40.5, .5, .5)).fluid())
    }
}
