package org.valkyrienskies.mod.mixin.feature.fluid_render;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.RenderType;
import org.objectweb.asm.Opcodes;
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

    // Injected on the field write rather than at TAIL because Forge's clinit numbers every chunk layer
    // (RenderType.chunkLayerId) in a loop that follows it. A layer added after that loop keeps the default id of -1,
    // and ChunkRenderTypeSet.of then rejects it as "a non-chunk render type". On vanilla this write is the last
    // statement of clinit anyway, so the injection point is equivalent to TAIL there.
    @Inject(
        method = "<clinit>",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraft/client/renderer/RenderType;CHUNK_BUFFER_LAYERS:Lcom/google/common/collect/ImmutableList;",
            shift = At.Shift.AFTER
        )
    )
    private static void onClinit(final CallbackInfo ci) {
        CHUNK_BUFFER_LAYERS = ImmutableList.<RenderType>builder()
            .addAll(CHUNK_BUFFER_LAYERS)
            .add(ShipFluidRenderTypes.AIR_CULL_RENDER_TYPE)
            .build();
    }
}
