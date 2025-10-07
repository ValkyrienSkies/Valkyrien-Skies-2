package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainConveyorRidingHandler.class)
public abstract class MixinChainConveyorRindingHandler {
    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity$ConnectionStats;start()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapStart(ConnectionStats instance, Operation<Vec3> original){
        Vec3 origPos = original.call(instance);
        ClientShip ship = VSClientGameUtils.getClientShip(origPos.x, origPos.y, origPos.z);
        if (ship != null) {
            Vector3d newPos = ship.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity$ConnectionStats;end()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapEnd(ConnectionStats instance, Operation<Vec3> original){
        Vec3 origPos = original.call(instance);
        ClientShip ship = VSClientGameUtils.getClientShip(origPos.x, origPos.y, origPos.z);
        if (ship != null) {
            Vector3d newPos = ship.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapBottomCenterOf(Vec3i vec3i, Operation<Vec3> original){
        Vec3 origPos = original.call(vec3i);
        ClientShip ship = VSClientGameUtils.getClientShip(origPos.x, origPos.y, origPos.z);
        if (ship != null) {
            Vector3d newPos = ship.getRenderTransform().getShipToWorld().transformPosition(origPos.x, origPos.y, origPos.z, new Vector3d());
            return VectorConversionsMCKt.toMinecraft(newPos);
        }
        return origPos;
    }
}
