package org.valkyrienskies.mod.mixin.mod_compat.vista;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.mehvahdjukaar.ml_classes.LOD;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LOD.class)
public class LODMixin {
    @WrapOperation(
        method = "<init>(Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 cameraPosition(Camera camera, Operation<Vec3> original, @Local(argsOnly = true) Vec3 objCenter){
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 pos = original.call(camera);
        if (VSGameUtilsKt.isBlockInShipyard(level, objCenter)) {
            pos = VSGameUtilsKt.toShipRenderCoordinates(level, objCenter, pos);
        }
        return pos;
    }

    @WrapOperation(
        method = "<init>(Lnet/minecraft/client/Camera;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getLookVector()Lorg/joml/Vector3f;")
    )
    private static Vector3f cameraDirection(Camera camera, Operation<Vector3f> original, @Local(argsOnly = true) Vec3 objCenter){
        ClientLevel level = Minecraft.getInstance().level;
        Vector3f dir = original.call(camera);
        if (VSGameUtilsKt.isBlockInShipyard(level, objCenter)) {
            dir = VSClientGameUtils.getClientShip(objCenter.x(), objCenter.y(), objCenter.z()).getWorldToShip().transformDirection(dir);
        }
        return dir;
    }
}
