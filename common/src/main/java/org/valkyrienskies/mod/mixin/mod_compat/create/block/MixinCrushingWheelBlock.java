package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CrushingWheelBlock.class)
public class MixinCrushingWheelBlock {
    @Unique
    void transform(final Vector3d in, BlockPos blockPosInside, Level levelInside) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos(levelInside, blockPosInside);
        if (ship != null) {
            ship.getWorldToShip().transformPosition(in);
        }
    }

    @WrapOperation(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    double getXPos(final Entity entity, Operation<Double> original,
        @Local(argsOnly = true) BlockPos blockPosInside, @Local(argsOnly = true) Level levelInside) {
        final Vector3d vector3d = new Vector3d(original.call(entity), entity.getY(), entity.getZ());
        transform(vector3d, blockPosInside, levelInside);
        return vector3d.x;
    }

    @WrapOperation(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getY()D"
            )
    )
    double getYPos(final Entity entity, Operation<Double> original,
        @Local(argsOnly = true) BlockPos blockPosInside, @Local(argsOnly = true) Level levelInside) {
        final Vector3d vector3d = new Vector3d(entity.getX(), original.call(entity), entity.getZ());
        transform(vector3d, blockPosInside, levelInside);
        return vector3d.y;
    }

    @WrapOperation(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    double getZPos(final Entity entity, Operation<Double> original,
        @Local(argsOnly = true) BlockPos blockPosInside, @Local(argsOnly = true) Level levelInside) {
        final Vector3d vector3d = new Vector3d(entity.getX(), entity.getY(), original.call(entity));
        transform(vector3d, blockPosInside, levelInside);
        return vector3d.z;
    }
}
