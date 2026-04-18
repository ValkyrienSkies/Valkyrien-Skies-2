package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bee.BeeEnterHiveGoal.class)
public class MixinEnterHiveGoal {
    @Unique
    private Bee vs$bee;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vs$captureBee(final Bee bee, final CallbackInfo ci) {
        this.vs$bee = bee;
    }

    @WrapOperation(method = "canBeeUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean onCloserToCenterThan(BlockPos instance, Position position, double v, Operation<Boolean> original) {
        return original.call(BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(this.vs$bee.level(), instance)), position, v);
    }
}
