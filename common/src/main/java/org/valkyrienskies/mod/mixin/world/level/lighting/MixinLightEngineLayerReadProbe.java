package org.valkyrienskies.mod.mixin.world.level.lighting;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.render.MeshCompileLightProbe;

/**
 * Test-only probe for the AO-corner sampling path.
 *
 * <p>Smooth lighting (AO) reads per-vertex corner light via
 * {@code BlockAndTintGetter.getBrightness(LightLayer, BlockPos)}, whose
 * default implementation calls
 * {@code getLightEngine().getLayerListener(layer).getLightValue(pos)}.
 * Both {@link SkyLightEngine} and {@link BlockLightEngine} inherit
 * {@link LightEngine#getLightValue} without overriding, so hooking here
 * catches EVERY layered light read — including the AO-corner samples that
 * {@code LevelRenderer.getLightColor} never sees.
 *
 * <p>Only used in vsgametest: the probe is disarmed by default
 * ({@link MeshCompileLightProbe#isArmed} returns false → single volatile
 * read cost per call). Scope-filtering by section bounding box keeps
 * unrelated reads (e.g., entity rendering elsewhere) out of the capture list.
 *
 * <p>Layer discrimination: {@code this instanceof SkyLightEngine} tells us
 * the layer. If neither SkyLightEngine nor BlockLightEngine, we skip —
 * unknown subclass.
 */
@Mixin(LightEngine.class)
public abstract class MixinLightEngineLayerReadProbe {

    @ModifyReturnValue(
        method = "getLightValue(Lnet/minecraft/core/BlockPos;)I",
        at = @At("RETURN")
    )
    private int vs$captureLayerRead(final int result, final BlockPos pos) {
        if (!MeshCompileLightProbe.isArmed()) return result;
        final int layer;
        if ((Object) this instanceof SkyLightEngine) {
            layer = 0;
        } else if ((Object) this instanceof BlockLightEngine) {
            layer = 1;
        } else {
            return result;
        }
        MeshCompileLightProbe.captureLayerRead(pos, layer, result);
        return result;
    }
}
