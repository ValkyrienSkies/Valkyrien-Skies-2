package org.valkyrienskies.mod.mixin.mod_compat.hexcasting;

import at.petrak.hexcasting.common.casting.actions.raycast.OpEntityRaycast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Pseudo
@Mixin(OpEntityRaycast.class)
public class MixinOpEntityRaycast {
    @WrapOperation(method = "getEntityHitResult", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/phys/AABB;clip(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/Optional;"))
    private Optional<Vec3> valkyrienskies$wrapEntityClip(AABB instance, Vec3 vec3, Vec3 vec32, Operation<Optional<Vec3>> original, @Local(argsOnly = true) Level level) {
        return original.call(ValkyrienSkies.toWorld(level, instance), vec3, vec32);
    }
}
