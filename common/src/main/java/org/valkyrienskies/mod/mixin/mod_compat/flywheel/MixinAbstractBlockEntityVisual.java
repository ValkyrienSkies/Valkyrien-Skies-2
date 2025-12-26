package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(AbstractBlockEntityVisual.class)
public class MixinAbstractBlockEntityVisual<T extends BlockEntity> {
    @Shadow
    @Final
    protected T blockEntity;

    @WrapOperation(
        method = "isVisible",
        at = @At(value = "INVOKE", target = "Lorg/joml/FrustumIntersection;testSphere(FFFF)Z"),
        remap = false
    )
    private boolean testRedirected(FrustumIntersection instance, float x, float y, float z, float r,
        Operation<Boolean> original) {
        Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(blockEntity.getLevel(), blockEntity.getBlockPos().getCenter());
        return original.call(instance, (float)worldPos.x, (float)worldPos.y, (float)worldPos.z, r);
    }

    @Redirect(
        method = "doDistanceLimitThisFrame",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D")
    )
    private double distInclShips(BlockPos blockPos, Position position, @Local(argsOnly = true) DynamicVisual.Context context){
        return VSGameUtilsKt.toWorldCoordinates(context.camera().getEntity().level(), blockPos.getCenter()).distanceToSqr(position.x(), position.y(), position.z());
    }
}
