package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(MoveToTargetSink.class)
public class MixinMoveToTargetSink {
    @WrapOperation(method = "reachedTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;distManhattan(Lnet/minecraft/core/Vec3i;)I"))
    private int onDistManhattan(BlockPos instance, Vec3i vec3i, Operation<Integer> original, @Local(argsOnly = true) Mob mob) {
        return original.call(new BlockPos(VSGameUtilsKt.toWorldCoordinates(mob.level, instance)), vec3i);
    }
}
