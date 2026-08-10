package org.valkyrienskies.mod.mixin.feature.fluid_render;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.fluid.client.ShipFluidRenderTypes;

@Mixin(RenderType.class)
public abstract class MixinRenderType {

    @Shadow
    @Final
    @Mutable
    private static ImmutableList<RenderType> CHUNK_BUFFER_LAYERS;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onClinit(CallbackInfo ci) {
        final ImmutableList.Builder<RenderType> builder = ImmutableList.builder();
        builder.addAll(CHUNK_BUFFER_LAYERS);
        builder.add(ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE);
        CHUNK_BUFFER_LAYERS = builder.build();
    }
}
