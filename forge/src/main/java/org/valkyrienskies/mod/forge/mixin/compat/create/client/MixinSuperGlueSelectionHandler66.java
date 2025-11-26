package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.simibubi.create.foundation.utility.RaycastHelper;
import java.lang.reflect.Method;
import java.util.Iterator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * This mixin is for Create 6.0.6
 * <br>
 * For the 6.0.7+ equivalent, see {@link MixinSuperGlueSelectionHandler67}
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler")
public abstract class MixinSuperGlueSelectionHandler66 {
    @Unique
    private Vec3 newTarget;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceOrigin(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 redirectGetTraceOrigin(Player playerIn) {
        double range = playerIn.getAttribute(ForgeMod.ENTITY_REACH.get()).getValue() + 1;

        // We have to use reflection here so this class can compile on 6.0.7,
        // even though it won't be run unless we're on 6.0.6
        Vec3 origin;
        try {
            final Class<?> clazz = RaycastHelper.class;
            final Method getTraceOrigin = clazz.getDeclaredMethod("getTraceOrigin", Player.class);
            origin = (Vec3) getTraceOrigin.invoke(null, playerIn);

        } catch (Exception e) {
            // Shouldn't ever happen, since this mixin class won't be loaded if we aren't on 6.0.6
            origin = new Vec3(0, 0, 0);
        }

        Vec3 target = RaycastHelper.getTraceTarget(playerIn, range, origin);


        AABB searchAABB = new AABB(origin, target).inflate(0.25, 2, 0.25);
        final Iterator<Ship> ships = VSGameUtilsKt.getShipsIntersecting(playerIn.level(), searchAABB).iterator();

        if (ships.hasNext()) {
            Ship ship = ships.next();

            Matrix4d world2Ship = (Matrix4d) ship.getTransform().getWorldToShip();
            AABBic shAABBi = ship.getShipAABB();

            // Hopefully fixes https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/1341
            // Presumably the AABB is null while the ship is still loading
            // This fix is also applied on the fabric version of this class
            if (shAABBi != null) {
                AABB shipAABB = new AABB(shAABBi.minX(), shAABBi.minY(), shAABBi.minZ(), shAABBi.maxX(), shAABBi.maxY(), shAABBi.maxZ());


                origin = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(origin)));
                target = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(target)));

                Quaterniond tempQuat = new Quaterniond();
                if (playerIn.getVehicle() != null && playerIn.getVehicle().getBoundingBox().intersects(shipAABB.inflate(20))) {
                    ship.getTransform().getWorldToShip().getNormalizedRotation(tempQuat);
                    tempQuat.invert();
                    Vector3d offset = VectorConversionsMCKt.toJOML(target.subtract(origin));
                    tempQuat.transform(offset);
                    target = origin.add(VectorConversionsMCKt.toMinecraft(offset));
                }
            }
        }

        newTarget = target;
        return origin;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"), remap = false)
    private Vec3 redirectGetTraceTarget(final Player playerIn, final double range, final Vec3 origin) {
        return newTarget;
    }
}
