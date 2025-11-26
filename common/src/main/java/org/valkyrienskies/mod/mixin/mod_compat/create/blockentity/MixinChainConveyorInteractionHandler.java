package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import net.minecraft.client.Minecraft;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainConveyorInteractionHandler.class)
public abstract class MixinChainConveyorInteractionHandler {
    @Shadow
    public static BlockPos selectedLift;

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapRelativePos(final Vec3 instance, final Vec3 liftVec, final Operation<Vec3> original) {
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
    private static double wrapDistanceSqr(final Vec3 instance, final Vec3 from, Operation<Double> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(Minecraft.getInstance().level, instance, from, original);
    }

    @WrapOperation(
        method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void wrapOutlineTranslation(PoseStack instance, double x, double y, double z, Operation<Void> original, PoseStack ms, MultiBufferSource buffer, Vec3 camera){
        ClientShip ship = VSClientGameUtils.getClientShip(selectedLift.getX(), selectedLift.getY(), selectedLift.getZ());
        if(ship != null) {
            Vector3d liftShipPos = ship.getRenderTransform().getShipToWorld().transformPosition(selectedLift.getX(), selectedLift.getY(), selectedLift.getZ(), new Vector3d());
            original.call(instance, liftShipPos.x - camera.x, liftShipPos.y - camera.y, liftShipPos.z - camera.z);
            instance.last().pose().rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        } else original.call(instance, x, y, z);
    }
}
