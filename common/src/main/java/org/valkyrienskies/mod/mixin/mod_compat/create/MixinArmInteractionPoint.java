package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmAngleTarget;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.compat.create.MechanicalArmCompat;

@Mixin(ArmInteractionPoint.class)
public abstract class MixinArmInteractionPoint {
    @Shadow protected Level level;
    @Shadow @Final protected BlockPos pos;

    @Shadow protected abstract Vec3 getInteractionPositionVector();
    @Shadow protected abstract Direction getInteractionDirection();

    @Inject(method = "getTargetAngles", at = @At("HEAD"), cancellable = true, remap = false)
    private void vs$followMovingTarget(BlockPos armPos, boolean ceiling, CallbackInfoReturnable<ArmAngleTarget> cir) {
        if (VSGameUtilsKt.getShipManagingPos(level, armPos) == VSGameUtilsKt.getShipManagingPos(level, pos)) {
            return;
        }
        // Relative ship transforms can change every tick; Create's cached angles are only valid within one ship.
        cir.setReturnValue(new ArmAngleTarget(armPos,
            MechanicalArmCompat.toArmSpace(level, armPos, pos, getInteractionPositionVector()),
            MechanicalArmCompat.toArmSpace(level, armPos, pos, getInteractionDirection()), ceiling));
    }
}
