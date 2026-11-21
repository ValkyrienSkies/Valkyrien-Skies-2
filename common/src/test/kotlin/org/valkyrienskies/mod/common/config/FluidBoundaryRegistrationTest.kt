package org.valkyrienskies.mod.common.config

import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.shapes.Shapes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.api.physics.blockstates.FluidBoundaryShape

class FluidBoundaryRegistrationTest {
    companion object {
        @BeforeAll @JvmStatic fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    private fun boundary(block: net.minecraft.world.level.block.state.BlockState) =
        FluidBoundaryRegistration.fromShape(block.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))

    private fun solidCount(shape: FluidBoundaryShape) = shape.copySolidMask().sumOf { java.lang.Long.bitCount(it) }

    @Test fun `slabs retain half capacity including waterlogged states`() {
        val bottom = Blocks.OAK_SLAB.defaultBlockState()
        val top = bottom.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)
        assertEquals(2048, solidCount(boundary(bottom)))
        assertEquals(2048, solidCount(boundary(top)))
        assertNotEquals(boundary(bottom), boundary(top))
        assertEquals(boundary(bottom), boundary(bottom.setValue(BlockStateProperties.WATERLOGGED, true)))
        assertEquals(FluidBoundaryShape.CLOSED, boundary(bottom.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE)))
    }

    @Test fun `door and trapdoor rotation changes fluid boundaries`() {
        for (block in listOf(Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR)) {
            val closed = block.defaultBlockState()
            val opened = closed.setValue(BlockStateProperties.OPEN, true)
            assertNotEquals(boundary(closed), boundary(opened))
            assertEquals(solidCount(boundary(closed)), solidCount(boundary(opened)))
            assertTrue(solidCount(boundary(closed)) in 1..4095)
        }
    }

    @Test fun `bars preserve openings and empty shapes are permeable`() {
        val bars = boundary(Blocks.IRON_BARS.defaultBlockState())
        assertTrue(solidCount(bars) in 1..4095)
        assertEquals(FluidBoundaryShape.OPEN, FluidBoundaryRegistration.fromShape(Shapes.empty()))
        assertEquals(FluidBoundaryShape.CLOSED, boundary(Blocks.STONE.defaultBlockState()))
    }

    @Test fun `overhanging shapes use closed fallback and subgrid geometry rounds outward`() {
        assertEquals(FluidBoundaryShape.CLOSED, FluidBoundaryRegistration.fromShape(Shapes.box(0.0, 0.0, 0.0, 1.0, 1.5, 1.0)))
        assertEquals(256, solidCount(FluidBoundaryRegistration.fromShape(Shapes.box(0.0, 0.0, 0.0, 0.001, 1.0, 1.0))))
    }
}
