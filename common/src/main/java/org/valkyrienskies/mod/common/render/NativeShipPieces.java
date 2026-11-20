package org.valkyrienskies.mod.common.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector4dc;
import org.joml.Vector4f;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.batched.ShipPieceRenderFrames;
import org.valkyrienskies.mod.common.render.batched.ShipMaterialUniforms;

public final class NativeShipPieces {
    public static final int PLANE_UNIT = 16;
    private static final Matrix4dc WORLD_AXES = new Matrix4d();
    private static int buffer, texture;

    private NativeShipPieces() {}

    public static List<ShipPhysicalPiece> pieces(ClientLevel level, long id) {
        return VSGameUtilsKt.getAllShips(level).getSplitPieces(id);
    }

    public static boolean sectionVisible(List<ShipPhysicalPiece> pieces, AABBdc section,
        AABBd box, Predicate<AABBd> visible) {
        for (var physical : pieces) {
            var piece = ShipPieceRenderFrames.rendered(physical);
            section.transform(piece.getShipToWorld(), box);
            box.intersection(piece.getWorldBounds());
            if (box.isValid() && visible.test(box)) return true;
        }
        return false;
    }

    static Vector4f localPlane(Matrix4dc m, Vector4dc plane, double x, double y, double z, Vector4f result) {
        return result.set(
            plane.x() * m.m00() + plane.y() * m.m01() + plane.z() * m.m02(),
            plane.x() * m.m10() + plane.y() * m.m11() + plane.z() * m.m12(),
            plane.x() * m.m20() + plane.y() * m.m21() + plane.z() * m.m22(),
            plane.w() + plane.x() * x + plane.y() * y + plane.z() * z);
    }

    public static int bind(int program, ShipPhysicalPiece piece, double x, double y, double z) {
        var planes = piece == null ? List.<Vector4dc>of() : piece.getClippingPlanes();
        return bind(program, planes, piece == null ? WORLD_AXES : piece.getShipToWorld(), x, y, z);
    }

    public static int bindWorldPlanes(int program, ShipPhysicalPiece piece, double x, double y, double z) {
        return bind(program, piece.getClippingPlanes(), WORLD_AXES, x, y, z);
    }

    private static int bind(int program, List<? extends Vector4dc> planes, Matrix4dc m, double x, double y, double z) {
        int count = planes.size();
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_PiecePlanes"), PLANE_UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "vs_PiecePlaneCount"), count);
        if (count == 0) return -1;
        var plane = localPlane(m, planes.get(0), x, y, z, new Vector4f());
        GL20.glUniform4f(GL20.glGetUniformLocation(program, "vs_FirstPiecePlane"), plane.x, plane.y, plane.z, plane.w);
        if (count == 1) return -1;
        var data = new float[(count - 1) * 4];
        for (int i = 1; i < count; i++) {
            localPlane(m, planes.get(i), x, y, z, plane);
            int offset = (i - 1) * 4;
            data[offset] = plane.x;
            data[offset + 1] = plane.y;
            data[offset + 2] = plane.z;
            data[offset + 3] = plane.w;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + PLANE_UNIT);
        int previous = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        int previousBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
            texture = GL11.glGenTextures();
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, data, GL15.GL_STREAM_DRAW);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, previousBuffer);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        return previous;
    }

    public static void restore(int previous) {
        ShipMaterialUniforms.restoreTable(previous, PLANE_UNIT);
    }

    public static void release() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (buffer != 0) GL15.glDeleteBuffers(buffer);
        texture = buffer = 0;
    }
}
