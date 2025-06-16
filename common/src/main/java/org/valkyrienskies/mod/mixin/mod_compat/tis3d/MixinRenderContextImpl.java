package org.valkyrienskies.mod.mixin.mod_compat.tis3d;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import li.cil.tis3d.client.renderer.RenderContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(RenderContextImpl.class)
public abstract class MixinRenderContextImpl {
    @WrapOperation(
        method = "closeEnoughForDetails(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$closerToCenterThan(final BlockPos instance, final Position pos, final double dist,
        final Operation<Boolean> orig) {
        // this code has been deemed better by those at the forge discord (since it calls the og function)
        final Ship ship = VSGameUtilsKt.getShipManagingPos(Minecraft.getInstance().level, instance);
        if (ship != null) {
            return orig.call(instance, VectorConversionsMCKt.toMinecraft(ship.getWorldToShip()
                .transformPosition(VectorConversionsMCKt.toJOML(pos))), dist);
        }
        return orig.call(instance, pos, dist);
    }
}
