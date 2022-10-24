package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matricesIgnore, final MultiBufferSource.BufferSource vertexConsumersIgnore,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        // Ignore the matrix/buffer inputs to this, we're really just using this mixin as a place to run our render code
        final PoseStack matrices = new PoseStack();
        final MultiBufferSource.BufferSource bufferSource =
            MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final ClientLevel world = Minecraft.getInstance().level;
        final ShipObjectClientWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        if (Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            for (final ShipObjectClient shipObjectClient : shipObjectClientWorld.getShipObjects().values()) {
                final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
                final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();

                final double renderRadius = .25;
                final AABB shipCenterOfMassBox =
                    new AABB(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                        shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                        shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius)
                        .move(-cameraX, -cameraY, -cameraZ);
                LevelRenderer
                    .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()), shipCenterOfMassBox,
                        250.0F / 255.0F, 194.0F / 255.0F, 19.0F / 255.0F, 1.0F);

                // Render the ship's physics AABB from Krunch
                final AABBdc shipPhysicsAABBdc = shipObjectClient.getDebugShipPhysicsAABB();
                final AABB shipPhysicsAABB = VectorConversionsMCKt.toMinecraft(shipPhysicsAABBdc);
                LevelRenderer
                    .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()),
                        shipPhysicsAABB.move(-cameraX, -cameraY, -cameraZ),
                        1.0F, 0.0F, 0.0F, 1.0F);

                // Render the ship's voxel AABB
                final AABBic shipVoxelAABBic = shipObjectClient.getShipData().getShipVoxelAABB();
                if (shipVoxelAABBic != null) {
                    matrices.pushPose();
                    final Vector3dc centerOfAABB = shipVoxelAABBic.center(new Vector3d());

                    // Offset the AABB by -[centerOfAABB] to fix floating point errors.
                    final AABB shipVoxelAABBAfterOffset =
                        new AABB(
                            shipVoxelAABBic.minX() - centerOfAABB.x(),
                            shipVoxelAABBic.minY() - centerOfAABB.y(),
                            shipVoxelAABBic.minZ() - centerOfAABB.z(),
                            shipVoxelAABBic.maxX() - centerOfAABB.x(),
                            shipVoxelAABBic.maxY() - centerOfAABB.y(),
                            shipVoxelAABBic.maxZ() - centerOfAABB.z()
                        );

                    // Offset the transform of the AABB by [centerOfAABB] to account for [shipVoxelAABBAfterOffset]
                    // being offset by -[centerOfAABB].
                    VSClientGameUtils.transformRenderWithShip(
                        shipRenderTransform, matrices,
                        centerOfAABB.x(), centerOfAABB.y(), centerOfAABB.z(),
                        cameraX, cameraY, cameraZ);

                    LevelRenderer
                        .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()),
                            shipVoxelAABBAfterOffset, 1.0F, 0.0F, 0.0F, 1.0F);
                    matrices.popPose();
                }

                // Render the ship's render AABB
                final AABBdc shipRenderAABBdc = shipObjectClient.getRenderAABB();
                final AABB shipRenderAABB = VectorConversionsMCKt.toMinecraft(shipRenderAABBdc);
                LevelRenderer
                    .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()),
                        shipRenderAABB.move(-cameraX, -cameraY, -cameraZ),
                        234.0F / 255.0F, 0.0F, 217.0f / 255.0f, 1.0F);
            }
        }
        bufferSource.endBatch();
    }
}
