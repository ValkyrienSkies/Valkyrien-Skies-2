package org.valkyrienskies.mod.fabric

import io.mockk.every
import io.mockk.mockk
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.fabric.client.FabricFluidAppearance

class FluidAppearanceTest {
    @Test
    fun `registered custom handler supplies both sprites tint and emitted block light`() {
        net.minecraft.SharedConstants.tryDetectVersion()
        net.minecraft.server.Bootstrap.bootStrap()
        val fluid = mockk<Fluid>()
        val state = mockk<FluidState>()
        val block = mockk<BlockState>()
        val level = mockk<BlockAndTintGetter>()
        val still = mockk<TextureAtlasSprite>()
        val flowing = mockk<TextureAtlasSprite>()
        val handler = mockk<FluidRenderHandler>()
        every { state.type } returns fluid
        every { state.createLegacyBlock() } returns block
        every { block.lightEmission } returns 11
        every { handler.getFluidSprites(level, BlockPos.ZERO, state) } returns arrayOf(still, flowing)
        every { handler.getFluidColor(level, BlockPos.ZERO, state) } returns 0x224466
        FluidRenderHandlerRegistry.INSTANCE.register(fluid, handler)
        val appearance = FabricFluidAppearance().resolve(level, BlockPos.ZERO, state)
        assertSame(still, appearance.still())
        assertSame(flowing, appearance.flowing())
        assertEquals(0xFF224466.toInt(), appearance.tint())
        assertEquals(11, appearance.emission())
        assertEquals(1f, appearance.alpha())
    }
}
