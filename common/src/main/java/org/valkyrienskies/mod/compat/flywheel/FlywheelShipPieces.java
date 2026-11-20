package org.valkyrienskies.mod.compat.flywheel;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.backend.engine.embed.Environment;
import net.minecraft.client.Minecraft;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.NativeShipPieces;
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.flywheel.ShipVisualEmbedding;

public final class FlywheelShipPieces {
    private FlywheelShipPieces() {}

    public static ShipVisualEmbedding split(Environment environment) {
        return environment instanceof ShipVisualEmbedding attached && !attached.vs$pieces().isEmpty() ? attached : null;
    }

    public static boolean hasPieces() {
        var level = Minecraft.getInstance().level;
        return level != null && !VSGameUtilsKt.getAllShips(level).getSplitShipIds().isEmpty();
    }

    public static void draw(ShipVisualEmbedding embedding, int program, Runnable submit) {
        var level = Minecraft.getInstance().level;
        var pieces = embedding.vs$pieces();
        var manager = VisualizationManager.get(level);
        if (manager == null || !(VSGameUtilsKt.getAllShips(level).getById(pieces.get(0).getShipId()) instanceof ClientShip ship)
            || !ShipRenderViews.shouldRenderShip(ship)) return;
        var origin = manager.renderOrigin();
        var view = ShipRenderViews.getCurrent();
        int active = GL20.glGetUniformLocation(program, "vs_pieceActive");
        int poseUniform = GL20.glGetUniformLocation(program, "vs_piecePose");
        int normalUniform = GL20.glGetUniformLocation(program, "vs_pieceNormal");
        var matrix = new Matrix4d();
        var pose = new Matrix4f();
        var normal = new Matrix3f();
        try (var stack = MemoryStack.stackPush()) {
            var matrixData = stack.mallocFloat(16);
            var normalData = stack.mallocFloat(9);
            GL20.glUniform1i(active, 1);
            for (var physical : pieces) {
                var piece = ShipPieceRenderFrames.rendered(physical);
                if (view != null && !view.getFrustum().isVisible(VectorConversionsMCKt.toMinecraft(piece.getWorldBounds()))) continue;
                embedding.vs$piecePose(piece, origin, matrix, normal);
                pose.set(matrix);
                GL20.glUniformMatrix4fv(poseUniform, false, pose.get(matrixData));
                GL20.glUniformMatrix3fv(normalUniform, false, normal.get(normalData));
                int unit = piece.getClippingPlanes().size() > 1 ? GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) : -1;
                int previous = NativeShipPieces.bindWorldPlanes(program, piece, origin.getX(), origin.getY(), origin.getZ());
                try {
                    if (unit >= 0) GlStateManager._activeTexture(unit);
                    submit.run();
                } finally {
                    NativeShipPieces.restore(previous);
                    if (unit >= 0) GlStateManager._activeTexture(unit);
                }
            }
        } finally {
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_PiecePlaneCount"), 0);
            GL20.glUniform1i(active, 0);
        }
    }
}
