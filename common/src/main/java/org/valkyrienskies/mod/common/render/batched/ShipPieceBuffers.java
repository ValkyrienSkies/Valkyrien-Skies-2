package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;

public final class ShipPieceBuffers implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final ShipPhysicalPiece piece;
    private final List<Vector4d> planes;
    private final Matrix4d shipToCamera;
    private final IdentityHashMap<RenderType, RenderType> layers = new IdentityHashMap<>();

    private ShipPieceBuffers(ShipPhysicalPiece piece, PoseStack view, Vec3 camera, MultiBufferSource delegate) {
        this.piece = piece;
        this.delegate = delegate;
        var worldToView = new Matrix4d(view.last().pose()).translate(-camera.x, -camera.y, -camera.z);
        shipToCamera = new Matrix4d(worldToView).mul(piece.getShipToWorld());
        var planeTransform = worldToView.invert().transpose();
        planes = piece.getClippingPlanes().stream().map(p -> planeTransform.transform(new Vector4d(p))).toList();
    }

    public static void draw(List<ShipPhysicalPiece> pieces, BlockPos pos, PoseStack view, Vec3 camera,
        MultiBufferSource source, BiConsumer<PoseStack, MultiBufferSource> render) {
        for (var physical : pieces) {
            var buffers = new ShipPieceBuffers(ShipPieceRenderFrames.rendered(physical), view, camera, source);
            render.accept(buffers.at(pos), buffers);
        }
    }

    private PoseStack at(BlockPos pos) {
        var matrix = shipToCamera.translate(pos.getX(), pos.getY(), pos.getZ(), new Matrix4d());
        var stack = new PoseStack();
        stack.last().pose().set(matrix);
        stack.last().normal().set(new Matrix4f(matrix).normal(new Matrix3f()));
        return stack;
    }

    public Vec3 cameraInShip(Vec3 camera) {
        var local = piece.getWorldToShip().transformPosition(new Vector3d(camera.x, camera.y, camera.z));
        return new Vec3(local.x, local.y, local.z);
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        RenderType output = type.mode().connectedPrimitives ? layers.computeIfAbsent(type, ShipPieceBuffers::independent) : type;
        return new SplitShipRenderer.Faces(planes, type.mode(), delegate.getBuffer(output));
    }

    private static RenderType independent(RenderType type) {
        Mode mode = switch (type.mode()) {
            case LINE_STRIP -> Mode.LINES;
            case DEBUG_LINE_STRIP -> Mode.DEBUG_LINES;
            default -> Mode.TRIANGLES;
        };
        return new RenderType("vs_clipped_strip", type.format(), mode, type.bufferSize(), type.affectsCrumbling(),
            false, type::setupRenderState, type::clearRenderState) {};
    }
}
