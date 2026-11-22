package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SculkShriekerBlockEntity.class)
public abstract class MixinSculkShriekerShipDarknessRange {

    @WrapOperation(
        method = "tryRespond",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private Vec3 vs$worldRenderedDarknessOrigin(
        final Vec3i pos, final Operation<Vec3> original,
        @Local(argsOnly = true) final ServerLevel level
    ) {
        return VSGameUtilsKt.toWorldCoordinates(level, original.call(pos));
    }
}
