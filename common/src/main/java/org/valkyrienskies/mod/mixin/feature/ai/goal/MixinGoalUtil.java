package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(GoalUtils.class)
public class MixinGoalUtil {

    @WrapOperation(
        method = "isSolid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private static BlockState vs$getBlockStateIsSolid(
        Level instance, BlockPos blockPos, Operation<BlockState> original) {
        BlockState originalState = original.call(instance, blockPos);
        if (originalState.isSolid()) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.getX(), blockPos.getY(), blockPos.getZ());
        for (Vector3d candidate : candidates) {
            BlockPos candidatePos = BlockPos.containing(ValkyrienSkies.toMinecraft(candidate));
            BlockState candidateState = instance.getBlockState(candidatePos);
            if (candidateState.isSolid()) {
                return candidateState;
            }
        }
        return originalState;
    }

    @WrapOperation(
        method = "isWater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private static FluidState vs$getFluidStateIsWater(
        Level instance, BlockPos blockPos, Operation<FluidState> original) {
        FluidState originalState = original.call(instance, blockPos);
        if (originalState.is(FluidTags.WATER)) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.getX(), blockPos.getY(), blockPos.getZ());
        for (Vector3d candidate : candidates) {
            BlockPos candidatePos = BlockPos.containing(ValkyrienSkies.toMinecraft(candidate));
            FluidState candidateState = instance.getFluidState(candidatePos);
            if (candidateState.is(FluidTags.WATER)) {
                return candidateState;
            }
        }
        return originalState;
    }
}
