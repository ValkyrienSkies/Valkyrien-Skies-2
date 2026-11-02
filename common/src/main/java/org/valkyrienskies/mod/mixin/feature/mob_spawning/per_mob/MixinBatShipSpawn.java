package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bat.class)
public abstract class MixinBatShipSpawn {

    @WrapOperation(
        method = "checkBatSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I",
            ordinal = 0
        )
    )
    private static int vs$shipBatY(
        final BlockPos pos, final Operation<Integer> original,
        final EntityType<Bat> type, final LevelAccessor level,
        final MobSpawnType spawnType, final BlockPos posArg, final RandomSource random
    ) {
        return VSGameUtilsKt.shipProjectedWorldY(level, pos, original.call(pos));
    }

    @WrapOperation(
        method = "checkBatSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I"
        )
    )
    private static int vs$shipBatMaxRawBrightness(
        final LevelAccessor level, final BlockPos pos, final Operation<Integer> original
    ) {
        final int orig = original.call(level, pos);
        if (!(level instanceof ServerLevel serverLevel)) return orig;
        return VSGameUtilsKt.shipAwareCombinedBrightness(serverLevel, pos, serverLevel.getSkyDarken(), orig);
    }
}
