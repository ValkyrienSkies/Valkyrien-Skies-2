package org.valkyrienskies.mod.fabric.mixin.feature.air_pockets;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.mixinducks.feature.air_pockets.ship_water_pockets.ShipWaterPocketEntityDuck;

@Mixin(Entity.class)
public abstract class MixinEntityFluidPushing {

    @Unique
    private boolean valkyrienair$ignoreWorldWaterInAirPocket = false;

    @Shadow
    public Level level;

    @Inject(
        method = "updateFluidHeightAndDoFluidPushing",
        at = @At("HEAD")
    )
    private void valkyrienair$setupWaterPocketFluidPushOverride(final TagKey<Fluid> tagKey, final double d,
        final CallbackInfoReturnable<Boolean> cir) {
        valkyrienair$ignoreWorldWaterInAirPocket = false;
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        valkyrienair$ignoreWorldWaterInAirPocket =
            ((ShipWaterPocketEntityDuck) (Object) this).vs$isInShipAirPocketForWorldWater();
    }

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private FluidState valkyrienair$overrideFluidStateForWaterPockets(final Level level, final BlockPos blockPos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, blockPos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        if (original.isEmpty() || !original.is(Fluids.WATER)) return original;

        if (valkyrienair$ignoreWorldWaterInAirPocket) {
            return Fluids.EMPTY.defaultFluidState();
        }

        return ShipWaterPocketManager.overrideWaterFluidState(level, blockPos, original);
    }
}

