package org.valkyrienskies.mod.mixin.mod_compat.vista;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.mehvahdjukaar.vista.client.ViewFinderController;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ViewFinderController.class)
public class ViewFinderControllerMixin {
    @Shadow
    protected static ViewFinderAccess access;

    @WrapOperation(
        method = "startControlling",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V")
    )
    private static void cameraRotSetup(Camera camera, float yaw, float pitch, Operation<Void> original){
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 pos = access.getCannonGlobalPosition(0.0f);
        if(VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            ClientShip ship = VSClientGameUtils.getClientShip(pos.x, pos.y, pos.z);
            Quaternionf rotation = new Quaternionf().rotationYXZ(-yaw * ((float) Math.PI / 180.0f), pitch * ((float) Math.PI / 180.0f), 0.0f);
            rotation = ship.getRenderTransform().getShipToWorld().getNormalizedRotation(new Quaternionf()).mul(rotation);
            Vector3f eulerDegrees = rotation.getEulerAnglesYXZ(new Vector3f()).mul(180.0f / (float) Math.PI);
            original.call(camera, -eulerDegrees.y, eulerDegrees.x);
        } else original.call(camera, yaw, pitch);
    }

    @WrapOperation(
        method = "setupCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private static void cameraPosToWorld(Camera camera, Vec3 pos, Operation<Void> original, @Share("ship")LocalRef<ClientShip> ship){
        ClientLevel level = Minecraft.getInstance().level;
        if(VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            original.call(camera, VSGameUtilsKt.toWorldCoordinates(level, pos));
            ship.set(VSClientGameUtils.getClientShip(pos.x, pos.y, pos.z));
        } else original.call(camera, pos);
    }

    @WrapOperation(
        method = "setupCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setRotation(FF)V")
    )
    private static void cameraRotComputation(Camera camera, float yaw, float pitch, Operation<Void> original,
        @Share("ship")LocalRef<ClientShip> ship, @Share("eulerDegrees")LocalRef<Vector3f> eulerAngles){
        if (ship.get() != null) {
            Quaternionf rotation = new Quaternionf().rotationYXZ(-yaw * (float) Math.PI / 180.0f, pitch * (float) Math.PI / 180.0f, 0.0f);
            rotation = ship.get().getRenderTransform().getWorldToShip().getNormalizedRotation(new Quaternionf()).mul(rotation);
            eulerAngles.set(rotation.getEulerAnglesYXZ(new Vector3f()).mul(180.0f / (float) Math.PI));
        }
        original.call(camera, yaw, pitch);
    }

    @WrapOperation(
        method = "setupCamera",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getYRot()F", ordinal = 1)
    )
    private static float cameraGetYRot(Camera camera, Operation<Float> original, @Share("eulerDegrees")LocalRef<Vector3f> eulerAngles){
        if(eulerAngles.get() != null) return -eulerAngles.get().y;
        else return original.call(camera);
    }

    @WrapOperation(
        method = "setupCamera",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getXRot()F", ordinal = 1)
    )
    private static float cameraGetXRot(Camera camera, Operation<Float> original, @Share("eulerDegrees")LocalRef<Vector3f> eulerAngles){
        if(eulerAngles.get() != null) return eulerAngles.get().x;
        else return original.call(camera);
    }
}
