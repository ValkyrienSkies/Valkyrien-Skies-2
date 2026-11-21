package org.valkyrienskies.mod.common.fluid

import org.joml.Vector3d
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Random

class FluidParcelFaceSorterTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            net.minecraft.SharedConstants.tryDetectVersion()
            net.minecraft.server.Bootstrap.bootStrap()
        }
    }

    @Test
    fun `standard parcel render type sorts quads on upload`() {
        val type = net.minecraft.client.renderer.RenderType.entityTranslucent(
            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
        val sorting = net.minecraft.client.renderer.RenderType::class.java.getDeclaredField("sortOnUpload")
        sorting.isAccessible = true
        assertTrue(sorting.getBoolean(type))
    }

    @Test
    fun `reused sorter preserves distance order through changing cameras and face counts`() {
        val random = Random(1729)
        val sorter = FluidParcelFaceSorter()
        for (count in listOf(1, 128, 16, 0, 256, 4, 128)) {
            val parcels = List(count) {
                FluidParcelCuboids.Parcel(Vector3d(random.nextDouble() * 8, random.nextDouble() * 8,
                    random.nextDouble() * 8), 0.125, it % 2)
            }
            val faces = FluidParcelCuboids.build(parcels).faces()
            repeat(4) {
                val x = random.nextDouble() * 12
                val y = random.nextDouble() * 12
                val z = random.nextDouble() * 12
                val expected = faces.sortedByDescending { it.distanceSquared(x, y, z) }
                val actual = faces.toMutableList()
                sorter.sort(actual, x, y, z)
                assertEquals(expected, actual)
            }
            if (count == 16) sorter.clear()
        }
    }

    @Test
    fun `equal distances retain input order for coincident material faces`() {
        val sorter = FluidParcelFaceSorter()
        val faces = (0..15).map {
            FluidParcelCuboids.Face(0, if (it % 2 == 0) 1 else -1, 1.0, -0.5, 0.5, -0.5, 0.5, it)
        }
        for (input in listOf(faces, faces.reversed(), faces.drop(5))) {
            val actual = input.toMutableList()
            sorter.sort(actual, 0.0, 0.0, 0.0)
            assertEquals(input, actual)
        }
    }
}
