package org.valkyrienskies.mod.mixin.mod_compat.common_create.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;

@Mixin(FunnelBlock.class)
public class MixinFunnelBlock {

    @WrapOperation(
        method = "entityInside",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 transformPos(
        Entity entity, Operation<Vec3> original,
        @Local(argsOnly = true) Level levelIn, @Local(argsOnly = true) BlockPos blockPos
    ) {
        return CompatUtil.INSTANCE.toSameSpaceAs(levelIn, original.call(entity), blockPos);
    }
}
