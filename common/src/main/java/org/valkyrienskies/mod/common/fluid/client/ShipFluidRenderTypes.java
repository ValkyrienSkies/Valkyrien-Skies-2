package org.valkyrienskies.mod.common.fluid.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.valkyrienskies.mod.mixin.accessors.client.render.RenderTypeAccessor;

public final class ShipFluidRenderTypes {


    // copy of the translucent render type
    public static final RenderType AIR_CULL_RENDER_TYPE =
        RenderType.create("vs_air_pocket", DefaultVertexFormat.BLOCK, Mode.QUADS, 2097152, true, true,
            RenderTypeAccessor.invokeTranslucentState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER));

    private ShipFluidRenderTypes() {
    }
}
