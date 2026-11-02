package org.valkyrienskies.mod.common.fluid

import com.mojang.blaze3d.platform.TextureUtil
import io.mockk.*
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

class FluidOcclusionLifecycleTest {
    @Test
    fun `closing a shader releases masks and a reused program id gets fresh locations`() {
        // Exercise resource ownership without requiring a window or GL context.
        mockkStatic(GL20::class, GL11::class, TextureUtil::class)
        try {
            every { GL20.glGetUniformLocation(31, any<CharSequence>()) } returns 5
            every { GL11.glIsTexture(113) } returns true
            every { TextureUtil.releaseTextureId(113) } just Runs
            FluidOcclusionRenderer.clear()
            assertTrue(FluidOcclusionRenderer.supportsProgram(31))
            assertTrue(FluidOcclusionRenderer.supportsProgram(31))
            verify(exactly = 1) { GL20.glGetUniformLocation(31, "VsFluidOcclusionEnabled") }

            val masksField = FluidOcclusionRenderer::class.java.getDeclaredField("SHIP_MASKS")
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val masks = masksField.get(null) as Long2ObjectOpenHashMap<Any>
            val maskType = Class.forName("org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer\$ShipMask")
            val mask = maskType.getDeclaredConstructor(Long::class.javaPrimitiveType)
                .apply { isAccessible = true }.newInstance(7L)
            val textureField = maskType.getDeclaredField("textureId").apply { isAccessible = true }
            textureField.setInt(mask, 113)
            masks.put(7L, mask)

            FluidOcclusionRenderer.onProgramClosed(31)
            verify(exactly = 1) { TextureUtil.releaseTextureId(113) }
            assertTrue(masks.isEmpty())
            assertEquals(0, textureField.getInt(mask))
            every { GL20.glGetUniformLocation(31, any<CharSequence>()) } returns -1
            assertFalse(FluidOcclusionRenderer.supportsProgram(31))
            verify(exactly = 2) { GL20.glGetUniformLocation(31, "VsFluidOcclusionEnabled") }

            // Disconnect also releases masks, independently of whether a fluid pass runs again.
            textureField.setInt(mask, 113)
            masks.put(7L, mask)
            FluidOcclusionRenderer.clear()
            assertTrue(masks.isEmpty())
            assertFalse(FluidOcclusionRenderContext.isActive())
            assertNull(FluidOcclusionRenderContext.getLevel())
            verify(exactly = 2) { TextureUtil.releaseTextureId(113) }
            every { GL20.glGetUniformLocation(31, any<CharSequence>()) } returns 12
            every { GL20.glUniform1f(12, 0f) } just Runs
            every { GL20.glUniform1i(any(), any()) } just Runs
            // A still-live ShaderInstance may already cache "supported" across a disconnect.
            FluidOcclusionRenderer.disableForProgram(31)
            verify(exactly = 1) { GL20.glUniform1f(12, 0f) }
        } finally {
            FluidOcclusionRenderer.clear()
            unmockkStatic(GL20::class, GL11::class, TextureUtil::class)
        }
    }
}
