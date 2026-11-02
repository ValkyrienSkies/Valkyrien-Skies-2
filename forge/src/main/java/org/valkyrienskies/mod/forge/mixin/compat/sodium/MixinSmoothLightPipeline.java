package org.valkyrienskies.mod.forge.mixin.compat.sodium;

import me.jellysquid.mods.sodium.client.model.light.data.QuadLightData;
import me.jellysquid.mods.sodium.client.model.light.smooth.SmoothLightPipeline;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = SmoothLightPipeline.class, remap = false)
public class MixinSmoothLightPipeline {

    @Inject(method = "calculate", at = @At(value = "INVOKE",
        target = "Lme/jellysquid/mods/sodium/client/model/light/smooth/SmoothLightPipeline;applySidedBrightness(Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Lnet/minecraft/core/Direction;Z)V"), cancellable = true)
    private void calculateInject(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace,
        Direction lightFace, boolean shade, CallbackInfo ci) {

        Level level = Minecraft.getInstance().level;
        if (level != null && VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "calculate", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/model/light/smooth/SmoothLightPipeline;applySidedBrightnessFromNormals(Lme/jellysquid/mods/sodium/client/model/light/data/QuadLightData;Lme/jellysquid/mods/sodium/client/model/quad/ModelQuadView;Z)V"), cancellable = true)
    private void calculateInject2(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace,
        Direction lightFace, boolean shade, CallbackInfo ci) {

        Level level = Minecraft.getInstance().level;
        if (level != null && VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            ci.cancel();
        }
    }
}
