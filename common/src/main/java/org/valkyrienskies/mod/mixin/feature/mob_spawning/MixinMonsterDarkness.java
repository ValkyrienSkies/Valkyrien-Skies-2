package org.valkyrienskies.mod.mixin.feature.mob_spawning;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Monster.class)
public class MixinMonsterDarkness {

    @WrapOperation(
        method = "isDarkEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            ordinal = 0
        )
    )
    private static int vs$minSkyShipAndWorld(
        final ServerLevelAccessor accessor, final LightLayer layer, final BlockPos pos,
        final Operation<Integer> original
    ) {
        return VSGameUtilsKt.shipAwareSkyBrightness(accessor, pos, original.call(accessor, layer, pos));
    }

    @WrapOperation(
        method = "isDarkEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"
        )
    )
    private static int vs$shipAwareMaxLocalRaw(
        final ServerLevelAccessor accessor, final BlockPos pos,
        final Operation<Integer> original
    ) {
        final int orig = original.call(accessor, pos);
        if (!(accessor instanceof ServerLevel serverLevel)) return orig;
        return VSGameUtilsKt.shipAwareCombinedBrightness(serverLevel, pos, serverLevel.getSkyDarken(), orig);
    }

    @WrapOperation(
        method = "isDarkEnoughToSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I"
        )
    )
    private static int vs$shipAwareMaxLocalRawStorm(
        final ServerLevelAccessor accessor, final BlockPos pos, final int skyDarken,
        final Operation<Integer> original
    ) {
        return VSGameUtilsKt.shipAwareCombinedBrightness(
            accessor, pos, skyDarken, original.call(accessor, pos, skyDarken)
        );
    }
}
