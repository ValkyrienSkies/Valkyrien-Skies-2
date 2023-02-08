package org.valkyrienskies.mod.forge.mixin.compat.tis3d;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import li.cil.tis3d.util.Raytracing;
import li.cil.tis3d.util.Raytracing.CollisionDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(Raytracing.class)
public class MixinRaytracing {
    @WrapOperation(
        remap = false,
        method = "raytrace(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lli/cil/tis3d/util/Raytracing$CollisionDetector;)Lnet/minecraft/world/phys/HitResult;",
        at = @At(value = "INVOKE", remap = true,
            target = "li/cil/tis3d/util/Raytracing$CollisionDetector.intersect (Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/HitResult;")
    )
    private static HitResult vs$raytrace(final CollisionDetector cd, final Level level, final BlockPos position,
        final Vec3 start, final Vec3 end,
        final Operation<HitResult> original) {
        //System.out.println("Called mixin for raytrace");
        final Iterable<Ship> ships = VSGameUtilsKt.getShipsIntersecting(level, new AABB(start, end));
        HitResult output = original.call(cd, level, position, start, end);
        Double lowestDistance = output != null ? output.getLocation().distanceTo(start) : Double.MAX_VALUE;
        for (final Ship ship : ships) {

            //translate World cordinates to intersecting ship cordinates
            final Vector3d pos_joml = ship.getTransform().getWorldToShip()
                .transformPosition(new Vector3d(position.getX(), position.getY(), position.getZ()));
            final Vector3d star_joml =
                ship.getTransform().getWorldToShip().transformPosition(new Vector3d(start.x, start.y, start.z));
            final Vector3d stop_joml =
                ship.getTransform().getWorldToShip().transformPosition(new Vector3d(end.x, end.y, end.z));

            //convert JOML vectors to minecraft vectors
            final BlockPos pos = new BlockPos(pos_joml.x, pos_joml.y, pos_joml.z);
            final Vec3 star = new Vec3(star_joml.x, star_joml.y, star_joml.z);
            final Vec3 stop = new Vec3(stop_joml.x, stop_joml.y, stop_joml.z);

            final HitResult translatedRes = original.call(cd, level, pos, star, stop);
            if (translatedRes != null) {
                if (translatedRes.getType() != Type.MISS) {
                    if (translatedRes.getLocation().distanceTo(star) < lowestDistance) {
                        lowestDistance = translatedRes.getLocation().distanceTo(star);
                        output = translatedRes;
                    }
                }
            }
        }
        return output;
    }

}
