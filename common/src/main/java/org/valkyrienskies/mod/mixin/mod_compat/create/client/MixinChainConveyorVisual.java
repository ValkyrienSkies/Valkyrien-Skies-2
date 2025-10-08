package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.transform.Rotate;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(value = ChainConveyorVisual.class, remap = false)
public abstract class MixinChainConveyorVisual {
    @WrapOperation(
        method = "setupBoxVisual",
        at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;translate(FFF)Ldev/engine_room/flywheel/lib/instance/TransformedInstance;", ordinal = 0)
    )
    private TransformedInstance wrapTranslate(TransformedInstance instance, float x, float y, float z,
        Operation<TransformedInstance> original, ChainConveyorBlockEntity be) {
        TransformedInstance result = original.call(instance, x, y, z);
        ClientShip ship = VSClientGameUtils.getClientShip(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
        if (ship != null) {
            result = result.rotate(ship.getRenderTransform().getWorldToShip().getNormalizedRotation(new Quaternionf()));
        }
        return result;
    }

    @WrapOperation(
        method = "setupBoxVisual",
        at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;rotateYDegrees(F)Ldev/engine_room/flywheel/lib/transform/Rotate;")
    )
    private Rotate wrapRotateY(TransformedInstance instance, float yaw, Operation<Rotate> original, ChainConveyorBlockEntity be) {
        ClientShip ship = VSClientGameUtils.getClientShip(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
        if (ship != null) {
            //Calculate new box yaw from previous yaw vector, transformed and flattened.
            Vector3d heading = new Vector3d(Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            ship.getRenderTransform().getShipToWorld().transformDirection(heading);
            float newYaw = (float) Mth.atan2(heading.x, heading.z) * Mth.RAD_TO_DEG;

            //if the ship is upside down, the yaw should be opposite.
            if(ship.getRenderTransform().getShipToWorld().transformDirection(new Vector3d(0, 1, 0)).y < 0)
                newYaw = (newYaw + 180) % 360;

            return original.call(instance, newYaw);
        }
        else return original.call(instance, yaw);
    }
}
