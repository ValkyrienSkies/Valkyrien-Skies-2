package org.valkyrienskies.mod.client.debug;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.bodies.ClientVsBody;
import org.valkyrienskies.core.api.bodies.properties.BodyTransform;
import org.valkyrienskies.core.api.bodies.shape.BodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.BoxBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.CapsuleBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.CompoundBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.SphereBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.VoxelBodyShapeData;
import org.valkyrienskies.core.api.bodies.shape.WheelBodyShapeData;
import org.valkyrienskies.core.api.event.RegisteredListener;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.api.events.PostRenderShipEvent;
import org.valkyrienskies.mod.api.render.ShipRenderView;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

public final class ShipHitboxRenderer {
    private static final Map<ShipRenderView, Vec3> sightEnds = new WeakHashMap<>();
    private static RegisteredListener listener;

    private ShipHitboxRenderer() { }

    public static void setEnabled(final boolean enabled) {
        if (enabled && listener == null) {
            listener = ValkyrienSkies.api().getPostRenderShipEvent().on(ShipHitboxRenderer::renderShip);
        } else if (!enabled && listener != null) {
            listener.unregister();
            listener = null;
            sightEnds.clear();
        }
    }

    private static boolean canSeeThroughBlocks(final ShipRenderView view, final AABBdc bounds) {
        final var player = Minecraft.getInstance().player;
        if (player.isSpectator() || player.isCreative()) return true;
        if (player.level() == view.getLevel() && bounds.containsPoint(VectorConversionsMCKt.toJOML(player.position()))) return true;
        final Vec3 start = view.getCamera().getPosition();
        final Vec3 end = sightEnds.computeIfAbsent(view, ShipHitboxRenderer::sightEnd);
        return bounds.intersectsLineSegment(start.x, start.y, start.z, end.x, end.y, end.z, new Vector2d()) != Intersectiond.OUTSIDE;
    }

    private static Vec3 sightEnd(final ShipRenderView view) {
        final var camera = view.getCamera();
        final var entity = camera.getEntity();
        final Vec3 start, direction;
        if (entity != null && camera == Minecraft.getInstance().gameRenderer.getMainCamera() && entity.level() == view.getLevel()) {
            start = entity.getEyePosition(0.0f);
            direction = entity.getViewVector(0.0f);
        } else {
            start = camera.getPosition();
            final var look = camera.getLookVector();
            direction = new Vec3(look.x(), look.y(), look.z());
        }
        return view.getLevel().clip(new ClipContext(start, start.add(direction.scale(20)), Block.COLLIDER,
            ClipContext.Fluid.NONE, entity != null ? entity : Minecraft.getInstance().player)).getLocation();
    }

    private static void renderShip(final PostRenderShipEvent event) {
        final var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.showOnlyReducedInfo()) return;
        final ClientShip ship = event.getShip();
        final var view = event.getView();
        final var buffers = event.getBuffers();
        final Vec3 camera = view.getCamera().getPosition();
        final boolean xray = canSeeThroughBlocks(view, ship.getRenderAABB());
        final var lineType = xray ? XRAY_LINES : RenderType.LINES;
        final AABBic bounds = ship.getShipAABB();
        final PoseStack worldPose = new PoseStack();
        worldPose.last().pose().set(view.getViewMatrix());
        view.getViewMatrix().normal(worldPose.last().normal());
        final AABB worldBounds = VectorConversionsMCKt.toMinecraft(ship.getRenderAABB()).move(-camera.x, -camera.y, -camera.z);
        if (bounds == null) {
            LevelRenderer.renderLineBox(worldPose, buffers.getBuffer(lineType), worldBounds, 1, 0, 0, 1);
            return;
        }
        LevelRenderer.renderLineBox(worldPose, buffers.getBuffer(RenderType.LINES), worldBounds, 234 / 255f, 0, 217 / 255f, 1);

