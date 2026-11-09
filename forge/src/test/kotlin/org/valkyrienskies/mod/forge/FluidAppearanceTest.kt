package org.valkyrienskies.mod.forge

import io.mockk.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.material.FluidState
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.minecraftforge.fluids.FluidType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.valkyrienskies.mod.forge.client.ForgeFluidAppearance
import java.util.function.Function

class FluidAppearanceTest {
    @Test
    fun `custom fluid type extension supplies texture tint alpha and context light`() {
        net.minecraft.SharedConstants.tryDetectVersion()
        mockkStatic(net.minecraftforge.network.NetworkHooks::class)
        every { net.minecraftforge.network.NetworkHooks.init() } just Runs
        try { net.minecraft.server.Bootstrap.bootStrap() } finally { unmockkStatic(net.minecraftforge.network.NetworkHooks::class) }
        val state = mockk<FluidState>()
        val type = mockk<FluidType>()
        val extension = mockk<IClientFluidTypeExtensions>()
        val level = mockk<BlockAndTintGetter>()
        val minecraft = mockk<Minecraft>()
        val still = mockk<TextureAtlasSprite>()
        val flowing = mockk<TextureAtlasSprite>()
        val stillId = ResourceLocation.fromNamespaceAndPath("fluid_test", "still")
        val flowId = ResourceLocation.fromNamespaceAndPath("fluid_test", "flow")
        every { state.fluidType } returns type
        every { type.renderPropertiesInternal } returns extension
        every { type.getLightLevel(state, level, BlockPos.ZERO) } returns 9
        every { extension.getStillTexture(state, level, BlockPos.ZERO) } returns stillId
        every { extension.getFlowingTexture(state, level, BlockPos.ZERO) } returns flowId
        every { extension.getTintColor(state, level, BlockPos.ZERO) } returns 0x80224466.toInt()
        every { minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS) } returns Function {
            if (it == stillId) still else if (it == flowId) flowing else error("Unexpected sprite $it")
        }
        mockkStatic(Minecraft::class, ItemBlockRenderTypes::class)
        try {
            every { Minecraft.getInstance() } returns minecraft
            every { ItemBlockRenderTypes.getRenderLayer(state) } returns RenderType.translucent()
            val appearance = ForgeFluidAppearance().resolve(level, BlockPos.ZERO, state)
            assertSame(still, appearance.still())
            assertSame(flowing, appearance.flowing())
            assertEquals(0x80224466.toInt(), appearance.tint())
            assertEquals(9, appearance.emission())
            assertEquals(128 / 255f, appearance.alpha())
            every { extension.getTintColor(state, level, BlockPos.ZERO) } returns 0x00ffffff
            every { ItemBlockRenderTypes.getRenderLayer(state) } returns RenderType.solid()
            val opaque = ForgeFluidAppearance().resolve(level, BlockPos.ZERO, state)
            assertEquals(0xffffffff.toInt(), opaque.tint())
            assertEquals(1f, opaque.alpha())
            assertSame(still, opaque.still())
            assertSame(flowing, opaque.flowing())
            every { ItemBlockRenderTypes.getRenderLayer(state) } returns RenderType.translucent()
            assertEquals(0f, ForgeFluidAppearance().resolve(level, BlockPos.ZERO, state).alpha())
        } finally { unmockkStatic(Minecraft::class, ItemBlockRenderTypes::class) }
    }
}
