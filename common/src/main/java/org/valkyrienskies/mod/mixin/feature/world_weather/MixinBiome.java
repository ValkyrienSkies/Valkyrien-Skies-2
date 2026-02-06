package org.valkyrienskies.mod.mixin.feature.world_weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Biome.class)
public class MixinBiome {
    // We mixin specifically the temperature check, as checks for valid position, free space and others should happen in ship chunk.
    @WrapOperation(
        method = {
            "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            "shouldSnow"
        }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/Biome;warmEnoughToRain(Lnet/minecraft/core/BlockPos;)Z")
    )
    boolean useTemperatureAtWorldPos(Biome instance, BlockPos blockPos, Operation<Boolean> original, @Local(argsOnly = true) LevelReader levelReader) {
        if (levelReader instanceof Level level) return original.call(instance, BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(level, blockPos)));
        return original.call(instance, blockPos);
    }

    // Lighting from world blocks can prevent snow / ice formation on ships.
    @WrapOperation(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelReader;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I"))
    int useCombinedLighting(LevelReader instance, LightLayer lightLayer, BlockPos pos, Operation<Integer> original) {
        if (instance instanceof Level level) return CompatUtil.INSTANCE.getCompoundBrightness(level, lightLayer, pos, null);
        return original.call(instance, lightLayer, pos);
    }
}
