package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.outliner.Outline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Outline.class)
public abstract class MixinOutline {
    @Shadow
    public abstract void bufferCuboidLine(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vector3d start, Vector3d end, float width, Vector4f color, int lightmap, boolean disableNormals);

    @Shadow
    public abstract void bufferQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f pos0, Vector3f pos1, Vector3f pos2, Vector3f pos3, Vector4f color, float minU, float minV, float maxU, float maxV, int lightmap, Vector3f normal);

    @Inject(method = "bufferCuboidLine(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Vector3d;Lorg/joml/Vector3d;FLorg/joml/Vector4f;IZ)V", at = @At("HEAD"), cancellable = true)
    private void preBufferCuboidLine0(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vector3d start, Vector3d end, float width, Vector4f color, int lightmap, boolean disableNormals, CallbackInfo ci) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final Vector3dc average = new Vector3d((start.x + end.x) / 2.0, (start.y + end.y) / 2.0, (start.z + end.z) / 2.0);
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, average);
            if (ship != null) {
                final ShipTransform transform = ship.getRenderTransform();
                final Vector3dc startTransformed = transform.getShipToWorld().transformPosition(new Vector3d(start.x, start.y, start.z));
                final Vector3dc endTransformed = transform.getShipToWorld().transformPosition(new Vector3d(end.x, end.y, end.z));
                float scaledWidth = (float) (width * transform.getShipToWorldScaling().x());
                bufferCuboidLine(poseStack, consumer, camera, new Vector3d(startTransformed.x(), startTransformed.y(), startTransformed.z()), new Vector3d(endTransformed.x(), endTransformed.y(), endTransformed.z()), scaledWidth, color, lightmap, disableNormals);
                ci.cancel();
            }
        }
    }

    @Inject(method = "bufferCuboid", at = @At("HEAD"), cancellable = true)
    private void preBufferCuboid(PoseStack.Pose pose, VertexConsumer consumer, Vector3f minPos, Vector3f maxPos, Vector4f color, int lightmap, boolean disableNormals, CallbackInfo ci) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final Vector3dc average = new Vector3d((minPos.x() + maxPos.x()) / 2.0, (minPos.y() + maxPos.y()) / 2.0, (minPos.z() + maxPos.z()) / 2.0);
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, average);
            if (ship != null) {
                final ShipTransform transform = ship.getRenderTransform();

                final Vector3d temp = new Vector3d();

                float minX = minPos.x();
                float minY = minPos.y();
                float minZ = minPos.z();
                float maxX = maxPos.x();
                float maxY = maxPos.y();
                float maxZ = maxPos.z();

                final Matrix4dc newPosMatrix = new Matrix4d(pose.pose()).mul(transform.getShipToWorld());// VectorConversionsMCKt.toJOML(pose.pose()).mul(transform.getShipToWorld());

                temp.set(minX, minY, maxZ);
                newPosMatrix.transformPosition(temp);
                double x0 = temp.x();
                double y0 = temp.y();
                double z0 = temp.z();
                //System.out.println("temp is " + temp);

                temp.set(minX, minY, minZ);
                newPosMatrix.transformPosition(temp);
                double x1 = temp.x();
                double y1 = temp.y();
                double z1 = temp.z();

                temp.set(maxX, minY, minZ);
                newPosMatrix.transformPosition(temp);
                double x2 = temp.x();
                double y2 = temp.y();
                double z2 = temp.z();

                temp.set(maxX, minY, maxZ);
                newPosMatrix.transformPosition(temp);
                double x3 = temp.x();
                double y3 = temp.y();
                double z3 = temp.z();

                temp.set(minX, maxY, minZ);
                newPosMatrix.transformPosition(temp);
                double x4 = temp.x();
                double y4 = temp.y();
                double z4 = temp.z();

                temp.set(minX, maxY, maxZ);
                newPosMatrix.transformPosition(temp);
                double x5 = temp.x();
                double y5 = temp.y();
                double z5 = temp.z();

                temp.set(maxX, maxY, maxZ);
                newPosMatrix.transformPosition(temp);
                double x6 = temp.x();
                double y6 = temp.y();
                double z6 = temp.z();

                temp.set(maxX, maxY, minZ);
                newPosMatrix.transformPosition(temp);
                double x7 = temp.x();
                double y7 = temp.y();
                double z7 = temp.z();

                float r = color.x();
                float g = color.y();
                float b = color.z();
                float a = color.w();

                // down

                if (disableNormals) {
                    temp.set(0, 1, 0);
                } else {
                    temp.set(0, -1, 0);
                }
                newPosMatrix.transformDirection(temp).normalize();
                float nx0 = (float) temp.x();
                float ny0 = (float) temp.y();
                float nz0 = (float) temp.z();

                consumer.vertex(x0, y0, z0)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx0, ny0, nz0)
                        .endVertex();

                consumer.vertex(x1, y1, z1)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx0, ny0, nz0)
                        .endVertex();

                consumer.vertex(x2, y2, z2)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx0, ny0, nz0)
                        .endVertex();

                consumer.vertex(x3, y3, z3)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx0, ny0, nz0)
                        .endVertex();

                // up

                temp.set(0, 1, 0);
                newPosMatrix.transformDirection(temp).normalize();
                float nx1 = (float) temp.x();
                float ny1 = (float) temp.y();
                float nz1 = (float) temp.z();

                consumer.vertex(x4, y4, z4)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx1, ny1, nz1)
                        .endVertex();

                consumer.vertex(x5, y5, z5)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx1, ny1, nz1)
                        .endVertex();

                consumer.vertex(x6, y6, z6)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx1, ny1, nz1)
                        .endVertex();

                consumer.vertex(x7, y7, z7)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx1, ny1, nz1)
                        .endVertex();

                // north

                if (disableNormals) {
                    temp.set(0, 1, 0);
                } else {
                    temp.set(0, 0, -1);
                }
                newPosMatrix.transformDirection(temp).normalize();
                float nx2 = (float) temp.x();
                float ny2 = (float) temp.y();
                float nz2 = (float) temp.z();

                consumer.vertex(x7, y7, z7)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx2, ny2, nz2)
                        .endVertex();

                consumer.vertex(x2, y2, z2)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx2, ny2, nz2)
                        .endVertex();

                consumer.vertex(x1, y1, z1)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx2, ny2, nz2)
                        .endVertex();

                consumer.vertex(x4, y4, z4)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx2, ny2, nz2)
                        .endVertex();

                // south

                if (disableNormals) {
                    temp.set(0, 1, 0);
                } else {
                    temp.set(0, 0, 1);
                }
                newPosMatrix.transformDirection(temp).normalize();
                float nx3 = (float) temp.x();
                float ny3 = (float) temp.y();
                float nz3 = (float) temp.z();

                consumer.vertex(x5, y5, z5)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx3, ny3, nz3)
                        .endVertex();

                consumer.vertex(x0, y0, z0)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx3, ny3, nz3)
                        .endVertex();

                consumer.vertex(x3, y3, z3)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx3, ny3, nz3)
                        .endVertex();

                consumer.vertex(x6, y6, z6)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx3, ny3, nz3)
                        .endVertex();

                // west

                if (disableNormals) {
                    temp.set(0, 1, 0);
                } else {
                    temp.set(-1, 0, 0);
                }
                newPosMatrix.transformDirection(temp).normalize();
                float nx4 = (float) temp.x();
                float ny4 = (float) temp.y();
                float nz4 = (float) temp.z();

                consumer.vertex(x4, y4, z4)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx4, ny4, nz4)
                        .endVertex();

                consumer.vertex(x1, y1, z1)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx4, ny4, nz4)
                        .endVertex();

                consumer.vertex(x0, y0, z0)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx4, ny4, nz4)
                        .endVertex();

                consumer.vertex(x5, y5, z5)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx4, ny4, nz4)
                        .endVertex();

                // east

                if (disableNormals) {
                    temp.set(0, 1, 0);
                } else {
                    temp.set(1, 0, 0);
                }
                newPosMatrix.transformDirection(temp).normalize();
                float nx5 = (float) temp.x();
                float ny5 = (float) temp.y();
                float nz5 = (float) temp.z();

                consumer.vertex(x6, y6, z6)
                        .color(r, g, b, a)
                        .uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx5, ny5, nz5)
                        .endVertex();

                consumer.vertex(x3, y3, z3)
                        .color(r, g, b, a)
                        .uv(0, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx5, ny5, nz5)
                        .endVertex();

                consumer.vertex(x2, y2, z2)
                        .color(r, g, b, a)
                        .uv(1, 1)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx5, ny5, nz5)
                        .endVertex();

                consumer.vertex(x7, y7, z7)
                        .color(r, g, b, a)
                        .uv(1, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(lightmap)
                        .normal(nx5, ny5, nz5)
                        .endVertex();

                ci.cancel();
            }
        }
    }

    @Inject(method = "bufferQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;Lorg/joml/Vector3f;Lorg/joml/Vector3f;Lorg/joml/Vector3f;Lorg/joml/Vector4f;FFFFILorg/joml/Vector3f;)V", at = @At("HEAD"), cancellable = true)
    private void preBufferQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f pos0, Vector3f pos1, Vector3f pos2, Vector3f pos3, Vector4f color, float minU, float minV, float maxU, float maxV, int lightmap, Vector3f normal, CallbackInfo ci) {
        final Level level = Minecraft.getInstance().level;
        if (level != null) {
            final Vector3dc average = new Vector3d((pos0.x() + pos1.x() + pos2.x() + pos3.x()) / 4.0, (pos0.y() + pos1.y() + pos2.y() + pos3.y()) / 4.0, (pos0.z() + pos1.z() + pos2.z() + pos3.z()) / 4.0);
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, average);
            if (ship != null) {
                final ShipTransform transform = ship.getRenderTransform();
                final Vector3dc pos0Transformed = transform.getShipToWorld().transformPosition(new Vector3d(pos0.x(), pos0.y(), pos0.z()));
                final Vector3dc pos1Transformed = transform.getShipToWorld().transformPosition(new Vector3d(pos1.x(), pos1.y(), pos1.z()));
                final Vector3dc pos2Transformed = transform.getShipToWorld().transformPosition(new Vector3d(pos2.x(), pos2.y(), pos2.z()));
                final Vector3dc pos3Transformed = transform.getShipToWorld().transformPosition(new Vector3d(pos3.x(), pos3.y(), pos3.z()));
                final Vector3dc normalTransformed = transform.getShipToWorld().transformDirection(new Vector3d(normal.x(), normal.y(), normal.z()));
                bufferQuad(
                        pose,
                        consumer,
                        new Vector3f((float) pos0Transformed.x(), (float) pos0Transformed.y(), (float) pos0Transformed.z()),
                        new Vector3f((float) pos1Transformed.x(), (float) pos1Transformed.y(), (float) pos1Transformed.z()),
                        new Vector3f((float) pos2Transformed.x(), (float) pos2Transformed.y(), (float) pos2Transformed.z()),
                        new Vector3f((float) pos3Transformed.x(), (float) pos3Transformed.y(), (float) pos3Transformed.z()),
                        color,
                        minU,
                        minV,
                        maxU,
                        maxV,
                        lightmap,
                        new Vector3f((float) normalTransformed.x(), (float) normalTransformed.y(), (float) normalTransformed.z())
                );
                ci.cancel();
            }
        }
    }
}
