package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matrices, final MultiBufferSource.BufferSource vertexConsumersIgnore,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {
        final MultiBufferSource.BufferSource bufferSource =
            MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final ClientLevel world = Minecraft.getInstance().level;
        final VsiClientShipWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        if (Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
            // Reduced debug info (gamerule) disables ability to see normal hitboxes, so we disable ship ones too
            if (Minecraft.getInstance().showOnlyReducedInfo()) return;

            for (final ClientShip shipObjectClient : shipObjectClientWorld.getLoadedShips()) {
                final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
                final Vector3dc shipRenderPosition = shipRenderTransform.getShipPositionInWorldCoordinates();
                final Vector3dc dragForce = DragInfoReporter.INSTANCE.getShipDragValues().get(shipObjectClient.getId());
                final Vector3dc liftForce =
                    DragInfoReporter.INSTANCE.getShipLiftValues().get(shipObjectClient.getId());

                final double renderRadius = .25;
                final AABB shipCenterOfMassBox =
                    new AABB(shipRenderPosition.x() - renderRadius, shipRenderPosition.y() - renderRadius,
                        shipRenderPosition.z() - renderRadius, shipRenderPosition.x() + renderRadius,
                        shipRenderPosition.y() + renderRadius, shipRenderPosition.z() + renderRadius)
                        .move(-cameraX, -cameraY, -cameraZ);
                LevelRenderer
                    .renderLineBox(matrices, bufferSource.getBuffer(RenderType.lines()), shipCenterOfMassBox,
                        250.0F / 255.0F, 194.0F / 255.0F, 19.0F / 255.0F, 1.0F);

                // Render the ship's voxel AABB
                final AABBic shipVoxelAABBic = shipObjectClient.getShipAABB();
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

                // Render the ship's drag and lift forces as a line
                if (dragForce != null && liftForce != null) {

                    matrices.pushPose();
                    matrices.translate(-cameraX, -cameraY, -cameraZ);
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);

                    int light = Integer.MAX_VALUE;
                    bufferSource.getBuffer(RenderType.debugLineStrip(1)).vertex(matrices.last().pose(), (float) shipRenderPosition.x(), (float) shipRenderPosition.y(), (float) shipRenderPosition.z()).color(0,0,255,255).uv2(light).endVertex();
                    bufferSource.getBuffer(RenderType.debugLineStrip(1)).vertex(matrices.last().pose(), (float) (shipRenderPosition.x() + Mth.clamp(dragForce.x() / 100.0, -10.0, 10.0)), (float) (shipRenderPosition.y() + Mth.clamp(dragForce.y() / 100.0, -10.0, 10.0)), (float) (shipRenderPosition.z() + Mth.clamp(dragForce.z() / 100.0, -10.0, 10.0))).color(0,0,255,255).uv2(light).endVertex();
                    bufferSource.getBuffer(RenderType.debugLineStrip(1)).vertex(matrices.last().pose(), (float) shipRenderPosition.x(), (float) shipRenderPosition.y(), (float) shipRenderPosition.z()).color(0,0,255,255).uv2(light).endVertex();

                    //bufferSource.getBuffer(RenderType.LINES).vertex(matrices.last().pose(), (float) shipRenderPosition.x(), (float) shipRenderPosition.y(), (float) shipRenderPosition.z()).color(0,255,0,255).uv2(light).endVertex();
                    bufferSource.getBuffer(RenderType.debugLineStrip(1)).vertex(matrices.last().pose(), (float) (shipRenderPosition.x() + Mth.clamp(liftForce.x() / 100.0, -10.0, 10.0)), (float) (shipRenderPosition.y() + Mth.clamp(liftForce.y() / 100.0, -10.0, 10.0)), (float) (shipRenderPosition.z() + Mth.clamp(liftForce.z() / 100.0, -10.0, 10.0))).color(0,255,0,255).uv2(light).endVertex();
                    matrices.popPose();
                }
            }
        }
        bufferSource.endBatch();
    }
}
