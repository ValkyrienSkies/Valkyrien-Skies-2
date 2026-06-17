package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(BaseSpawner.class)
public abstract class MixinBaseSpawnerSky {

    @WrapOperation(
        method = "serverTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            ordinal = 1
        )
    )
    private int vs$customSpawnRulesMinSky(
        final ServerLevel level, final LightLayer layer, final BlockPos pos,
        final Operation<Integer> original
    ) {
        return VSGameUtilsKt.shipAwareSkyBrightness(level, pos, original.call(level, layer, pos));
    }
}
