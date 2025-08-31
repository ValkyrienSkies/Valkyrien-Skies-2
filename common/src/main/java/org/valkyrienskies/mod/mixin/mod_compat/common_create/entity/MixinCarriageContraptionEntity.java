package org.valkyrienskies.mod.mixin.mod_compat.common_create.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.Position;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(CarriageContraptionEntity.class)
public abstract class MixinCarriageContraptionEntity {
    @WrapOperation(
            method = "control",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"
            )
    )
    private boolean wrapCloserThan(
        final Vec3 instance, final Position arg, final double d, final Operation<Boolean> closerThan,
        @Local(argsOnly = true) Player player
    ) {
        return closerThan.call(VSGameUtilsKt.toWorldCoordinates(player.level(), instance), arg, d);
    }
}
