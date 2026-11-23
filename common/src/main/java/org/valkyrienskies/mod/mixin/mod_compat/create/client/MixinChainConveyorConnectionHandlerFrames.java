package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;

// Connecting between frames (see ChainConveyorFrames): steepness, vertical and chain cost checks.
@Mixin(ChainConveyorConnectionHandler.class)
public abstract class MixinChainConveyorConnectionHandlerFrames {
    @Shadow
    private static BlockPos firstPos;

    // Steepness and vertical checks, seen from the first conveyor.
    @WrapOperation(
        method = "validateAndConnect",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 vs$actualOffset(final Vec3i connection, final Operation<Vec3> original,
        @Local(argsOnly = true) final LevelAccessor level) {
        if (firstPos == null || !(level instanceof final Level l) || !(connection instanceof final BlockPos c)) {
            return original.call(connection);
        }
        return ChainConveyorFrames.offset(l, firstPos, c);
    }

    @WrapOperation(
        method = "validateAndConnect",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;getChainCost(Lnet/minecraft/core/BlockPos;)I")
    )
    private static int vs$actualCost(final BlockPos connection, final Operation<Integer> original,
        @Local(argsOnly = true) final LevelAccessor level) {
        if (firstPos == null || !(level instanceof final Level l)) return original.call(connection);
        return ChainConveyorFrames.chainCost(l, firstPos, connection);
    }
}
