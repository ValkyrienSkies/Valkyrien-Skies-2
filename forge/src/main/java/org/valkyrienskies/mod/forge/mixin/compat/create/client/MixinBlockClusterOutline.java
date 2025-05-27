package org.valkyrienskies.mod.forge.mixin.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Iterator;
import net.createmod.catnip.outliner.BlockClusterOutline;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.BindableTexture;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.forge.mixin.compat.create.accessors.OutlineParamsAccessor;
import org.valkyrienskies.mod.forge.mixinducks.mod_compat.create.CWCluster;

@Mixin(BlockClusterOutline.class)
public abstract class MixinBlockClusterOutline extends Outline {
    @Unique
    private CWCluster cw$cluster = null;

    @Shadow
    @Final
    protected Vector3f originTemp;

    @Shadow
    protected abstract void bufferBlockFace(PoseStack.Pose pose, VertexConsumer consumer, BlockPos pos, Direction face, Vector4f color, int lightmap);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postInit(final Iterable<BlockPos> positions, final CallbackInfo ci) {
        final Iterator<BlockPos> iterator = positions.iterator();
        final BlockPos firstPos = iterator.hasNext() ? iterator.next() : null;
        if (firstPos != null) {
            final Level level = Minecraft.getInstance().level;
            if (level != null && VSGameUtilsKt.getShipManagingPos(level, firstPos) != null) {
                // Only generate cw$cluster if we're on a ship
                cw$cluster = new CWCluster();
                positions.forEach(cw$cluster::include);
            }
        }
    }

    @Inject(method = "renderFaces", at = @At("HEAD"), cancellable = true, remap = false)
    private void preRenderFaces(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, Vector4f color, int lightmap, CallbackInfo ci) {
        if (cw$cluster != null) {
            final BlockPos anchorPos = cw$cluster.anchor;
            if (anchorPos == null) {
                return;
            }
            final Level level = Minecraft.getInstance().level;
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, anchorPos);
            if (ship != null) {
                final BindableTexture faceTexture = ((OutlineParamsAccessor) params).getFaceTexture();
                if (faceTexture == null)
                    return;

                final ShipTransform renderTransform = ship.getRenderTransform();

                ms.pushPose();
                ms.translate(renderTransform.getPositionInWorld().x() - camera.x, renderTransform.getPositionInWorld().y() - camera.y, renderTransform.getPositionInWorld().z() - camera.z);
                ms.scale((float) renderTransform.getShipToWorldScaling().x(), (float) renderTransform.getShipToWorldScaling().y(), (float) renderTransform.getShipToWorldScaling().z());
                ms.mulPose(VectorConversionsMCKt.toFloat(renderTransform.getShipToWorldRotation()));
                ms.translate(
                        cw$cluster.anchor.getX() - renderTransform.getPositionInShip().x(),
                        cw$cluster.anchor.getY() - renderTransform.getPositionInShip().y(),
                        cw$cluster.anchor.getZ() - renderTransform.getPositionInShip().z()
                );

                PoseStack.Pose pose = ms.last();
                RenderType renderType = PonderRenderTypes.outlineTranslucent(faceTexture.getLocation(), true);
                VertexConsumer consumer = buffer.getLateBuffer(renderType);

                cw$cluster.visibleFaces.forEach((face, axisDirection) -> {
                    Direction direction = Direction.get(axisDirection, face.axis);
                    BlockPos pos = face.pos;
                    if (axisDirection == Direction.AxisDirection.POSITIVE)
                        pos = pos.relative(direction.getOpposite());

                    bufferBlockFace(pose, consumer, pos, direction, color, lightmap);
                });

                ms.popPose();
                ci.cancel();
            }
        }
    }

    @Inject(method = "renderEdges", at = @At("HEAD"), cancellable = true, remap = false)
    private void preRenderEdges(PoseStack ms, SuperRenderTypeBuffer buffer, Vec3 camera, float pt, Vector4f color, int lightmap, boolean disableNormals, CallbackInfo ci) {
        if (cw$cluster != null) {
            final BlockPos anchorPos = cw$cluster.anchor;
            if (anchorPos == null) {
                return;
            }
            final Level level = Minecraft.getInstance().level;
            final ClientShip ship = (ClientShip) VSGameUtilsKt.getShipManagingPos(level, anchorPos);
            if (ship != null) {
                float lineWidth = params.getLineWidth();
                if (lineWidth == 0)
                    return;
                if (cw$cluster.isEmpty())
                    return;

                final ShipTransform renderTransform = ship.getRenderTransform();

                ms.pushPose();
                ms.translate(renderTransform.getPositionInWorld().x() - camera.x, renderTransform.getPositionInWorld().y() - camera.y, renderTransform.getPositionInWorld().z() - camera.z);
                ms.scale((float) renderTransform.getShipToWorldScaling().x(), (float) renderTransform.getShipToWorldScaling().y(), (float) renderTransform.getShipToWorldScaling().z());
                ms.mulPose(VectorConversionsMCKt.toFloat(renderTransform.getShipToWorldRotation()));
                ms.translate(
                        cw$cluster.anchor.getX() - renderTransform.getPositionInShip().x(),
                        cw$cluster.anchor.getY() - renderTransform.getPositionInShip().y(),
                        cw$cluster.anchor.getZ() - renderTransform.getPositionInShip().z()
                );

                PoseStack.Pose pose = ms.last();
                VertexConsumer consumer = buffer.getBuffer(PonderRenderTypes.outlineSolid());

                cw$cluster.visibleEdges.forEach(edge -> {
                    BlockPos pos = edge.pos;
                    Vector3f origin = originTemp;
                    origin.set(pos.getX(), pos.getY(), pos.getZ());
                    Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, edge.axis);
                    bufferCuboidLine(pose, consumer, origin, direction, 1, lineWidth, color, lightmap, disableNormals);
                });

                ms.popPose();

                ci.cancel();
            }
        }
    }
}

