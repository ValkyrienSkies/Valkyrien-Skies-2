package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import java.util.function.BiConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4dc;
import org.valkyrienskies.core.api.ships.ShipPhysicalPiece;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;

public final class SplitShipRenderer {
    private SplitShipRenderer() {}

    public static boolean split(ClientLevel level, long ship) {
        return level != null && !VSGameUtilsKt.getAllShips(level).getSplitPieces(ship).isEmpty();
    }

    public static void drawOutline(List<ShipPhysicalPiece> pieces, VoxelShape shape, BlockPos pos,
        PoseStack view, VertexConsumer lines, double camX, double camY, double camZ) {
        var pose = view.last();
        for (var physical : pieces) {
            var piece = ShipPieceRenderFrames.rendered(physical);
            shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
                var start = piece.getShipToWorld().transformPosition(new Vector3d(pos.getX() + x0, pos.getY() + y0, pos.getZ() + z0));
                var end = piece.getShipToWorld().transformPosition(new Vector3d(pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1));
                var clipped = RaycastUtilsKt.clipToPiece(new Vec3(start.x, start.y, start.z), new Vec3(end.x, end.y, end.z), piece);
                if (clipped == null) return;
                var a = clipped.getFirst();
                var b = clipped.getSecond();
                var normal = b.subtract(a).normalize();
                if (normal == Vec3.ZERO) return;
                lines.vertex(pose.pose(), (float)(a.x - camX), (float)(a.y - camY), (float)(a.z - camZ))
                    .color(0f, 0f, 0f, .4f).normal(pose.normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
                lines.vertex(pose.pose(), (float)(b.x - camX), (float)(b.y - camY), (float)(b.z - camZ))
                    .color(0f, 0f, 0f, .4f).normal(pose.normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
            });
        }
    }

    public static void drawDamage(List<ShipPhysicalPiece> pieces, BlockPos pos, PoseStack view, Vec3 camera,
        VertexConsumer output, BiConsumer<PoseStack, VertexConsumer> drawModel) {
        var local = new PoseStack();
        for (var physical : pieces) {
            var piece = ShipPieceRenderFrames.rendered(physical);
            var faces = new Faces(piece, output, view.last().pose(), camera.x, camera.y, camera.z);
            faces.cell.set(pos);
            drawModel.accept(local, new SheetedDecalTextureGenerator(faces, local.last().pose(), local.last().normal(), 1f));
        }
    }

    private record
        Corner(Vector3d point, float r, float g, float b, float a, float u, float v, int light, int overlay, Vector3d normal) {
        double distance(Vector4dc p) {
            return point.x * p.x() + point.y * p.y() + point.z * p.z() + p.w();
        }
        Corner between(Corner b, double fraction) {
            float t = (float) fraction, s = 1 - t;
            int lo = Math.round((light & 65535) * s + (b.light & 65535) * t);
            int hi = Math.round((light >>> 16) * s + (b.light >>> 16) * t);
            return new Corner(point.lerp(b.point, fraction, new Vector3d()), r * s + b.r * t, g * s + b.g * t,
                this.b * s + b.b * t, a * s + b.a * t, u * s + b.u * t, v * s + b.v * t, lo | (hi << 16),
                t < 0.5f ? overlay : b.overlay, normal.lerp(b.normal, t, new Vector3d()));
        }
    }

