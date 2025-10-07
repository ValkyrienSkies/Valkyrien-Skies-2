package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainConveyorInteractionHandler.class)
public abstract class MixinChainConveyorInteractionHandler {
    @Shadow
    public static BlockPos selectedLift;

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapSubtract(Vec3 instance, Vec3 liftVec, Operation<Vec3> original) {
        ClientShip ship = VSClientGameUtils.getClientShip(liftVec.x, liftVec.y, liftVec.z);
        if (ship != null) {
            Vector3d shipInstance = VectorConversionsMCKt.toJOML(instance);
            shipInstance = ship.getTransform().getWorldToShip().transformPosition(shipInstance);
            return original.call(VectorConversionsMCKt.toMinecraft(shipInstance), liftVec);
        } else return original.call(instance, liftVec);
    }

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double wrapDistance(Vec3 instance, Vec3 from, Operation<Double> original) {
        ClientShip ship = VSClientGameUtils.getClientShip(instance.x, instance.y, instance.z);
        if (ship != null) {
            Vector3d shipFrom = VectorConversionsMCKt.toJOML(from);
            shipFrom = ship.getTransform().getWorldToShip().transformPosition(shipFrom);
            return original.call(instance, VectorConversionsMCKt.toMinecraft(shipFrom));
        } else return original.call(instance, from);
    }

    @WrapOperation(
        method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void wrapTranslate(PoseStack instance, double x, double y, double z, Operation<Void> original, PoseStack ms, MultiBufferSource buffer, Vec3 camera){
        ClientShip ship = VSClientGameUtils.getClientShip(selectedLift.getX(), selectedLift.getY(), selectedLift.getZ());
        if(ship != null) {
            Vector3d liftShipPos = ship.getRenderTransform().getShipToWorld().transformPosition(selectedLift.getX(), selectedLift.getY(), selectedLift.getZ(), new Vector3d());
            original.call(instance, liftShipPos.x - camera.x, liftShipPos.y - camera.y, liftShipPos.z - camera.z);
            instance.last().pose().rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        } else original.call(instance, x, y, z);
    }
}