        final var poses = event.getPoseStack();
        final var origin = event.getOrigin();
        final AABB localBounds = new AABB(bounds.minX() - origin.getX(), bounds.minY() - origin.getY(), bounds.minZ() - origin.getZ(),
            bounds.maxX() - origin.getX(), bounds.maxY() - origin.getY(), bounds.maxZ() - origin.getZ());
        LevelRenderer.renderLineBox(poses, buffers.getBuffer(RenderType.LINES), localBounds, 0.5f, 0, 0, 1);
        final Long bodyId = ship.getBodyId();
        final var body = bodyId == null ? null : VSGameUtilsKt.getShipObjectWorld(view.getLevel()).getAllBodies().getById(bodyId);
        if (body != null) {
            renderBodyShape(poses, buffers.getBuffer(lineType), body.getCollisionShape(), false, 0, true, origin);
        }
        final var transform = ship.getRenderTransform();
        final Vector3d center = new Vector3d(transform.getPositionInModel()).sub(origin.getX(), origin.getY(), origin.getZ());
        LevelRenderer.renderLineBox(poses, buffers.getBuffer(lineType),
            AABB.ofSize(VectorConversionsMCKt.toMinecraft(center), 0.25, 0.25, 0.25), 250 / 255f, 194 / 255f, 19 / 255f, 1);
        if (xray) renderGizmoInsideAABB(poses, buffers.getBuffer(lineType), localBounds, center.x, center.y, center.z, 1, 0.125f);
        final var drag = DragInfoReporter.INSTANCE.getShipDragValues().get(ship.getId());
        final var lift = DragInfoReporter.INSTANCE.getShipLiftValues().get(ship.getId());
        if (drag != null) renderForce(poses, buffers.getBuffer(RenderType.LINES), center,
            transform.getWorldToShip().transformDirection(drag, new Vector3d()), 0.01, 10, 0, 0.5f, 1, 1);
        if (lift != null) renderForce(poses, buffers.getBuffer(RenderType.LINES), center,
            transform.getWorldToShip().transformDirection(lift, new Vector3d()), 0.01, 10, 1, 0.5f, 1, 1);
    }

    public static void renderStandaloneBodies(final ShipRenderView view, final VsiClientShipWorld shipObjectClientWorld,
        final PoseStack matrices, final MultiBufferSource.BufferSource bufferSource) {
        final Vec3 camera = view.getCamera().getPosition();
        final String dimension = VSGameUtilsKt.getDimensionId(view.getLevel());
        final var shipBodyIds = new HashSet<Long>();
        for (final ClientShip ship : shipObjectClientWorld.getLoadedShips()) shipBodyIds.add(ship.getBodyId());
        for (ClientVsBody clientVsBody : shipObjectClientWorld.getAllBodies()) {
            if (!dimension.equals(clientVsBody.getDimension()) || shipBodyIds.contains(clientVsBody.getId())) continue;
            final BodyTransform bodyRenderTransform = clientVsBody.getRenderTransform();

            AABBdc renderAABBd = validAABBWithMinSize(clientVsBody.getRenderAABB(), 0.1);
            matrices.pushPose();

            final boolean xrayEligible = canSeeThroughBlocks(view, renderAABBd);

            AABB renderAABB = VectorConversionsMCKt.toMinecraft(renderAABBd)
                .move(-camera.x, -camera.y, -camera.z);

            // Bounding Box
            LevelRenderer
                .renderLineBox(matrices, bufferSource.getBuffer(RenderType.LINES), renderAABB, 234.0F / 255.0F, 0.0F, 217.0f / 255.0f, 1.0F);

            final Matrix4d bodyRenderMatrix = new Matrix4d()
                .translate(-camera.x, -camera.y, -camera.z)
                .mul(bodyRenderTransform.getToWorld());
            VectorConversionsMCKt.multiply(matrices, bodyRenderMatrix, bodyRenderTransform.getRotation());

            final AABBdc localRenderAABB = renderAABBd.transform(bodyRenderTransform.getToModel(), new AABBd());

            // Body-local bounding box, transformed by the pose above.
            LevelRenderer.renderLineBox(
                matrices, bufferSource.getBuffer(RenderType.LINES),
                VectorConversionsMCKt.toMinecraft(localRenderAABB),
                0.5F, 1.0F, 0.0F, 1.0F);

            final BodyShapeData collisionShape = clientVsBody.getCollisionShape();
            renderBodyShape(
                matrices, bufferSource.getBuffer(xrayEligible ? XRAY_LINES : RenderType.LINES),
                collisionShape, false, 0
            );

            // Render center of mass as a small cube
            Vector3d centerOfMass = bodyRenderTransform.getPositionInModel().get(new Vector3d());
            final double comBoxSize = .25;
            final AABB comBox = AABB.ofSize(VectorConversionsMCKt.toMinecraft(centerOfMass), comBoxSize, comBoxSize, comBoxSize);
            LevelRenderer.renderLineBox(
                matrices, bufferSource.getBuffer(xrayEligible ? XRAY_LINES : RenderType.LINES),
                comBox,
                250.0F / 255.0F, 194.0F / 255.0F, 19.0F / 255.0F, 1.0F
            );
            // Render gizmos (X, Y, Z axes from center of mass)
            if (xrayEligible) {
                renderGizmoInsideAABB(
                    matrices, bufferSource.getBuffer(XRAY_LINES),
                    VectorConversionsMCKt.toMinecraft(localRenderAABB),
                    centerOfMass.x, centerOfMass.y, centerOfMass.z, 1.0F, .125F
                );
            }

            matrices.popPose();
        }
    }

    private static AABBd validAABBWithMinSize(final AABBdc aabb, final double minSize) {
        double minX = Math.min(aabb.minX(), aabb.maxX());
        double minY = Math.min(aabb.minY(), aabb.maxY());
        double minZ = Math.min(aabb.minZ(), aabb.maxZ());
        double maxX = Math.max(aabb.minX(), aabb.maxX());
        double maxY = Math.max(aabb.minY(), aabb.maxY());
        double maxZ = Math.max(aabb.minZ(), aabb.maxZ());

        if (maxX - minX < minSize) {
            final double center = (minX + maxX) * 0.5;
            minX = center - minSize * 0.5;
            maxX = center + minSize * 0.5;
        }
        if (maxY - minY < minSize) {
            final double center = (minY + maxY) * 0.5;
            minY = center - minSize * 0.5;
            maxY = center + minSize * 0.5;
        }
        if (maxZ - minZ < minSize) {
            final double center = (minZ + maxZ) * 0.5;
            minZ = center - minSize * 0.5;
            maxZ = center + minSize * 0.5;
        }

        return new AABBd(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void renderBodyShape(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final BodyShapeData shape,
        final boolean renderShapeBox,
        final int depth
    ) {
        renderBodyShape(poseStack, vertexConsumer, shape, renderShapeBox, depth, false, BlockPos.ZERO);
    }

    private static void renderBodyShape(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final BodyShapeData shape,
        final boolean renderShapeBox,
        final int depth,
        final boolean skipFirstCompoundChild,
        final BlockPos origin
    ) {
        if (shape == null || depth > 8) return;

        if (renderShapeBox) {
            renderAABB(poseStack, vertexConsumer, shape.getAabb(), origin, 1.0F, 0.45F, 0.0F, 1.0F);
        }

        if (shape instanceof BoxBodyShapeData || shape instanceof VoxelBodyShapeData) {
            renderAABB(poseStack, vertexConsumer, shape.getAabb(), origin, 0.0F, 0.85F, 1.0F, 1.0F);
        } else if (shape instanceof CompoundBodyShapeData compound) {
            final List<CompoundBodyShapeData.Child> children = compound.getChildren();
            for (int i = skipFirstCompoundChild ? 1 : 0; i < children.size(); i++) {
                final CompoundBodyShapeData.Child child = children.get(i);
                poseStack.pushPose();
                final Vector3dc position = child.getPosition();
                final Vector3dc offset = child.getCollisionShapeOffset();
                final float scale = (float) child.getCollisionShapeScaling();

                poseStack.translate(position.x() - origin.getX(), position.y() - origin.getY(), position.z() - origin.getZ());
                poseStack.mulPose(child.getRotation().get(new Quaternionf()));
                poseStack.translate(offset.x(), offset.y(), offset.z());
                poseStack.scale(scale, scale, scale);

                renderBodyShape(poseStack, vertexConsumer, child.getShape(), true, depth + 1);
                poseStack.popPose();
            }
        } else {
            poseStack.pushPose();
            poseStack.translate(-origin.getX(), -origin.getY(), -origin.getZ());
            renderRoundShape(poseStack, vertexConsumer, shape);
            poseStack.popPose();
        }
    }

    private static void renderRoundShape(final PoseStack poseStack, final VertexConsumer vertexConsumer, final BodyShapeData shape) {
        if (shape instanceof SphereBodyShapeData) {
            renderSphere(poseStack, vertexConsumer, ((SphereBodyShapeData) shape).getRadius(), 0.0F, 0.85F, 1.0F, 1.0F);
        } else if (shape instanceof CapsuleBodyShapeData) {
            final CapsuleBodyShapeData capsuleShape = (CapsuleBodyShapeData) shape;
            renderCapsule(
                poseStack, vertexConsumer,
                capsuleShape.getRadius(), capsuleShape.getHalfLength(),
                0.0F, 0.85F, 1.0F, 1.0F
            );
        } else if (shape instanceof WheelBodyShapeData) {
            final WheelBodyShapeData wheelShape = (WheelBodyShapeData) shape;
            renderWheel(
                poseStack, vertexConsumer,
                wheelShape.getRadius(), wheelShape.getHalfThickness(),
                0.0F, 0.85F, 1.0F, 1.0F
            );
        }
    }

    private static void renderAABB(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final AABBdc aabb,
        final BlockPos origin,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        if (aabb == null || !aabb.isValid()) return;
        LevelRenderer.renderLineBox(poseStack, vertexConsumer, VectorConversionsMCKt.toMinecraft(aabb)
            .move(-origin.getX(), -origin.getY(), -origin.getZ()), r, g, b, alpha);
    }

    private static void renderSphere(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double radius,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0) return;

        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            final double angle0 = Math.PI * 2.0 * i / segments;
            final double angle1 = Math.PI * 2.0 * (i + 1) / segments;

            final double x0 = Math.cos(angle0) * radius;
            final double y0 = Math.sin(angle0) * radius;
            final double x1 = Math.cos(angle1) * radius;
            final double y1 = Math.sin(angle1) * radius;

            renderLine(poseStack, vertexConsumer, x0, y0, 0.0, x1, y1, 0.0, r, g, b, alpha);
            renderLine(poseStack, vertexConsumer, x0, 0.0, y0, x1, 0.0, y1, r, g, b, alpha);
            renderLine(poseStack, vertexConsumer, 0.0, x0, y0, 0.0, x1, y1, r, g, b, alpha);
        }
    }

    private static void renderCapsule(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double radius,
        final double halfLength,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        if (!Double.isFinite(radius) || !Double.isFinite(halfLength) || radius <= 0.0 || halfLength < 0.0) return;

        renderCircleX(poseStack, vertexConsumer, -halfLength, radius, r, g, b, alpha);
        renderCircleX(poseStack, vertexConsumer, halfLength, radius, r, g, b, alpha);
        renderSphereAt(poseStack, vertexConsumer, -halfLength, 0.0, 0.0, radius, r, g, b, alpha);
        renderSphereAt(poseStack, vertexConsumer, halfLength, 0.0, 0.0, radius, r, g, b, alpha);

        renderLine(poseStack, vertexConsumer, -halfLength, radius, 0.0, halfLength, radius, 0.0, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, -halfLength, -radius, 0.0, halfLength, -radius, 0.0, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, -halfLength, 0.0, radius, halfLength, 0.0, radius, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, -halfLength, 0.0, -radius, halfLength, 0.0, -radius, r, g, b, alpha);
    }

    private static void renderWheel(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double radius,
        final double halfThickness,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        if (!Double.isFinite(radius) || !Double.isFinite(halfThickness) || radius <= 0.0 || halfThickness < 0.0) return;

        renderCircleY(poseStack, vertexConsumer, -halfThickness, radius, r, g, b, alpha);
        renderCircleY(poseStack, vertexConsumer, halfThickness, radius, r, g, b, alpha);

        renderLine(poseStack, vertexConsumer, radius, -halfThickness, 0.0, radius, halfThickness, 0.0, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, -radius, -halfThickness, 0.0, -radius, halfThickness, 0.0, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, 0.0, -halfThickness, radius, 0.0, halfThickness, radius, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, 0.0, -halfThickness, -radius, 0.0, halfThickness, -radius, r, g, b, alpha);
        renderLine(poseStack, vertexConsumer, 0.0, -halfThickness, 0.0, 0.0, halfThickness, 0.0, r, g, b, alpha);
    }

    private static void renderSphereAt(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double x,
        final double y,
        final double z,
        final double radius,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        renderSphere(poseStack, vertexConsumer, radius, r, g, b, alpha);
        poseStack.popPose();
    }

    private static void renderCircleX(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double x,
        final double radius,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            final double angle0 = Math.PI * 2.0 * i / segments;
            final double angle1 = Math.PI * 2.0 * (i + 1) / segments;

            renderLine(
                poseStack, vertexConsumer,
                x, Math.cos(angle0) * radius, Math.sin(angle0) * radius,
                x, Math.cos(angle1) * radius, Math.sin(angle1) * radius,
                r, g, b, alpha
            );
        }
    }

    private static void renderCircleY(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double y,
        final double radius,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        final int segments = 32;
        for (int i = 0; i < segments; i++) {
            final double angle0 = Math.PI * 2.0 * i / segments;
            final double angle1 = Math.PI * 2.0 * (i + 1) / segments;

            renderLine(
                poseStack, vertexConsumer,
                Math.cos(angle0) * radius, y, Math.sin(angle0) * radius,
                Math.cos(angle1) * radius, y, Math.sin(angle1) * radius,
                r, g, b, alpha
            );
        }
    }

    private static void renderLine(
        final PoseStack poseStack,
        final VertexConsumer vertexConsumer,
        final double x0,
        final double y0,
        final double z0,
        final double x1,
        final double y1,
        final double z1,
        final float r,
        final float g,
        final float b,
        final float alpha
    ) {
        final Matrix4f m4 = poseStack.last().pose();
        final Matrix3f m3 = poseStack.last().normal();
        final float dx = (float) (x1 - x0);
        final float dy = (float) (y1 - y0);
        final float dz = (float) (z1 - z0);

        vertexConsumer.vertex(m4, (float) x0, (float) y0, (float) z0)
            .color(r, g, b, alpha)
            .normal(m3, dx, dy, dz).endVertex();
        vertexConsumer.vertex(m4, (float) x1, (float) y1, (float) z1)
            .color(r, g, b, alpha)
            .normal(m3, dx, dy, dz).endVertex();
    }

    private static void renderForce(PoseStack poseStack, VertexConsumer vertexConsumer, Vector3dc pos, Vector3dc force, double scale, double cap, float r, float g, float b, float alpha) {
        Matrix4f m4 = poseStack.last().pose();
        Matrix3f m3 = poseStack.last().normal();

        Vector3d diff = new Vector3d(
            Math.min(Math.max(-cap, force.x() * scale), cap),
            Math.min(Math.max(-cap, force.y() * scale), cap),
            Math.min(Math.max(-cap, force.z() * scale), cap)
        );

        vertexConsumer.vertex(m4, (float) pos.x(), (float) pos.y(), (float) pos.z())
            .color(r, g, b, alpha)
            .normal(m3, (float) diff.x, (float) diff.y, (float) diff.z).endVertex();
        vertexConsumer.vertex(m4, (float) (pos.x() + diff.x), (float) (pos.y() + diff.y), (float) (pos.z() + diff.z))
            .color(1.0F, 1.0F, 1.0F, 0.0F) // Fade out effect for force lines to stand out against AABBs and gizmos
            .normal(m3, (float) diff.x, (float) diff.y, (float) diff.z).endVertex();
    }

    private static void renderGizmoInsideAABB(PoseStack poseStack, VertexConsumer vertexConsumer, AABB aABB, double cx, double cy, double cz, float alpha, float gizmoSize) {
        renderGizmoInsideAABB(poseStack, vertexConsumer, aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ, cx, cy, cz, alpha, gizmoSize);
    }

    private static void renderGizmoInsideAABB(
        PoseStack poseStack,
        VertexConsumer vertexConsumer,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double cx, double cy, double cz,
        float alpha,
        float gizmoSize
    ) {
        Matrix4f m4 = poseStack.last().pose();
        Matrix3f m3 = poseStack.last().normal();

        float mx = (float) minX, my = (float) minY, mz = (float) minZ;
        float Mx = (float) maxX, My = (float) maxY, Mz = (float) maxZ;
        float fx = (float) cx, fy = (float) cy, fz = (float) cz;

        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();
        Vector3f color = new Vector3f();

        BiConsumer<Vector3f, Vector3f> line = (a, b) -> {
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float dz = b.z - a.z;
            vertexConsumer.vertex(m4, a.x, a.y, a.z)
                .color(color.x, color.y, color.z, alpha)
                .normal(m3, dx, dy, dz).endVertex();
            vertexConsumer.vertex(m4, b.x, b.y, b.z)
                .color(color.x, color.y, color.z, alpha)
                .normal(m3, dx, dy, dz).endVertex();
        };

        color.set(1, 0, 0);
        line.accept(p1.set(mx, fy, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(mx, fy - gizmoSize, fz - gizmoSize), p2.set(mx, fy + gizmoSize, fz + gizmoSize));
        line.accept(p1.set(mx, fy - gizmoSize, fz + gizmoSize), p2.set(mx, fy + gizmoSize, fz - gizmoSize));
        float backX = Mx - gizmoSize;
        line.accept(p1.set(backX, fy - gizmoSize, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy + gizmoSize, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy, fz - gizmoSize), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy, fz + gizmoSize), p2.set(Mx, fy, fz));

        color.set(0, 1, 0);
        line.accept(p1.set(fx, my, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx - gizmoSize, my, fz - gizmoSize), p2.set(fx + gizmoSize, my, fz + gizmoSize));
        line.accept(p1.set(fx - gizmoSize, my, fz + gizmoSize), p2.set(fx + gizmoSize, my, fz - gizmoSize));
        float backY = My - gizmoSize;
        line.accept(p1.set(fx - gizmoSize, backY, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx + gizmoSize, backY, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx, backY, fz - gizmoSize), p2.set(fx, My, fz));
        line.accept(p1.set(fx, backY, fz + gizmoSize), p2.set(fx, My, fz));

        color.set(0, 0, 1);
        line.accept(p1.set(fx, fy, mz), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx - gizmoSize, fy - gizmoSize, mz), p2.set(fx + gizmoSize, fy + gizmoSize, mz));
        line.accept(p1.set(fx - gizmoSize, fy + gizmoSize, mz), p2.set(fx + gizmoSize, fy - gizmoSize, mz));
        float backZ = Mz - gizmoSize;
        line.accept(p1.set(fx - gizmoSize, fy, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx + gizmoSize, fy, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx, fy - gizmoSize, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx, fy + gizmoSize, backZ), p2.set(fx, fy, Mz));
    }

    private static final RenderType XRAY_LINES = new RenderStateShard(null, null, null) {
        // RenderStateShard.RENDERTYPE_LINES_SHADER and others are public in Fabric but protected in Forge.
        // Instead of accessor mixins or access transformers we will use a dummy anonymous class.
        // It is only called once and references static methods and fields. This should be safe.
        public static RenderType createXrayLines() {
            return RenderType.create(
                "xray_lines",
                DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,
                256, false, false,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE) // Thinner than RenderType.LINES, though we do not really care.
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false)
            );
        }
    }.createXrayLines();
}
