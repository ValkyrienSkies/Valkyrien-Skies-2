package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

import com.jamieswhiteshirt.reachentityattributes.ReachEntityAttributes;
import com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler;
import com.simibubi.create.foundation.utility.RaycastHelper;
import java.util.Iterator;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * Ship glue selection for Create builds the split-piece hooks ({@link MixinSuperGlueSelectionHandler}) aren't
 * verified on: moves Create's selection ray into the space of the ship it crosses. Neither hook is required, so a
 * build without these calls keeps Create's own selection.
 */
@Mixin(SuperGlueSelectionHandler.class)
public abstract class MixinSuperGlueSelectionRay {
    @Unique
    private Vec3 newTarget;

    @Redirect(method = "tick", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetTraceOrigin(final LocalPlayer playerIn) {
        double range = ReachEntityAttributes.getReachDistance(playerIn, playerIn.isCreative() ? 5.0 : 4.5); // PlatformUtils.getReachDistance(playerIn) + 1;
        Vec3 origin = playerIn.getEyePosition();
        Vec3 target = RaycastHelper.getTraceTarget(playerIn, range, origin);


        AABB searchAABB = new AABB(origin, target).inflate(0.25, 2, 0.25);
        final Iterator<ClientShip> ships = VSGameUtilsKt.getRenderShipsIntersecting(playerIn.clientLevel, VectorConversionsMCKt.toJOML(searchAABB)).iterator();

        if (ships.hasNext()) {
            ClientShip ship = ships.next();

            Matrix4dc world2Ship = ship.getRenderTransform().getWorldToShip();
            AABBic shAABBi = ship.getShipAABB();

            // Hopefully fixes https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/1341
            // Presumably the AABB is null while the ship is still loading
            // This fix is also applied on the forge version of this class
            if (shAABBi != null) {
                AABB shipAABB = new AABB(shAABBi.minX(), shAABBi.minY(), shAABBi.minZ(), shAABBi.maxX(), shAABBi.maxY(), shAABBi.maxZ());


                origin = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(origin)));
                target = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(target)));

                Quaterniond tempQuat = new Quaterniond();
                if (playerIn.getVehicle() != null && playerIn.getVehicle().getBoundingBox().intersects(shipAABB.inflate(20))) {
                    world2Ship.getNormalizedRotation(tempQuat);
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

    @Redirect(method = "tick", require = 0, at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/RaycastHelper;getTraceTarget(Lnet/minecraft/world/entity/player/Player;DLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 redirectGetTraceTarget(final Player playerIn, final double range, final Vec3 origin) {
        // Without the origin hook (not found on this build), Create's own target.
        return newTarget != null ? newTarget : RaycastHelper.getTraceTarget(playerIn, range, origin);
    }
}
