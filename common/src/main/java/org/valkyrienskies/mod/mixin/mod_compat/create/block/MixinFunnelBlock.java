package org.valkyrienskies.mod.mixin.mod_compat.create.block;

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
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

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
        Ship ship = ValkyrienSkies.getShipManagingPos(entity.level(), blockPos);
        Vec3 pos = original.call(entity);
        if (ship != null) {
            pos = ValkyrienSkies.transformToShip(ship, ValkyrienSkies.transformToWorld(levelIn, pos)); // same as the todo for mixin vibration system
        }
        return pos;
    }
}
