package org.valkyrienskies.mod.common.fluid

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidGameplaySnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel
import org.valkyrienskies.core.internal.world.VsiClientShipWorld
import org.valkyrienskies.core.internal.world.VsiServerShipWorld
import org.valkyrienskies.core.internal.world.VsiShipWorld

internal fun floodingSnapshot(vararg voxels: VsiFluidFloodedVoxel) = VsiFluidFloodingSnapshot(
    bodyId = 7, dimensionId = "minecraft:overworld", streamSequence = 4,
    nativePublicationId = 2, sourceRevision = 11,
    totalFloodedVoxelCount = voxels.size, voxels = voxels.toList(),
)

internal fun topologySnapshot(x: Int = 0) = VsiFluidTopologySnapshot(
    bodyId = 7, dimensionId = "minecraft:overworld", streamSequence = 3,
    nativePublicationId = 2, sourceRevision = 11, enabled = true, valid = true,
    totalDomainVoxelCount = 1, voxels = listOf(VsiFluidTopologyVoxel(x, 0, 0, 1)),
)

internal fun gameplaySnapshot(
    topology: VsiFluidTopologySnapshot = topologySnapshot(),
    flooding: VsiFluidFloodingSnapshot = floodingSnapshot(),
) = VsiFluidGameplaySnapshot(topology,
    flooding.copy(streamSequence = maxOf(topology.streamSequence, flooding.streamSequence)), true)

class FluidCacheLifecycleTest {
    @AfterEach
    fun clearClientIndices() {
        FloodedFluidClientCache.clear()
        FluidTopologyClientCache.clear()
    }

    @Test
    fun `reconnect with identical body and sequence uses the new worlds cells`() {
        val first = mockk<VsiClientShipWorld>()
        val second = mockk<VsiClientShipWorld>()
        val a = floodingSnapshot(VsiFluidFloodedVoxel(0, 0, 0, 17, 255, 0))
        val b = floodingSnapshot(VsiFluidFloodedVoxel(9, 0, 0, 18, 128, 0))
        every { first.getFluidGameplaySnapshot(7) } returns gameplaySnapshot(topologySnapshot(0), a)
        every { second.getFluidGameplaySnapshot(7) } returns gameplaySnapshot(topologySnapshot(9), b)

        val cachedA = FloodedFluidClientCache.get(first, 7)!!
        val topologyA = FluidTopologyClientCache.get(first, 7)!!
        assertSame(cachedA, FloodedFluidClientCache.get(first, 7))
        assertSame(topologyA, FluidTopologyClientCache.get(first, 7))
        val cachedB = FloodedFluidClientCache.get(second, 7)!!
        val topologyB = FluidTopologyClientCache.get(second, 7)!!
        assertNull(cachedB.get(0, 0, 0))
        assertEquals(18, cachedB.get(9, 0, 0)!!.fluidId)
        assertNull(topologyB.get(0, 0, 0))
        assertNotNull(topologyB.get(9, 0, 0))
        // Alternating worlds must not retain the second world's values either.
        assertEquals(17, FloodedFluidClientCache.get(first, 7)!!.get(0, 0, 0)!!.fluidId)
        assertNotNull(FluidTopologyClientCache.get(first, 7)!!.get(0, 0, 0))
    }

    @Test
    fun `explicit disconnect clears indices even when the world instance is reused`() {
        val world = mockk<VsiClientShipWorld>()
        every { world.getFluidGameplaySnapshot(7) } returns gameplaySnapshot()
        val flooding = FloodedFluidClientCache.get(world, 7)
        val topology = FluidTopologyClientCache.get(world, 7)
        clearClientIndices()
        assertNotSame(flooding, FloodedFluidClientCache.get(world, 7))
        assertNotSame(topology, FluidTopologyClientCache.get(world, 7))
    }

    @Test
    fun `tick pruning releases deleted client bodies without querying them`() {
        val world = mockk<VsiClientShipWorld>(relaxed = true)
        every { world.getFluidGameplaySnapshot(7) } returns gameplaySnapshot()
        assertDeletedBodyPruned(world)
    }

    @Test
    fun `tick pruning releases deleted server bodies without querying them`() {
        val world = mockk<VsiServerShipWorld>(relaxed = true)
        every { world.getFluidGameplaySnapshot(7) } returns gameplaySnapshot()
        assertDeletedBodyPruned(world)
    }

    private fun assertDeletedBodyPruned(world: VsiShipWorld) {
        val ship = mockk<Ship>()
        every { world.allShips.getById(7) } returns ship
        val body = ShipFluidInteraction.bodyFor(world, 7)
        assertNotNull(body)
        ShipFluidInteraction.prune(world)
        assertSame(body, ShipFluidInteraction.bodyFor(world, 7))

        every { world.allShips.getById(7) } returns null
        ShipFluidInteraction.prune(world)
        val cacheField = ShipFluidInteraction::class.java.getDeclaredField("CACHES")
            .apply { isAccessible = true }
        assertFalse((cacheField.get(null) as Map<*, *>).containsKey(world))

        // Recreating the same ID must build a new index, even if the mock still reports old sequences.
        every { world.allShips.getById(7) } returns ship
        assertNotSame(body, ShipFluidInteraction.bodyFor(world, 7))
        ShipFluidInteraction.clear(world)
        assertFalse((cacheField.get(null) as Map<*, *>).containsKey(world))
    }
}
