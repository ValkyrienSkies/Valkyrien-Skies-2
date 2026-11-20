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

    @Test fun `open sliding and folding doors register only the panel inside the cell`() {
        for (width in listOf(3, 9)) {
            val w = width / 16.0
            for (far in listOf(false, true)) for (hinge in listOf(false, true)) for (rotate in listOf(false, true)) {
                val panelStart = if (far) 13 / 16.0 else 0.0
                val lo = if (hinge) 1 - w else if (width == 3) -13 / 16.0 else -3 / 16.0
                val hi = if (!hinge) w else if (width == 3) 29 / 16.0 else 19 / 16.0
                fun panel(a: Double, b: Double) = if (rotate) Shapes.box(a, 0.0, panelStart, b, 1.0, panelStart + 3 / 16.0)
                    else Shapes.box(panelStart, 0.0, a, panelStart + 3 / 16.0, 1.0, b)
                val boundary = FluidBoundaryRegistration.fromShape(panel(lo, hi))
                assertEquals(FluidBoundaryRegistration.fromShape(panel(maxOf(0.0, lo), minOf(1.0, hi))), boundary)
                assertEquals(3 * 16 * width, solidCount(boundary))
            }
        }
    }

    @Test fun `overhangs retain local boxes and subgrid geometry rounds outward`() {
        assertEquals(FluidBoundaryShape.CLOSED, FluidBoundaryRegistration.fromShape(Shapes.box(0.0, 0.0, 0.0, 1.0, 1.5, 1.0)))
        val outside = Shapes.box(-2.0, 0.0, 0.0, 0.0, 1.0, 1.0)
        val middle = Shapes.box(.25, .25, .25, .75, .75, .75)
        assertEquals(FluidBoundaryShape.OPEN, FluidBoundaryRegistration.fromShape(outside))
        assertEquals(FluidBoundaryRegistration.fromShape(middle), FluidBoundaryRegistration.fromShape(Shapes.or(outside, middle)))
        assertEquals(256, solidCount(FluidBoundaryRegistration.fromShape(Shapes.box(0.0, 0.0, 0.0, 0.001, 1.0, 1.0))))
    }
}
