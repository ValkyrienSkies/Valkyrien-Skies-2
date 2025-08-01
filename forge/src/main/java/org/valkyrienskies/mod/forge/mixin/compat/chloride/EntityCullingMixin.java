package org.valkyrienskies.mod.forge.mixin.compat.chloride;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import me.srrapero720.chloride.impl.EntityCulling;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityCulling.class)
public class EntityCullingMixin {

    @Inject(method = "isEntityInRange(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;II)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void isEntityInRange(Level level, Vec3 position, Vec3 camera, int maxHeight, int maxDistanceSquared, CallbackInfoReturnable<Boolean> cir){
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceBetweenInclShips(level, position.x, position.y, position.z, camera.x, camera.y, camera.z) < maxDistanceSquared);
    }

}
