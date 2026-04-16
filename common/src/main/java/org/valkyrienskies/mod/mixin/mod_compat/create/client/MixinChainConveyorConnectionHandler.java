package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChainConveyorConnectionHandler.class)
public abstract class MixinChainConveyorConnectionHandler {
    @Shadow
    private static BlockPos firstPos;

    @Inject(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;length()D"), cancellable = true
    )
    private static void cancelDrawIfTooFar(CallbackInfo ci, @Local Level level, @Local BlockPos pos) {
        if (VSGameUtilsKt.getShipManagingPos(level, firstPos) != VSGameUtilsKt.getShipManagingPos(level, pos)) {
            // Cannot connect chain conveyors between ships, do not render anything.
            ci.cancel();
        }
    }
}
