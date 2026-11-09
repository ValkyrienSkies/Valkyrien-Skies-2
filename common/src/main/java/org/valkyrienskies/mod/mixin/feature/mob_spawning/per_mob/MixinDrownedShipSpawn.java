package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Drowned.class)
public abstract class MixinDrownedShipSpawn {

    @WrapOperation(
        method = "isDeepEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private static int vs$shipDeepEnoughY(
        final BlockPos receiver, final Operation<Integer> original,
        final LevelAccessor level, final BlockPos pos
    ) {
        return VSGameUtilsKt.shipProjectedWorldY(level, receiver, original.call(receiver));
    }
}
