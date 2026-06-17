package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Husk.class)
public abstract class MixinHuskShipSpawn {

    @WrapOperation(
        method = "checkHuskSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;canSeeSky(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private static boolean vs$shipAwareCanSeeSky(
        final ServerLevelAccessor instance, final BlockPos pos, final Operation<Boolean> original
    ) {
        if (!(instance instanceof Level level)) return original.call(instance, pos);
        return VSGameUtilsKt.shipAwareCanSeeSky(level, pos);
    }
}
