package org.valkyrienskies.mod.common.fluid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidFloodingSnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologySnapshot
import org.valkyrienskies.core.internal.physics.VsiFluidTopologyVoxel

class FloodedFluidClientCacheTest {
    @Test
    fun `partial cell fog only applies below its local fill surface`() {
        assertFalse(FloodedFluidClientCache.isLocallySubmerged(0, 0.0))
        assertTrue(FloodedFluidClientCache.isLocallySubmerged(128, 0.25))
        assertFalse(FloodedFluidClientCache.isLocallySubmerged(128, 0.75))
        assertTrue(FloodedFluidClientCache.isLocallySubmerged(255, 0.999))
    }

    @Test
    fun `ship fluid interaction distinguishes flooded dry and external points`() {
        val topology = VsiFluidTopologySnapshot(
            bodyId = 7L,
            dimensionId = "minecraft:overworld",
            streamSequence = 3L,
            nativePublicationId = 2L,
            sourceRevision = 11L,
            enabled = true,
            valid = true,
            totalDomainVoxelCount = 1,
            voxels = listOf(VsiFluidTopologyVoxel(1, 2, 3, 1)),
        )
        val flooding = VsiFluidFloodingSnapshot(
            bodyId = 7L,
            dimensionId = "minecraft:overworld",
            streamSequence = 4L,
            nativePublicationId = 2L,
            sourceRevision = 11L,
            totalFloodedVoxelCount = 1,
            voxels = listOf(VsiFluidFloodedVoxel(1, 2, 3, 17, 128, 0)),
        )
        val body = ShipFluidInteraction.CachedBody(topology, flooding)

        val flooded = body.sampleLocal(1.5, 2.25, 3.5)
        assertTrue(flooded.insideDomain())
        assertEquals(17, flooded.fluidId())

        val dry = body.sampleLocal(1.5, 2.75, 3.5)
        assertTrue(dry.insideDomain())
        assertNull(dry.fluidId())

        val external = body.sampleLocal(2.5, 2.25, 3.5)
        assertFalse(external.insideDomain())
        assertNull(external.fluidId())
    }
}
