package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.foundation.blockEntity.behaviour.edgeInteraction.EdgeInteractionRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(EdgeInteractionRenderer.class)
public class MixinEdgeInteractionRenderer {
    @WrapOperation(
        method="tick",
        at=@At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getLocation()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 injectGetLocation(HitResult instance, Operation<Vec3> original, @Local(ordinal = 0)
        ClientLevel world) {
        if (instance instanceof BlockHitResult hit) return RaycastUtilsKt.blockHitForUse(world, hit).getLocation();
        return original.call(instance);
    }
}
