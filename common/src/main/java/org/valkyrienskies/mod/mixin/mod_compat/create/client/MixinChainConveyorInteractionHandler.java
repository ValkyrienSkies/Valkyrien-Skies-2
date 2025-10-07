package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
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
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atLowerCornerOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapPos(Vec3i vec3i, Operation<Vec3> original){
        ClientShip ship = VSClientGameUtils.getClientShip(vec3i.getX(), vec3i.getY(), vec3i.getZ());
        if (ship != null) {
            Vector3d shipPos = VectorConversionsMCKt.toJOML(original.call(vec3i));
            ship.getRenderTransform().getShipToWorld().transformPosition(shipPos);
            return VectorConversionsMCKt.toMinecraft(shipPos);
        }
        return original.call(vec3i);
    }

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorShape;intersect(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapIntersect(ChainConveyorShape instance, Vec3 from, Vec3 to, Operation<Vec3> original, @Local(ordinal = 1) BlockPos liftPos){
        ClientShip ship = VSClientGameUtils.getClientShip(liftPos.getX(), liftPos.getY(), liftPos.getZ());
        if(ship != null) {
            Vector3f newFrom = ship.getRenderTransform().getWorldToShip().transformDirection(from.toVector3f());
            Vector3f newTo = ship.getRenderTransform().getWorldToShip().transformDirection(to.toVector3f());
            return original.call(instance, new Vec3(newFrom), new Vec3(newTo));
        }
        return original.call(instance, from, to);
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
