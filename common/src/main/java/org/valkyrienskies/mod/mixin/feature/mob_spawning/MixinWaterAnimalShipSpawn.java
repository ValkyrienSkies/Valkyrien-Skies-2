package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(WaterAnimal.class)
public abstract class MixinWaterAnimalShipSpawn {

    @WrapOperation(
        method = "checkSurfaceWaterAnimalSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private static int vs$shipWaterAnimalY(
        final BlockPos pos, final Operation<Integer> original,
        final EntityType<? extends WaterAnimal> type, final LevelAccessor level,
        final MobSpawnType spawnType, final BlockPos posArg, final RandomSource random
    ) {
        return VSGameUtilsKt.shipProjectedWorldY(level, pos, original.call(pos));
    }
}
