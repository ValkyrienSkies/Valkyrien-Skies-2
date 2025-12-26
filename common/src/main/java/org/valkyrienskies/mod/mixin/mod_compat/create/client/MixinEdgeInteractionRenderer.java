package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(EdgeInteractionRenderer.class)
public class MixinEdgeInteractionRenderer {
    @WrapOperation(
        method="tick",
        at=@At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getLocation()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 injectGetLocation(HitResult instance, Operation<Vec3> original, @Local(ordinal = 0)
        ClientLevel world) {
        // Create already checks if hit result is a block hit result further up
        BlockHitResult hitResult = (BlockHitResult) instance;

        // BlockHitResult.getLocation returns a world position
        Vec3 originalResult = original.call(instance);

        Ship ship = VSGameUtilsKt.getShipManagingPos(world, hitResult.getBlockPos());
        if (ship != null) {
            Vector3d resultJoml = VectorConversionsMCKt.toJOML(originalResult);

            // Get it back to the shipyard for the AABB check
            ship.getTransform().getWorldToShip().transformPosition(resultJoml);

            return VectorConversionsMCKt.toMinecraft(resultJoml);
        }
        // No ship, don't do anything
        return originalResult;
    }
}
