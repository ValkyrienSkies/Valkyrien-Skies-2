package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.MechanicalArmCompat;

@Mixin(ArmInteractionPointHandler.class)
public abstract class MixinArmInteractionPointHandler {
    @WrapOperation(
        method = "flushSettings",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z")
    )
    private static boolean vs$checkRange(BlockPos targetPos, Vec3i armPos, double range, Operation<Boolean> original,
        @Local ArmInteractionPoint point) {
        return MechanicalArmCompat.isWithinRange(point.getLevel(), new BlockPos(armPos), targetPos, range);
    }
}
