package org.valkyrienskies.mod.common.fluid

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.core.internal.physics.*
import org.valkyrienskies.core.internal.world.VsiClientShipWorld

class FluidOcclusionContentTest {
    @Test
    fun `new publication metadata retains identical spatial indices and mask`() {
        val f = MaskContentFixture()
        try {
            f.update()
            val topologyMap = f.index(f.topology!!)
            val floodingMap = f.index(f.flooding!!)
            val mask = f.data()
            f.update()
            assertSame(topologyMap, f.index(f.topology!!), "unchanged topology index")
            assertSame(floodingMap, f.index(f.flooding!!), "unchanged flooding index")
            assertSame(mask, f.data(), "unchanged mask must not be reconstructed")
            assertEquals(2L, f.flooding!!.snapshot.streamSequence)
        } finally { f.close() }
    }

    @Test
    fun `pressure and fluid identity update queries without rebuilding dry-space masks`() {
        mockkStatic(FluidOcclusionMaskData::class)
        val f = MaskContentFixture()
        try {
            f.update()
            val mask = f.data()
            repeat(8) { n ->
                f.rows = f.rows.map { it.copy(pressureAmount = n + 1, fluidId = 19 + n) }
                f.update()
                assertEquals(n + 1, f.flooding!!.get(1, 0, 0)!!.pressureAmount)
                assertEquals(19 + n, f.flooding!!.get(1, 0, 0)!!.fluidId)
                assertSame(mask, f.data(), "pressure-only publication ${n + 1}")
            }
            verify(exactly = 1) { FluidOcclusionMaskData.build(any(), any()) }
        } finally { f.close(); unmockkStatic(FluidOcclusionMaskData::class) }
    }

    @Test
    fun `reordering and topology flags retain mask identity while exposing new flags`() {
        val f = MaskContentFixture()
        try {
            f.update()
            val mask = f.data()
            f.domain = f.domain.reversed().map { it.copy(flags = 3) }
            f.rows = f.rows.reversed()
            f.update()
            assertEquals(3, f.topology!!.get(0, 0, 0)!!.flags)
            assertSame(mask, f.data())
            val map = f.index(f.topology!!)
            f.domain = f.domain.reversed()
            f.update()
            assertSame(map, f.index(f.topology!!))
            assertSame(mask, f.data())
        } finally { f.close() }
    }

    @Test
    fun `all partial fills and full-empty transitions match freshly constructed masks`() {
        val f = MaskContentFixture()
        try {
            var previous: FluidOcclusionMaskData? = null
            for (fill in 0..255) {
                f.rows = listOf(VsiFluidFloodedVoxel(1, 0, 0, 17, fill, 0))
                f.update()
                val actual = f.data()!!
                if (previous != null) assertNotSame(previous, actual, "fill $fill")
                f.assertFreshMask()
                previous = actual
            }
            f.rows = f.domain.map { VsiFluidFloodedVoxel(it.positionX, 0, 0, 17, 255, 0) }
            assertFalse(f.update())
            assertNull(f.data())
            f.rows = emptyList()
            assertTrue(f.update())
            f.assertFreshMask()
        } finally { f.close() }
    }

    @Test
    fun `outside-domain changes retain mask but moving its origin does not`() {
        val f = MaskContentFixture()
        try {
            f.update()
            val mask = f.data()!!
            f.rows = f.rows + VsiFluidFloodedVoxel(100, 0, 0, 17, 128, 0)
            f.update()
            assertSame(mask, f.data())
            f.domain = f.domain.map { it.copy(positionX = it.positionX + 10) }
            f.rows = f.rows.take(1).map { it.copy(positionX = it.positionX + 10) }
            f.update()
            assertNotSame(mask, f.data())
            assertEquals(mask.bytes, f.data()!!.bytes)
            assertEquals(10, f.data()!!.minX)
            f.assertFreshMask()
        } finally { f.close() }
    }

    @Test
    fun `disabled invalid empty and replacement worlds cannot reuse stale dry space`() {
        val f = MaskContentFixture()
        try {
            f.update()
            f.enabled = false
            assertFalse(f.update()); assertNull(f.data())
            f.enabled = true
            assertTrue(f.update()); f.assertFreshMask()
            f.valid = false
            assertFalse(f.update()); assertNull(f.data())
            f.valid = true
            assertTrue(f.update())
            val old = f.data()
            val previousSequence = f.sequence
            f.world = mockk<VsiClientShipWorld>()
            f.domain = listOf(VsiFluidTopologyVoxel(-10, 0, 0, 1))
            f.rows = emptyList()
            f.sequence = previousSequence - 1 // Same body and sequence, different world.
            assertTrue(f.update())
            assertNotSame(old, f.data())
            assertEquals(-10, f.data()!!.minX)
            f.domain = emptyList()
            assertFalse(f.update()); assertNull(f.data())
        } finally { f.close() }
    }

