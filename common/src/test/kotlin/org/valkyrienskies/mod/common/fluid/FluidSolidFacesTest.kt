package org.valkyrienskies.mod.common.fluid

import io.mockk.every
import io.mockk.mockk
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class FluidSolidFacesTest {
    companion object {
        @JvmStatic @BeforeAll
        fun bootstrap() {
            net.minecraft.SharedConstants.tryDetectVersion()
            net.minecraft.server.Bootstrap.bootStrap()
        }
    }

    @Test
    fun `opaque walls cover only their touching fluid faces`() {
        val level = mockk<BlockGetter>()
        every { level.getBlockState(any()) } returns Blocks.OAK_PLANKS.defaultBlockState()
        val mask = FluidSolidFaces.mask(level, 30_000_000, 64, -30_000_000)
        assertEquals(63, mask)
        for (axis in 0..2) for (side in 0..1) {
            val face = listOf(Vector3d(), Vector3d(), Vector3d()).mapIndexed { index, p ->
                p.setComponent(axis, side.toDouble())
                p.setComponent((axis + 1) % 3, if (index == 1) 0.8 else 0.2)
                p.setComponent((axis + 2) % 3, if (index == 2) 0.8 else 0.2)
            }
            assertTrue(FluidSolidFaces.covered(face, 1 shl (axis * 2 + side)))
            assertFalse(FluidSolidFaces.covered(face, 1 shl (axis * 2 + 1 - side)))
            assertFalse(FluidSolidFaces.covered(face.map {
                Vector3d(it).setComponent(axis, it.get(axis) + if (side == 0) 0.01 else -0.01)
            }, mask))
        }
    }

    @Test
    fun `glass air and partial side openings retain fluid faces`() {
        val level = mockk<BlockGetter>()
        for (block in listOf(Blocks.GLASS, Blocks.AIR, Blocks.OAK_FENCE)) {
            every { level.getBlockState(any()) } returns block.defaultBlockState()
            assertEquals(0, FluidSolidFaces.mask(level, 0, 0, 0))
        }
        every { level.getBlockState(any()) } answers {
            if (firstArg<BlockPos>().y == 1) Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM) else Blocks.AIR.defaultBlockState()
        }
        assertEquals(1 shl 3, FluidSolidFaces.mask(level, 0, 0, 0))
        every { level.getBlockState(any()) } returns Blocks.AIR.defaultBlockState()
        assertEquals(0, FluidSolidFaces.mask(level, 0, 0, 0))
    }
}
