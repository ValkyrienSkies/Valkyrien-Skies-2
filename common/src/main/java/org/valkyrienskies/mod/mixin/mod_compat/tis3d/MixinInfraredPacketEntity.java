package org.valkyrienskies.mod.mixin.mod_compat.tis3d;


import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import li.cil.tis3d.common.entity.InfraredPacketEntity;
import li.cil.tis3d.util.Raytracing;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.compat.tis3d.Tis3dClipContext;

@Pseudo
@Mixin(InfraredPacketEntity.class)
public class MixinInfraredPacketEntity {
    @WrapOperation(
        remap = false,
        method = "checkCollision()Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE", remap = true,
            target = "li/cil/tis3d/util/Raytracing.raytrace (Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lli/cil/tis3d/util/Raytracing$CollisionDetector;)Lnet/minecraft/world/phys/HitResult;")
    )
    private HitResult vs$raytrace(final Level level, final Vec3 start, final Vec3 target,
        final Raytracing.CollisionDetector collisionDetector,
        final Operation<HitResult> orig) {
        return RaycastUtilsKt.clipIncludeShips(level,
            new Tis3dClipContext(start, target, Block.VISUAL, Fluid.ANY, null));
    }
}

