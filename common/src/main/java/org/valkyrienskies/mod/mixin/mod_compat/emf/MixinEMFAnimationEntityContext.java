package org.valkyrienskies.mod.mixin.mod_compat.emf;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.utils.EMFEntity;

@Mixin(EMFAnimationEntityContext.class)
public class MixinEMFAnimationEntityContext {
    @Shadow
    private static EMFEntity IEMFEntity;

    @Inject(
        at = @At("HEAD"),
        method = "distanceOfEntityFrom",
        cancellable = true
    )
    private static void distanceOfEntityFrom(final BlockPos pos, final CallbackInfoReturnable<Integer> cir) {
        if (IEMFEntity != null) {
            final var level = Minecraft.getInstance().level;
            final var posW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(pos));
            final var entityW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(IEMFEntity.etf$getBlockPos()));
            final var dist = posW.distanceTo(entityW);
            cir.setReturnValue((int) dist);
        }
    }
}
