package org.valkyrienskies.mod.common

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import java.util.function.Supplier

class VSRenderTypes(
    name: String,
    format: VertexFormat,
    mode: VertexFormat.Mode,
    bufferSize: Int,
    affectsCrumbling: Boolean,
    sortOnUpload: Boolean,
    setupState: Runnable,
    clearState: Runnable
) : RenderType(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState) {
    companion object {
        var shipSolidShader: ShaderInstance? = null
        var shipCutoutMippedShader: ShaderInstance? = null
        var shipCutoutShader: ShaderInstance? = null
        var shipTranslucentShader: ShaderInstance? = null

        private val RENDERTYPE_SHIP_SOLID_SHADER: ShaderStateShard = ShaderStateShard(Supplier { shipSolidShader })
        private val RENDERTYPE_SHIP_CUTOUT_MIPPED_SHADER: ShaderStateShard = ShaderStateShard(Supplier { shipCutoutMippedShader })
        private val RENDERTYPE_SHIP_CUTOUT_SHADER: ShaderStateShard = ShaderStateShard(Supplier { shipCutoutShader })
        private val RENDERTYPE_SHIP_TRANSLUCENT_SHADER: ShaderStateShard = ShaderStateShard(Supplier { shipTranslucentShader })

        // RenderTypes are not actually used anywhere, only shaders.
        // Needs refactoring to adjust for CompositeState.builder() being protected on Forge, not urgent for now?
        /*
        val SHIP_SOLID = create(
            ValkyrienSkiesMod.MOD_ID + "ship_solid",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            false,
            CompositeState.builder()
                .setLightmapState(LIGHTMAP)
                .setShaderState(RENDERTYPE_SHIP_SOLID_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .createCompositeState(true)
        );
        val SHIP_CUTOUT_MIPPED: RenderType = create(
            ValkyrienSkiesMod.MOD_ID + "ship_cutout_mipped",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            131072,
            true,
            false,
            CompositeState.builder()
                .setLightmapState(LIGHTMAP)
                .setShaderState(RENDERTYPE_SHIP_CUTOUT_MIPPED_SHADER)
                .setTextureState(BLOCK_SHEET_MIPPED)
                .createCompositeState(true)
        )
        val SHIP_CUTOUT: RenderType = create(
            ValkyrienSkiesMod.MOD_ID + "ship_cutout",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            131072,
            true,
            false,
            CompositeState.builder()
                .setLightmapState(LIGHTMAP)
                .setShaderState(RENDERTYPE_SHIP_CUTOUT_SHADER)
                .setTextureState(BLOCK_SHEET)
                .createCompositeState(true)
        )
        val TRANSLUCENT: RenderType = create(
            ValkyrienSkiesMod.MOD_ID + "translucent",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            true,
            translucentState(RENDERTYPE_SHIP_TRANSLUCENT_SHADER)
        )
        */

        fun shipShaderFor(renderType: RenderType): ShaderInstance? {
            return when (renderType) {
                RenderType.solid() -> shipSolidShader
                RenderType.cutoutMipped() -> shipCutoutMippedShader
                RenderType.cutout() -> shipCutoutShader
                RenderType.translucent() -> shipTranslucentShader
                else -> null
            }
        }
    }
}
