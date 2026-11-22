package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.compat.create.ChainConveyorFrames;

// Riding between frames (see ChainConveyorFrames): branch choice.
@Mixin(ChainConveyorRidingHandler.class)
public abstract class MixinChainConveyorRidingHandlerFrames {
    // Picks the branch the player looks along.
    @WrapOperation(
        method = "updateTargetPosition",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 vs$actualOffset(final Vec3i connection, final Operation<Vec3> original,
        @Local(argsOnly = true) final ChainConveyorBlockEntity clbe) {
        if (!(connection instanceof final BlockPos c)) return original.call(connection);
        return ChainConveyorFrames.offset(clbe.getLevel(), clbe.getBlockPos(), c);
    }
}
