package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.contraptions.components.crusher.CrushingWheelBlock;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CrushingWheelBlock.class)
public class MixinCrushingWheelBlock {

    @Unique
    private BlockPos blockPosInside;
    @Unique
    private Level levelInside;

    @Inject(method = "entityInside", at = @At("HEAD"))
    void startInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn, CallbackInfo info) {
        blockPosInside = pos;
        levelInside = worldIn;
    }

    @Unique
    void tranform(Vector3d in) {
        VSGameUtilsKt.getShipManagingPos(levelInside, blockPosInside).getShipTransform()
            .getWorldToShipMatrix().transformPosition(in);
    }

    @Redirect(
        method = "entityInside",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getX()D"
        )
    )
    double getXPos(Entity entity) {
        Vector3d vector3d = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        tranform(vector3d);
        return vector3d.x;
    }

    @Redirect(
        method = "entityInside",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getY()D"
        )
    )
    double getYPos(Entity entity) {
        Vector3d vector3d = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        tranform(vector3d);
        return vector3d.x;
    }

    @Redirect(
        method = "entityInside",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getZ()D"
        )
    )
    double getZPos(Entity entity) {
        Vector3d vector3d = new Vector3d(entity.getX(), entity.getY(), entity.getZ());
        tranform(vector3d);
        return vector3d.x;
    }

}
