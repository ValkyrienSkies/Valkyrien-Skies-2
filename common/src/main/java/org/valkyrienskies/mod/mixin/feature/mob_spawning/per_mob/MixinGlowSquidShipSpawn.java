package org.valkyrienskies.mod.mixin.feature.mob_spawning.per_mob;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(GlowSquid.class)
public abstract class MixinGlowSquidShipSpawn {

    @WrapOperation(
        method = "checkGlowSquideSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;getY()I"
        )
    )
    private static int vs$shipGlowSquidY(
        final BlockPos pos, final Operation<Integer> original,
        final EntityType<? extends LivingEntity> type, final ServerLevelAccessor accessor,
        final MobSpawnType spawnType, final BlockPos posArg, final RandomSource random
    ) {
        return VSGameUtilsKt.shipProjectedWorldY(accessor, pos, original.call(pos));
    }

    @WrapOperation(
        method = "checkGlowSquideSpawnRules",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"
        )
    )
    private static int vs$shipGlowSquidBrightness(
        final ServerLevelAccessor accessor, final BlockPos pos, final int skyDarken,
        final Operation<Integer> original
    ) {
        return VSGameUtilsKt.shipAwareCombinedBrightness(accessor, pos, skyDarken,
            original.call(accessor, pos, skyDarken));
    }
}