    @Test
    fun `failed direct upload retries unchanged content and commits only after success`() {
        mockkStatic(org.lwjgl.opengl.GL11::class, com.mojang.blaze3d.platform.GlStateManager::class)
        val f = MaskContentFixture()
        try {
            val type = f.mask.javaClass
            type.getDeclaredField("textureId").apply { isAccessible = true }.setInt(f.mask, 113)
            type.getDeclaredField("textureHeight").apply { isAccessible = true }.setInt(f.mask, 1)
            every { org.lwjgl.opengl.GL11.glIsTexture(113) } returns true
            every { org.lwjgl.opengl.GL11.glGetInteger(any()) } returns 0
            every { org.lwjgl.opengl.GL11.glPixelStorei(any(), any()) } just Runs
            every { com.mojang.blaze3d.platform.GlStateManager._bindTexture(any()) } just Runs
            var attempts = 0
            every { org.lwjgl.opengl.GL11.glTexSubImage2D(any(), any(), any(), any(), any(), any(), any(), any(), any<java.nio.ByteBuffer>()) } answers {
                if (++attempts == 1) error("injected upload failure")
            }
            val failure = assertThrows(java.lang.reflect.InvocationTargetException::class.java) { f.update(true) }
            assertEquals("injected upload failure", failure.cause!!.message)
            assertTrue(f.update(true))
            assertTrue(f.update(true))
            assertEquals(2, attempts)
        } finally { f.close(); unmockkStatic(org.lwjgl.opengl.GL11::class, com.mojang.blaze3d.platform.GlStateManager::class) }
    }
}

internal class MaskContentFixture : AutoCloseable {
    var world = mockk<VsiClientShipWorld>()
    var domain = listOf(VsiFluidTopologyVoxel(0, 0, 0, 1), VsiFluidTopologyVoxel(1, 0, 0, 1))
    var rows = listOf(VsiFluidFloodedVoxel(1, 0, 0, 17, 128, 0))
    var enabled = true
    var valid = true
    var sequence = 0L
    var topology: FluidTopologyClientCache.CachedSnapshot? = null
    var flooding: FloodedFluidClientCache.CachedSnapshot? = null
    private val type = Class.forName("org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer\$ShipMask")
    val mask: Any = type.getDeclaredConstructor(Long::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(7L)
    private val prepare = type.declaredMethods.first { it.name == "prepare" }.apply { isAccessible = true }
    private val dataField = type.getDeclaredField("data").apply { isAccessible = true }
    init { FluidTopologyClientCache.clear(); FloodedFluidClientCache.clear() }
    fun update(individualTexture: Boolean = false): Boolean {
        sequence++
        val publishedDomain = if (enabled && valid) domain else emptyList()
        val publishedRows = if (enabled && valid) rows else emptyList()
        val pair = VsiFluidGameplaySnapshot(
            VsiFluidTopologySnapshot(7, "minecraft:overworld", sequence, sequence, sequence, enabled, valid, publishedDomain.size, publishedDomain),
            VsiFluidFloodingSnapshot(7, "minecraft:overworld", sequence, sequence, sequence, publishedRows.size, publishedRows), true)
        topology = FluidTopologyClientCache.get(world, 7, pair)
        flooding = FloodedFluidClientCache.get(world, 7, pair)
        return prepare.invoke(mask, topology, flooding, individualTexture) as Boolean
    }
    fun data(): FluidOcclusionMaskData? = dataField.get(mask) as FluidOcclusionMaskData?
    fun assertFreshMask() {
        val expected = FluidOcclusionMaskData.build(topology, flooding)!!
        val actual = data()!!
        assertEquals(listOf(expected.minX, expected.minY, expected.minZ, expected.sizeX, expected.sizeY, expected.sizeZ, expected.partialCount),
            listOf(actual.minX, actual.minY, actual.minZ, actual.sizeX, actual.sizeY, actual.sizeZ, actual.partialCount))
        assertEquals(expected.bytes, actual.bytes)
    }
    fun index(snapshot: Any): Any = snapshot.javaClass.getDeclaredField("voxelsByPosition").apply { isAccessible = true }.get(snapshot)
    override fun close() { FluidTopologyClientCache.clear(); FloodedFluidClientCache.clear() }
}
