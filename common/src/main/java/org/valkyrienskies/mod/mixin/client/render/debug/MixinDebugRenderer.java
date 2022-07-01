package org.valkyrienskies.mod.mixin.client.render.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matrices, final MultiBufferSource.BufferSource vertexConsumers,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        final ClientLevel world = Minecraft.getInstance().level;
        final ShipObjectClientWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        if (Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            for (final ShipObjectClient shipObjectClient : shipObjectClientWorld.getShipObjects().values()) {
                final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
                final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();
                final Quaterniondc shipRenderOrientation =
                    shipRenderTransform.getShipCoordinatesToWorldCoordinatesRotation();

                final double renderRadius = .25;
                final AABB shipCenterOfMassBox =
                    new AABB(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                        shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                        shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius)
                        .move(-cameraX, -cameraY, -cameraZ);
                LevelRenderer
                    .renderLineBox(matrices, vertexConsumers.getBuffer(RenderType.lines()), shipCenterOfMassBox,
                        1.0F, 1.0F, 1.0F, 1.0F);

                // Render AABB from Krunch
                final AABBdc shipPhysicsAABBdc = shipObjectClient.getDebugShipPhysicsAABB();
                final AABB shipAABB =
                    new AABB(shipPhysicsAABBdc.minX(), shipPhysicsAABBdc.minY(), shipPhysicsAABBdc.minZ(),
                        shipPhysicsAABBdc.maxX(),
                        shipPhysicsAABBdc.maxY(), shipPhysicsAABBdc.maxZ());
                LevelRenderer
                    .renderLineBox(matrices, vertexConsumers.getBuffer(RenderType.lines()),
                        shipAABB.move(-cameraX, -cameraY, -cameraZ),
                        1.0F, 0.0F, 0.0F, 1.0F);

                // Render the ship's voxel AABB
                final AABBdc shipVoxelAABBdc = shipObjectClient.getShipData().getShipVoxelAABB();
                if (shipVoxelAABBdc != null) {
                    matrices.pushPose();
                    final Vector3dc centerOfAABB = shipVoxelAABBdc.center(new Vector3d());

                    // Offset the AABB by -[centerOfAABB] to fix floating point errors.
                    final AABB shipVoxelAABBAfterOffset =
                        new AABB(
                            shipVoxelAABBdc.minX() - centerOfAABB.x(),
                            shipVoxelAABBdc.minY() - centerOfAABB.y(),
                            shipVoxelAABBdc.minZ() - centerOfAABB.z(),
                            shipVoxelAABBdc.maxX() - centerOfAABB.x(),
                            shipVoxelAABBdc.maxY() - centerOfAABB.y(),
                            shipVoxelAABBdc.maxZ() - centerOfAABB.z()
                        );

                    // Offset the transform of the AABB by [centerOfAABB] to account for [shipVoxelAABBAfterOffset]
                    // being offset by -[centerOfAABB].
                    VSClientGameUtils.transformRenderWithShip(
                        shipRenderTransform, matrices,
                        centerOfAABB.x(), centerOfAABB.y(), centerOfAABB.z(),
                        cameraX, cameraY, cameraZ);

                    LevelRenderer
                        .renderLineBox(matrices, vertexConsumers.getBuffer(RenderType.lines()),
                            shipVoxelAABBAfterOffset, 1.0F, 0.0F, 0.0F, 1.0F);
                    matrices.popPose();
                }
            }
        }
    }
}
