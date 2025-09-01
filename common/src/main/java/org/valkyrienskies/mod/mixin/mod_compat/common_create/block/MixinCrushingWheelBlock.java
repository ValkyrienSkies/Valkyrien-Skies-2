package org.valkyrienskies.mod.mixin.mod_compat.common_create.block;

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
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CrushingWheelBlock.class)
public class MixinCrushingWheelBlock {

    @Unique
    private BlockPos blockPosInside;
    @Unique
    private Level levelInside;

    @Inject(method = "entityInside", at = @At("HEAD"))
    void startInside(
            final BlockState state, final Level worldIn, final BlockPos pos, final Entity entityIn,
            final CallbackInfo info) {
        blockPosInside = pos;
        levelInside = worldIn;
    }

    @Redirect(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D"
            )
    )
    double getXPos(final Entity entity) {
        return CompatUtil.INSTANCE.toSameSpaceAs(levelInside, entity.position(), blockPosInside).x;
    }

    @Redirect(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getY()D"
            )
    )
    double getYPos(final Entity entity) {
        return CompatUtil.INSTANCE.toSameSpaceAs(levelInside, entity.position(), blockPosInside).y;
    }

    @Redirect(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D"
            )
    )
    double getZPos(final Entity entity) {
        return CompatUtil.INSTANCE.toSameSpaceAs(levelInside, entity.position(), blockPosInside).z;
    }

}
