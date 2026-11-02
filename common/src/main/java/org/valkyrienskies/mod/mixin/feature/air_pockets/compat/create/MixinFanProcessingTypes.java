package org.valkyrienskies.mod.mixin.feature.air_pockets.compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;

@Pseudo
@Mixin(
    targets = {
        "com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes$BlastingType",
        "com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes$HauntingType",
        "com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes$SmokingType",
        "com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes$SplashingType"
    },
    remap = false
)
public abstract class MixinFanProcessingTypes {

    @Unique
    private static final BlockState valkyrienair$AIR = Blocks.AIR.defaultBlockState();

    @WrapOperation(
        method = "isValidAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        ),
        require = 0
    )
    private FluidState valkyrienair$overrideCreateFanProcessingFluidState(final Level level, final BlockPos pos,
        final Operation<FluidState> getFluidState) {
        final FluidState original = getFluidState.call(level, pos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;
        return ShipWaterPocketManager.overrideWaterFluidState(level, pos, original);
    }

    @WrapOperation(
        method = "isValidAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        require = 0
    )
    private BlockState valkyrienair$overrideCreateFanProcessingBlockState(final Level level, final BlockPos pos,
        final Operation<BlockState> getBlockState) {
        final BlockState original = getBlockState.call(level, pos);
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return original;

        final FluidState fluidState = original.getFluidState();
        if (fluidState.isEmpty()) return original;

        return ShipWaterPocketManager.isWorldPosInShipAirPocket(level, pos) ? valkyrienair$AIR : original;
    }
}
