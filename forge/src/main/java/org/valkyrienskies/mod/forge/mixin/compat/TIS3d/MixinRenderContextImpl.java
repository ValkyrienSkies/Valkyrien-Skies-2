package org.valkyrienskies.mod.forge.mixin.compat.TIS3d;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import li.cil.tis3d.client.renderer.RenderContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(RenderContextImpl.class)
public abstract class MixinRenderContextImpl {
    @WrapOperation(
        method = "closeEnoughForDetails(Lnet/minecraft/core/BlockPos;)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;F)Z"
        )
    )
    private boolean ValkyrienSkies$closerToCenterThan(final BlockPos instance, final Position pos, final float dist,
        final Operation<Boolean> orig) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            Minecraft.getInstance().level,
            instance.getX(),
            instance.getY(),
            instance.getZ(),
            pos.x(),
            pos.y(),
            pos.z()
        ) <= dist;
        // I initially did this, but then ruby pointed out the function above
        //final ClientLevel lvl = Minecraft.getInstance().level;
        //final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(lvl, instance);
        //VSGameUtilsKt.squaredDistanceBetweenInclShips()
        //if (ship != null) {
        //    final Vector3d spos = ship.getTransform().getWorldToShip().transformPosition(
        //        new Vector3d(pos.x(), pos.y(), pos.z())
        //    );
        //    return orig.call(instance, new Vec3(spos.x, spos.y, spos.z), dist);
        //} else {
        //    return orig.call(instance, pos, dist);
        //}
    }
}
