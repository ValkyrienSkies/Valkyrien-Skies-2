package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ContraptionControlsRenderer.class)
public abstract class MixinContraptionControlsRenderer {
    @WrapOperation(
        method = "renderInContraption",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", remap = true)
    )
    private static double redirectDistance(final Vec3 from, final Vec3 to, final Operation<Double> original){
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(Minecraft.getInstance().level, from, to, original);
    }
}
