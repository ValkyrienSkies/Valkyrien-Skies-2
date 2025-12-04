package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.turntable.TurntableBlock;
import com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(TurntableBlock.class)
public class MixinTurntableBlock {
    @WrapOperation(
        method = "entityInside",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getY()D")
    )
    private double getYtoShip(Entity entity, Operation<Double> original,
        @Local(argsOnly = true) Level worldIn, @Local(argsOnly = true) BlockPos pos) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(worldIn, pos);
        if(ship != null) {
            return ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(entity.position())).y;
        } else return original.call(entity);
    }

    @WrapOperation(
        method = "lambda$entityInside$0",
        at = @At(value = "INVOKE", target = "Lnet/createmod/catnip/math/VecHelper;getCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 positionToWorld(Vec3i pos, Operation<Vec3> original, @Local(argsOnly = true) TurntableBlockEntity tbe) {
        final Vec3 result = original.call(pos);
        if(VSGameUtilsKt.isBlockInShipyard(tbe.getLevel(), tbe.getBlockPos())) {
            return VSGameUtilsKt.toWorldCoordinates(tbe.getLevel(), result);
        }
        else return result;
    }
}
