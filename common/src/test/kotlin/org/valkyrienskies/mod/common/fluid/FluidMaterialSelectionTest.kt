package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.material.Fluids
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceConstituent
import org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion
import org.valkyrienskies.mod.common.config.MassDatapackResolver

class FluidMaterialSelectionTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @BeforeEach fun setup() {
        ClientFluidTypes.replace(mapOf(17 to "minecraft:water", 18 to "minecraft:flowing_water", 19 to "minecraft:lava"))
        mockkObject(MassDatapackResolver)
        every { MassDatapackResolver.getFlowingFluid(any(), false) } answers {
            when (firstArg<Int>()) { 17 -> Fluids.WATER; 18, 19 -> Fluids.LAVA; else -> null }
        }
    }

    @AfterEach fun cleanup() { unmockkObject(MassDatapackResolver); ClientFluidTypes.clear() }

    private fun surface(vararg parts: Pair<Int, Double>) = VsiFluidSurfaceRegion(
        (16 shl 15) or (16 shl 20) or (16 shl 25), 0.0, 1.0, 0.0, 0.0, 1.0,
        parts.map { VsiFluidSurfaceConstituent(it.first, it.second) })

    @Test fun `split liquid states select the dominant canonical material using the correct side registry`() {
        val region = surface(17 to 70.0, 18 to 70.0, 19 to 100.0)
        val voxel = VsiFluidFloodedVoxel(0, 0, 0, 19, 240, 0, emptyList(), listOf(region))
        assertEquals(17, FluidVoxelGeometry.dominantFluid(region, true))
        assertEquals(18, FluidVoxelGeometry.dominantFluid(region, false))
        assertEquals(17, FluidVoxelGeometry.fluidAt(voxel, 0.5, 0.5, 0.5, true))
        assertEquals(18, FluidVoxelGeometry.fluidAt(voxel, 0.5, 0.5, 0.5, false))
        assertEquals(listOf(70.0, 70.0, 100.0), region.constituents.map { it.amount })
    }

    @Test fun `material ties use the lowest representative ID independent of constituent order`() {
        val parts = arrayOf(18 to 60.0, 17 to 60.0, 19 to 120.0)
        assertEquals(17, FluidVoxelGeometry.dominantFluid(surface(*parts), true))
        assertEquals(17, FluidVoxelGeometry.dominantFluid(surface(*parts.reversedArray()), true))
    }

    @Test fun `unresolved IDs stay distinct and adjacent states of one source fluid share a material`() {
        assertEquals(19, FluidVoxelGeometry.dominantFluid(surface(99 to 65.0, 100 to 65.0, 19 to 100.0), true))
        assertTrue(FluidVoxelGeometry.sameMaterial(17, 18, true))
        assertFalse(FluidVoxelGeometry.sameMaterial(17, 19, true))
        assertFalse(FluidVoxelGeometry.sameMaterial(99, 100, true))
        assertTrue(FluidVoxelGeometry.sameMaterial(99, 99, true))
    }
}