    static final class Faces implements VertexConsumer {
        final BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        final List<Corner> quad = new ArrayList<>(4);
        final ShipPhysicalPiece piece;
        final VertexConsumer buffer;
        final List<? extends Vector4dc> planes;
        final Mode mode;
        boolean odd;
        final Matrix4f view;
        final Vector3d camera;
        Faces(ShipPhysicalPiece piece, VertexConsumer buffer, Matrix4f view, double x, double y, double z) {
            this.mode = Mode.QUADS;
            this.planes = piece.getClippingPlanes();
            this.piece = piece;
            this.buffer = buffer;
            this.view = view;
            this.camera = new Vector3d(x, y, z);
        }
        Faces(List<? extends Vector4dc> planes, Mode mode, VertexConsumer buffer) {
            this.piece = null;
            this.view = null;
            this.camera = null;
            this.buffer = buffer;
            this.mode = mode;
            this.planes = planes;
        }
        double x, y, z;
        float r = 1, g = 1, b = 1, a = 1, u, v, nx, ny, nz;
        int light, overlay;
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) {
            this.r = r / 255f;
            this.g = g / 255f;
            this.b = b / 255f;
            this.a = a / 255f;
            return this;
        }
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }
        public VertexConsumer overlayCoords(int u, int v) {
            overlay = u | (v << 16);
            return this;
        }
        public VertexConsumer uv2(int u, int v) {
            light = u | (v << 16);
            return this;
        }
        public VertexConsumer normal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }
        public void defaultColor(int r, int g, int b, int a) {
            color(r, g, b, a);
        }
        public void unsetDefaultColor() {
            r = g = b = a = 1;
        }
        public void endVertex() {
            var position = piece == null ? new Vector3d(x, y, z) : piece.getShipToWorld().transformPosition(
                new Vector3d(cell.getX() + x, cell.getY() + y, cell.getZ() + z));
            var normal = piece == null ? new Vector3d(nx, ny, nz) : piece.getShipToWorld().transformDirection(new Vector3d(nx, ny, nz)).normalize();
            quad.add(new Corner(position, r, g, b, a, u, v, light, overlay, normal));
            if (quad.size() < mode.primitiveLength)
                return;
            if (quad.size() == 2) {
                var a = quad.get(0);
                var b = quad.get(1);
                boolean visible = true;
                for (var plane : planes) {
                    double da = a.distance(plane), db = b.distance(plane);
                    if (da < 0 && db < 0) { visible = false; break; }
                    if (da < 0) a = a.between(b, da / (da - db));
                    else if (db < 0) b = a.between(b, da / (da - db));
                }
                if (visible) { emit(a); emit(b); }
            } else {
                var face = mode == Mode.TRIANGLE_STRIP && odd
                    ? List.of(quad.get(1), quad.get(0), quad.get(2)) : quad;
                drawFace(face);
            }
            if (mode == Mode.TRIANGLE_FAN) quad.remove(1);
            else if (mode.connectedPrimitives) quad.remove(0);
            else quad.clear();
            odd = !odd;
        }
        private void drawFace(List<Corner> face) {
            boolean crosses = false;
            for (var plane : planes) {
                int inside = 0;
                for (var vertex : face) if (vertex.distance(plane) >= 0) inside++;
                if (inside == 0) return;
                crosses |= inside != face.size();
            }
            if (!crosses) {
                face.forEach(this::emit);
            } else if (mode == Mode.QUADS) {
                clipPolygon(List.of(face.get(0), face.get(1), face.get(2)));
                clipPolygon(List.of(face.get(2), face.get(3), face.get(0)));
            } else {
                clipPolygon(face);
            }
        }
        private void clipPolygon(List<Corner> face) {
            for (var plane : planes) {
                if (face.isEmpty())
                    break;
                var clipped = new ArrayList<Corner>(face.size() + 1);
                var previous = face.get(face.size() - 1);
                double before = previous.distance(plane);
                for (var current : face) {
                    double after = current.distance(plane);
                    if ((after >= 0) != (before >= 0))
                        clipped.add(previous.between(current, before / (before - after)));
                    if (after >= 0)
                        clipped.add(current);
                    before = after;
                    previous = current;
                }
                face = clipped;
            }
            if (mode == Mode.QUADS && face.size() == 4) {
                face.forEach(this::emit);
                return;
            }
            for (int i = 2; i < face.size(); i++) {
                emit(face.get(0));
                emit(face.get(i - 1));
                emit(face.get(i));
                if (mode == Mode.QUADS) emit(face.get(i));
            }
        }
        void emit(Corner c) {
            if (view == null) buffer.vertex(c.point.x, c.point.y, c.point.z);
            else {
                var p = c.point.sub(camera, new Vector3d());
                buffer.vertex(view, (float) p.x, (float) p.y, (float) p.z);
            }
            var n = c.normal;
            buffer.color(c.r, c.g, c.b, c.a)
                .uv(c.u, c.v)
                .overlayCoords(c.overlay)
                .uv2(c.light)
                .normal((float) n.x, (float) n.y, (float) n.z)
                .endVertex();
        }
    }
}
