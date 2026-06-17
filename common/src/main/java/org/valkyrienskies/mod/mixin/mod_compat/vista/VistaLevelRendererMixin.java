package org.valkyrienskies.mod.mixin.mod_compat.vista;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer;
import net.mehvahdjukaar.vista.common.view_finder.ViewFinderBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(VistaLevelRenderer.class)
public class VistaLevelRendererMixin {

    @Inject(method = "setupSceneCamera", at = @At(value = "TAIL"))
    private static void postSetupCamera(ViewFinderBlockEntity tile, Camera dummyCamera, float partialTicks, CallbackInfo ci,
        @Local Level level) {
        Vec3 pos = dummyCamera.getPosition();
        if(VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            ClientShip ship = VSClientGameUtils.getClientShip(pos.x, pos.y, pos.z);
            Quaternionf rotation = new Quaternionf().rotationYXZ(-dummyCamera.getYRot() * ((float) Math.PI / 180.0f), dummyCamera.getXRot() * ((float) Math.PI / 180.0f), 0.0f);
            rotation = ship.getRenderTransform().getShipToWorld().getNormalizedRotation(new Quaternionf()).mul(rotation);
            Vector3f eulerAngles = rotation.getEulerAnglesYXZ(new Vector3f()).mul(180.0f / (float) Math.PI);
            Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(level, pos);
            ((IVSCamera)dummyCamera).setPositionVS(worldPos);
            ((IVSCamera)dummyCamera).setRotationVS(-eulerAngles.y, eulerAngles.x, eulerAngles.z);

            dummyCamera.getEntity().setPos(worldPos);
            dummyCamera.getEntity().setXRot(eulerAngles.x);
            dummyCamera.getEntity().setYRot(-eulerAngles.y + 180.0F);
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 0, shift = Shift.BEFORE))
    private static void rollCamera(Minecraft mc, RenderTarget target, Camera camera, float fov, CallbackInfo ci, @Local(name = "arg") PoseStack arg){
        arg.mulPose(Axis.ZP.rotationDegrees(((IVSCamera)camera).getZrot()));
    }
}
