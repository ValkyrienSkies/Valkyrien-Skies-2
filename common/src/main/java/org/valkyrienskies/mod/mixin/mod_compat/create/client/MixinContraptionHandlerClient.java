package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import com.simibubi.create.foundation.utility.Couple;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ContraptionHandlerClient.class)
public abstract class MixinContraptionHandlerClient {

    @Inject(method = "getRayInputs", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/utility/Couple;create(Ljava/lang/Object;Ljava/lang/Object;)Lcom/simibubi/create/foundation/utility/Couple;"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void redirectedOrigin(final LocalPlayer player, final CallbackInfoReturnable<Couple<Vec3>> cir, final Minecraft mc, Vec3 origin, final double reach, Vec3 target) {

        if (mc.hitResult != null) {
            AABB searchAABB = new AABB(origin, target).inflate(0.25, 2, 0.25);
            final Iterator<Ship> ships = VSGameUtilsKt.getShipsIntersecting(player.level, searchAABB).iterator();

            if (ships.hasNext()) {
                Ship ship = ships.next();

                Matrix4d world2Ship = (Matrix4d) ship.getTransform().getWorldToShip();
                AABBic shAABBi = ship.getShipAABB();
                if (shAABBi == null)
                    return;
                AABB shipAABB = new AABB(shAABBi.minX(), shAABBi.minY(), shAABBi.minZ(), shAABBi.maxX(), shAABBi.maxY(), shAABBi.maxZ());


                origin = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(origin)));
                target = VectorConversionsMCKt.toMinecraft(world2Ship.transformPosition(VectorConversionsMCKt.toJOML(target)));

                Quaterniond tempQuat = new Quaterniond();
                if (player.getVehicle() != null && player.getVehicle().getBoundingBox().intersects(shipAABB.inflate(20))) {
                    ship.getTransform().getWorldToShip().getNormalizedRotation(tempQuat);
                    tempQuat.invert();
                    Vector3d offset = VectorConversionsMCKt.toJOML(target.subtract(origin));
                    tempQuat.transform(offset);
                    target = origin.add(VectorConversionsMCKt.toMinecraft(offset));
                }
            }
        }
        cir.setReturnValue(Couple.create(origin, target));
    }
}
