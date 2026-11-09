package org.valkyrienskies.mod.mixin.mod_compat.vista;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.ml_classes.IOneUserInteractable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(IOneUserInteractable.class)
public interface IOneUserInteractableMixin {
    @WrapOperation(
        method = "isCloseEnoughToUse",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double wrapDist1(AABB aabb, Vec3 pos, Operation<Double> original, @Local(argsOnly = true) Entity entity){
        return original.call(VSGameUtilsKt.transformAabbToWorld(entity.level(), aabb), pos);
    }

    @WrapOperation(
        method = "canInteractWithBlock",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double wrapDist2(AABB aabb, Vec3 pos, Operation<Double> original, @Local(argsOnly = true) Player player){
        return original.call(VSGameUtilsKt.transformAabbToWorld(player.level(), aabb), pos);
    }
}
