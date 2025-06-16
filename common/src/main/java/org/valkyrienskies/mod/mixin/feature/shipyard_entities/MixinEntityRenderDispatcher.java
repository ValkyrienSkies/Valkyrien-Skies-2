package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE),
        locals = LocalCapture.CAPTURE_FAILHARD)
    <T extends Entity> void render(
        final T entity, final double x, final double y, final double z, final float rotationYaw,
        final float partialTicks, final PoseStack matrixStack,
        final MultiBufferSource buffer, final int packedLight, final CallbackInfo ci,
        final EntityRenderer<T> entityRenderer
    ) {
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTicks);

        if (shipMountedToData != null) {
            // Remove the earlier applied translation
            matrixStack.popPose();
            matrixStack.pushPose();

            final ShipTransform renderTransform = ((ClientShip) shipMountedToData.getShipMountedTo()).getRenderTransform();

            final Vec3 entityPosition = entity.getPosition(partialTicks);
            final Vector3dc transformed = renderTransform.getShipToWorld().transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());

            final double camX = x - entityPosition.x;
            final double camY = y - entityPosition.y;
            final double camZ = z - entityPosition.z;

            final Vec3 offset = entityRenderer.getRenderOffset(entity, partialTicks);
            final Vector3dc scale = renderTransform.getShipToWorldScaling();

            matrixStack.translate(transformed.x() + camX, transformed.y() + camY, transformed.z() + camZ);
            matrixStack.mulPose(new Quaternionf(renderTransform.getShipToWorldRotation()));
            matrixStack.scale((float) scale.x(), (float) scale.y(), (float) scale.z());
            matrixStack.translate(offset.x, offset.y, offset.z);
        } else {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                // Remove the earlier applied translation
                matrixStack.popPose();
                matrixStack.pushPose();

                VSEntityManager.INSTANCE.getHandler(entity)
                    .applyRenderTransform(ship, entity, entityRenderer, x, y, z,
                        rotationYaw, partialTicks, matrixStack,
                        buffer, packedLight);
            } else if (entity.isPassenger()) {
                final ClientShip vehicleShip =
                    (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(entity.level(),
                        entity.getVehicle().blockPosition());
                // If the entity is a passenger and that vehicle is in ship space
                if (vehicleShip != null) {
                    VSEntityManager.INSTANCE.getHandler(entity.getVehicle())
                        .applyRenderOnMountedEntity(vehicleShip, entity.getVehicle(), entity, partialTicks,
                            matrixStack);
                }
            }
        }
    }

    @ModifyReturnValue(
        method = "shouldRender",
        at = @At("RETURN")
    )
    boolean shouldRender(final boolean returns, final Entity entity, final Frustum frustum,
        final double camX, final double camY, final double camZ) {

        if (!returns) {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getShipObjectManagingPos(entity.level(), entity.blockPosition());
            if (ship != null) {
                AABB aABB = entity.getBoundingBoxForCulling().inflate(0.5);
                if (aABB.hasNaN() || aABB.getSize() == 0.0) {
                    aABB = new AABB(entity.getX() - 2.0, entity.getY() - 2.0,
                        entity.getZ() - 2.0, entity.getX() + 2.0,
                        entity.getY() + 2.0, entity.getZ() + 2.0);
                }
                final AABBd aabb = VectorConversionsMCKt.toJOML(aABB);

                // Get the in world position and do it minus what the aabb already has and then add the offset
                aabb.transform(ship.getRenderTransform().getShipToWorld());
                return frustum.isVisible(VectorConversionsMCKt.toMinecraft(aabb));
            }
        }

        return returns;
    }

}
