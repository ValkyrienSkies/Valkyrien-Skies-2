package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import net.minecraft.core.Position;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainConveyorRenderer.class)
public abstract class MixinChainConveyorRenderer {
    @WrapOperation(
        method = "renderChains",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z")
    )
    private boolean wrapCloserThan(final Vec3 instance, final Position position, final double d, final Operation<Boolean> original, final @NotNull ChainConveyorBlockEntity be){
        if (VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos()) instanceof ClientShip ship){
            final Vector3d worldPos = ship.getShipToWorld().transformPosition(position.x(), position.y(), position.z(), new Vector3d());
            return original.call(instance, VectorConversionsMCKt.toMinecraft(worldPos), d);
        } else return original.call(instance, position, d);
    }
}
