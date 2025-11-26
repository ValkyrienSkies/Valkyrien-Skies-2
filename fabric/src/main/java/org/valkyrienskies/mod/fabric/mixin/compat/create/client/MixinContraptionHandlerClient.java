package org.valkyrienskies.mod.fabric.mixin.compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ContraptionHandlerClient.class)
public class MixinContraptionHandlerClient {
    // Fixes raytracing contraptions on ships
    @WrapOperation(method = "rayTraceContraption", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;toLocalVector(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 wrapRayTraceContraptionToLocalVector(
        final AbstractContraptionEntity instance, final Vec3 localVec, final float partialTicks, final Operation<Vec3> toLocalVector
    ) {
        final ClientShip clientShip = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, instance.getAnchorVec().x, instance.getAnchorVec().y, instance.getAnchorVec().z);
        if (clientShip != null) {
            final Vec3 newLocalVec = VectorConversionsMCKt.toMinecraft(clientShip.getRenderTransform().getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(localVec)));
            return toLocalVector.call(instance, newLocalVec, partialTicks);
        }
        return toLocalVector.call(instance, localVec, partialTicks);
    }

    // Fixes raytracing contraptions on ships
    @WrapOperation(method = "rightClickingOnContraptionsGetsHandledLocally", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/contraptions/AbstractContraptionEntity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private static AABB wrapRightClickingOnContraptionsGetsHandledLocallyGetBoundingBox(final AbstractContraptionEntity instance, final Operation<AABB> getBoundingBox) {
        final ClientShip clientShip = VSGameUtilsKt.getShipObjectManagingPos(Minecraft.getInstance().level, instance.getAnchorVec().x, instance.getAnchorVec().y, instance.getAnchorVec().z);
        if (clientShip != null) {
            final AABB original = getBoundingBox.call(instance);
            final AABBdc modified = VectorConversionsMCKt.toJOML(original).transform(clientShip.getRenderTransform().getShipToWorld());
            return VectorConversionsMCKt.toMinecraft(modified);
        }
        return getBoundingBox.call(instance);
    }
}
