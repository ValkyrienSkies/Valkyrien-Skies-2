package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.transform.Rotate;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(value = ChainConveyorVisual.class, remap = false)
public abstract class MixinChainConveyorVisual {
    @Unique
    private ClientShip vs$ship;

    @Inject(
        method = "setupBoxVisual",
        at = @At("HEAD")
    )
    private void getShipPrevBoxVisual(ChainConveyorBlockEntity be, ChainConveyorPackage box, float partialTicks,
        CallbackInfo ci){
        vs$ship = VSClientGameUtils.getClientShip(be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ());
    }

    @WrapOperation(
        method = "setupBoxVisual",
        at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;translate(FFF)Ldev/engine_room/flywheel/lib/instance/TransformedInstance;", ordinal = 0)
    )
    private TransformedInstance wrapTranslateDown(TransformedInstance instance, float x, float y, float z,
        Operation<TransformedInstance> original, ChainConveyorBlockEntity be) {
        //Hanging position should be translated correctly.
        TransformedInstance result = original.call(instance, x, y, z);
        if (vs$ship != null) {
            return result.rotate(vs$ship.getRenderTransform().getWorldToShip().getNormalizedRotation(new Quaternionf()));
        }
        return result;
    }

    @WrapOperation(
        method = "setupBoxVisual",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F", remap = true)
    )
    private float disableClampIfOnShip(float f, float g, float h, Operation<Float> original){
        //Let's disable the dangling clamping on ship, for more visual effect.
        if (vs$ship != null) {
            return original.call(f, -180f, 180f);
        } else return original.call(f, g, h);
    }

    @WrapOperation(
        method = "setupBoxVisual",
        at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;rotateYDegrees(F)Ldev/engine_room/flywheel/lib/transform/Rotate;", ordinal = 0)
    )
    private Rotate wrapRotateYaw(TransformedInstance instance, float yaw, Operation<Rotate> original) {
        if (vs$ship != null) {
            //Calculate new box yaw from previous yaw vector, transformed and flattened.
            final Vector3d heading = new Vector3d(Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            vs$ship.getRenderTransform().getShipToWorld().transformDirection(heading);
            final float yawInShip = (float) Mth.atan2(heading.x, heading.z) * Mth.RAD_TO_DEG;

            //if the ship is about to be turned upside down, make box yaw transition smoother.
            final float offset = (float) vs$ship.getRenderTransform().getShipToWorld().transformDirection(new Vector3d(0, 1, 0)).y;
            final float newYaw = yawInShip + (Mth.clamp(offset, -0.1f, 0.1f) - 0.1f) * -900;

            return original.call(instance, newYaw);
        }
        else return original.call(instance, yaw);
    }
}
