package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.cannon.SchematicannonRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(SchematicannonRenderer.class)
public abstract class MixinSchematicannonRenderer {

    // Replace the final relative aim vector, not Create's integer target. Quantizing a
    // cross-ship conversion to BlockPos makes the barrel jump a full block at a time.
    @ModifyExpressionValue(method = "getCannonAngles", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;",
        ordinal = 0))
    private static Vec3 vs$currentDirection(Vec3 original, @Local(argsOnly = true) SchematicannonBlockEntity cannon,
        @Local(argsOnly = true) BlockPos base, @Local(ordinal = 1) BlockPos target) {
        return CompatUtil.INSTANCE.toRenderSpaceOf(cannon.getLevel(), target.getCenter(), base).subtract(base.getCenter());
    }

    @ModifyExpressionValue(method = "getCannonAngles", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vs$interpolatedDirection(Vec3 original, @Local(argsOnly = true) SchematicannonBlockEntity cannon,
        @Local(argsOnly = true) BlockPos base, @Local(ordinal = 1) BlockPos target, @Local(argsOnly = true) float t) {
        Vec3 previous = CompatUtil.INSTANCE.toRenderSpaceOf(cannon.getLevel(), cannon.previousTarget.getCenter(), base);
        Vec3 current = CompatUtil.INSTANCE.toRenderSpaceOf(cannon.getLevel(), target.getCenter(), base);
        return previous.lerp(current, t).subtract(base.getCenter());
    }

    @WrapOperation(
        method = "renderLaunchedBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;",
            ordinal = 1
        )
    )
    private static Vec3 transformTarget(
        Vec3i vec3i, Operation<Vec3> original, @Local(argsOnly = true) SchematicannonBlockEntity blockEntity
    ) {
        return CompatUtil.INSTANCE.toRenderSpaceOf(
            blockEntity.getLevel(), original.call(vec3i), blockEntity.getBlockPos()
        );
    }
}
