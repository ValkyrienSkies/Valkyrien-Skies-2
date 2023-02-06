package org.valkyrienskies.mod.forge.mixin.compat.TIS3d;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import li.cil.tis3d.client.renderer.RenderContextImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(RenderContextImpl.class)
public abstract class MixinRenderContextImpl {
    @WrapOperation(
        method = "closeEnoughForDetails(Lnet/minecraft/core/BlockPos;)Z",
        remap = false,
        at = @At(value = "INVOKE",
            remap = true,
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean ValkyrienSkies$closerToCenterThan(final BlockPos instance, final Position pos, final double dist,
        final Operation<Boolean> orig) {
        // this code has been deemed better by those at the forge discord (since it calls the og function)
        final ClientShip ship = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, instance);
        if (ship != null) {
            final Vector3d spos = ship.getTransform().getWorldToShip().transformPosition(
                new Vector3d(pos.x(), pos.y(), pos.z())
            );
            return orig.call(instance, new Vec3(spos.x, spos.y, spos.z), dist);
        } else {
            return orig.call(instance, pos, dist);
        }
    }
}
