package org.valkyrienskies.mod.common.fluid;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.physics.VsiFluidFloodedVoxel;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.compat.flooding.shaders.complementary.ComplementaryFluidCompat;

public final class RetainedFluidRenderer {
    private static final Map<Long, ShipMeshes> SHIPS = new HashMap<>();
    private static final BufferBuilder BUILDER = new BufferBuilder(256 * 1024);
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();
    private static final int[][] NEIGHBORS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static ShaderInstance shader;
    private static ClientLevel lastLevel;
    private static VsiClientShipWorld lastWorld;
    private static ShaderFrame shaderFrame;
    private static boolean renderingShaderFrame;
    private static boolean opaquePass;

    private RetainedFluidRenderer() { }

    public static void setShader(final ShaderInstance value) {
        clear();
        shader = value;
    }

    public static void clear() {
        shaderFrame = null;
        for (final var ship : SHIPS.values()) ship.close();
        SHIPS.clear();
        lastLevel = null;
        lastWorld = null;
    }

    public static void render(final PoseStack poseStack, final VsiClientShipWorld world,
        final double cameraX, final double cameraY, final double cameraZ) {
        final ClientLevel level = Minecraft.getInstance().level;
        if (!VSGameConfig.CLIENT.getRenderFloodedVoxels() || level == null || shader == null) {
            clear();
            return;
        }
        if (!renderingShaderFrame && LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) {
            shaderFrame = new ShaderFrame(new Matrix4f(poseStack.last().pose()), world, cameraX, cameraY, cameraZ);
            return;
        }
        if (level != lastLevel || world != lastWorld) {
            clear(); lastLevel = level; lastWorld = world;
        }
        final var present = new HashSet<Long>();
        final var draws = new ArrayList<Draw>();
        final var frustum = org.valkyrienskies.mod.common.render.batched.ShipRenderFrustum.create(
            poseStack.last().pose(), RenderSystem.getProjectionMatrix(), cameraX, cameraY, cameraZ);
        for (final ClientShip ship : world.getLoadedShips()) {
            final var pair = world.getFluidGameplaySnapshot(ship.getId());
            final var flooding = FloodedFluidClientCache.get(world, ship.getId(), pair);
            if (flooding == null || flooding.getSnapshot().getVoxels().isEmpty()) continue;
            present.add(ship.getId());
            final var topology = FluidTopologyClientCache.get(world, ship.getId(), pair);
            final var meshes = SHIPS.computeIfAbsent(ship.getId(), ignored -> new ShipMeshes());
            meshes.update(level, ship, flooding, topology);
            final var cameraLocal = ship.getRenderTransform().getWorldToShip().transformPosition(cameraX, cameraY, cameraZ, new Vector3d());
            for (final var mesh : meshes.sections.values()) {
                if (mesh.opaque != opaquePass) continue;
                final var bounds = new org.joml.primitives.AABBd(mesh.origin.x, mesh.origin.y, mesh.origin.z,
                    mesh.origin.x + 16, mesh.origin.y + 16, mesh.origin.z + 16)
                    .transform(ship.getRenderTransform().getShipToWorld());
                if (!frustum.isVisible(new AABB(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ))) continue;
                final var center = ship.getRenderTransform().getShipToWorld().transformPosition(
                    mesh.origin.x + 8, mesh.origin.y + 8, mesh.origin.z + 8, new Vector3d());
                draws.add(new Draw(ship, mesh, cameraLocal, center.distanceSquared(cameraX, cameraY, cameraZ)));
            }
        }
        SHIPS.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) return false;
            entry.getValue().close(); return true;
        });
        draws.sort(Comparator.comparingDouble(Draw::distance).reversed());
        final var packShader = LoadedMods.getIris() ? ComplementaryFluidCompat.shader(opaquePass) : null;
        final var drawShader = packShader == null ? shader : packShader;
        final RenderType type = opaquePass ? RenderType.solid() : RenderType.translucent();
        type.setupRenderState();
        RenderSystem.disableCull();
        if (packShader != null) ComplementaryFluidCompat.drawing = true;
        try {
            for (final var draw : draws) {
                final var mesh = draw.mesh;
                if (mesh.buffer == null) continue;
                if (org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin.getVSRenderer()
                    == org.valkyrienskies.mod.compat.VSRenderer.SODIUM) {
                    org.valkyrienskies.mod.compat.sodium.SodiumFluidSprites.markActive(mesh.sprites);
                }
                mesh.sort(draw.cameraLocal);
                poseStack.pushPose();
                try {
                    VSClientGameUtils.transformRenderWithShip(draw.ship.getRenderTransform(), poseStack,
                        mesh.origin.x, mesh.origin.y, mesh.origin.z, cameraX, cameraY, cameraZ);
                    if (drawShader.CHUNK_OFFSET != null) drawShader.CHUNK_OFFSET.set(0.0f, 0.0f, 0.0f);
                    mesh.buffer.bind();
                    mesh.buffer.drawWithShader(poseStack.last().pose(), RenderSystem.getProjectionMatrix(), drawShader);
                } finally { poseStack.popPose(); }
            }
        } finally {
            if (packShader != null) ComplementaryFluidCompat.drawing = false;
            VertexBuffer.unbind();
            RenderSystem.enableCull();
            type.clearRenderState();
        }
    }

    public static boolean isShader(final ShaderInstance value) { return shader == value; }

    public static void renderShaderFrame() { renderShaderFrame(false); }

    public static void renderOpaqueShaderFrame() { renderShaderFrame(true); }

    private static void renderShaderFrame(final boolean opaque) {
        final var frame = shaderFrame;
        shaderFrame = null;
        if (frame == null) return;
        final var pose = new PoseStack();
        pose.mulPoseMatrix(frame.view);
        renderingShaderFrame = true;
        opaquePass = opaque;
        try { render(pose, frame.world, frame.x, frame.y, frame.z); }
        finally { renderingShaderFrame = false; opaquePass = false; shaderFrame = opaque ? frame : null; }
    }

    private record ShaderFrame(Matrix4f view, VsiClientShipWorld world, double x, double y, double z) { }

    private record Draw(ClientShip ship, Mesh mesh, Vector3d cameraLocal, double distance) { }
    record Cell(VsiFluidFloodedVoxel voxel, AABB box, FluidAppearance appearance, int light,
                List<VsiFluidFloodedVoxel> neighbors,
                org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion surface,
                List<FluidSurfaceReconstruction.Face> reconstructed, int solidFaces) {
        Cell(VsiFluidFloodedVoxel voxel, AABB box, FluidAppearance appearance, int light,
            List<VsiFluidFloodedVoxel> neighbors, org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion surface,
            List<FluidSurfaceReconstruction.Face> reconstructed) {
            this(voxel, box, appearance, light, neighbors, surface, reconstructed, 0);
        }
        Cell(VsiFluidFloodedVoxel voxel, AABB box, FluidAppearance appearance, int light,
            List<VsiFluidFloodedVoxel> neighbors, org.valkyrienskies.core.internal.physics.VsiFluidSurfaceRegion surface) {
            this(voxel, box, appearance, light, neighbors, surface, null);
        }
        Cell(VsiFluidFloodedVoxel voxel, AABB box, FluidAppearance appearance, int light, List<VsiFluidFloodedVoxel> neighbors) {
            this(voxel, box, appearance, light, neighbors, null);
        }
    }

    static VsiFluidFloodedVoxel geometry(final VsiFluidFloodedVoxel voxel) {
        return voxel == null ? null : new VsiFluidFloodedVoxel(voxel.getPositionX(), voxel.getPositionY(),
            voxel.getPositionZ(), voxel.getFluidId(), Math.min(255, voxel.getFillAmount()), 0, voxel.getRegions(),
            FluidVoxelGeometry.withoutCurrents(voxel.getSurfaceRegions()));
    }

    static List<VsiFluidFloodedVoxel> surfaceNeighbors(final VsiFluidFloodedVoxel voxel,
        final List<VsiFluidFloodedVoxel> neighbors, final List<FluidSurfaceReconstruction.Face> faces) {
        if (faces == null) return neighbors;
        final var result = new ArrayList<VsiFluidFloodedVoxel>(neighbors.size());
        for (final var other : neighbors) {
            boolean joined = false;
            if (other != null) {
                final int[] delta = {other.getPositionX() - voxel.getPositionX(), other.getPositionY() - voxel.getPositionY(),
                    other.getPositionZ() - voxel.getPositionZ()};
                for (int axis = 0; axis < 3; axis++) if (Math.abs(delta[axis]) == 1) {
                    final int dimension = axis;
                    final double coordinate = delta[axis] > 0 ? 1 : 0;
                    joined = faces.stream().anyMatch(face -> face.internal()
                        && face.points().stream().allMatch(p -> Math.abs(p.get(dimension) - coordinate) < 1e-12));
                }
            }
            result.add(joined ? null : other);
        }
        return result;
    }

    private record SectionKey(BlockPos position, boolean opaque) { }

    private static final class ShipMeshes implements AutoCloseable {
        private final Map<SectionKey, Mesh> sections = new HashMap<>();
        private Object snapshot;
        private Object topologyIdentity;
        private Object surfaceIdentity;
        private Object materialIdentity;
        private Map<Long, List<FluidSurfaceReconstruction.Face>> reconstructed = Map.of();
        private long tick = Long.MIN_VALUE;
        private boolean nativeFormat;
        private final Matrix4d transform = new Matrix4d().zero();

        void update(final ClientLevel level, final ClientShip ship,
            final FloodedFluidClientCache.CachedSnapshot flooding, final FluidTopologyClientCache.CachedSnapshot topology) {
            final boolean nativeMaterial = LoadedMods.getIris() && ComplementaryFluidCompat.active();
            final var currentTransform = ship.getRenderTransform().getShipToWorld();
            final Object domain = topology == null ? null : topology.occlusionIdentity();
            if (nativeFormat == nativeMaterial && snapshot == flooding && topologyIdentity == domain && tick == level.getGameTime()
                && transform.equals(currentTransform)) return;
            if (surfaceIdentity != flooding.occlusionIdentity() || materialIdentity != ClientFluidTypes.identity()) {
                reconstructed = FluidSurfaceReconstruction.build(flooding.getSnapshot().getVoxels(), id -> {
                    final var fluid = ClientFluidTypes.get(id);
                    return fluid == null ? null : fluid.getSource();
                });
                surfaceIdentity = flooding.occlusionIdentity();
                materialIdentity = ClientFluidTypes.identity();
            }
            final Map<SectionKey, List<Cell>> next = new HashMap<>();
            final WorldFluidCoverage coverage = new WorldFluidCoverage(level);
            final Vector3d surface = new Vector3d();
            for (final var voxel : flooding.getSnapshot().getVoxels()) {
                if (voxel.getFillAmount() <= 0 && voxel.getSurfaceRegions().isEmpty()) continue;
                final var shape = geometry(voxel);
                final var key = new BlockPos(Math.floorDiv(voxel.getPositionX(), 16),
                    Math.floorDiv(voxel.getPositionY(), 16), Math.floorDiv(voxel.getPositionZ(), 16));
                if (!voxel.getSurfaceRegions().isEmpty()) {
                    final int solidFaces = FluidSolidFaces.mask(level, voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ());
                    currentTransform.transformPosition(voxel.getPositionX() + 0.5, voxel.getPositionY() + 0.5,
                        voxel.getPositionZ() + 0.5, surface);
                    final BlockPos position = BlockPos.containing(surface.x, surface.y, surface.z);
                    final var neighbors = new ArrayList<VsiFluidFloodedVoxel>(6);
                    for (final var offset : NEIGHBORS) neighbors.add(geometry(flooding.get(
                        voxel.getPositionX() + offset[0], voxel.getPositionY() + offset[1], voxel.getPositionZ() + offset[2])));
                    final var rebuilt = reconstructed.get(FloodedFluidClientCache.pack(
                        voxel.getPositionX(), voxel.getPositionY(), voxel.getPositionZ()));
                    final var surfaceNeighbors = surfaceNeighbors(shape, neighbors, rebuilt);
                    for (final var region : voxel.getSurfaceRegions()) {
                        final var fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(FluidVoxelGeometry.dominantFluid(region, true), true);
                        if (fluid == null || !WorldFluidCoverage.hasProtectedBoundary(voxel, topology, flooding)
                            && coverage.covers(currentTransform, voxel, fluid)) continue;
                        final var appearance = FluidAppearance.resolve(level, position, fluid.defaultFluidState());
                        if (appearance == null) continue;
                        next.computeIfAbsent(new SectionKey(key, nativeMaterial && fluid.defaultFluidState().is(net.minecraft.tags.FluidTags.LAVA)), ignored -> new ArrayList<>()).add(new Cell(shape, null, appearance,
                            appearance.light(level.hasChunkAt(position) ? LevelRenderer.getLightColor(level, position) : LightTexture.FULL_BRIGHT),
                            surfaceNeighbors, FluidVoxelGeometry.withoutCurrent(region), rebuilt, solidFaces));
                    }
                } else if (voxel.getRegions().isEmpty()) {
                    final var fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(voxel.getFluidId(), true);
                    if (fluid == null || !WorldFluidCoverage.hasProtectedBoundary(voxel, topology, flooding)
                        && coverage.covers(currentTransform, voxel, fluid)) continue;
                    currentTransform.transformPosition(voxel.getPositionX() + 0.5,
                        voxel.getPositionY() + Math.min(255, voxel.getFillAmount()) / 255.0,
                        voxel.getPositionZ() + 0.5, surface);
                    final BlockPos position = BlockPos.containing(surface.x, surface.y, surface.z);
                    final var appearance = FluidAppearance.resolve(level, position, fluid.defaultFluidState());
                    if (appearance == null) continue;
                    final var neighbors = new ArrayList<VsiFluidFloodedVoxel>(6);
                    for (final var offset : NEIGHBORS) neighbors.add(geometry(flooding.get(
                        voxel.getPositionX() + offset[0], voxel.getPositionY() + offset[1], voxel.getPositionZ() + offset[2])));
                    next.computeIfAbsent(new SectionKey(key, nativeMaterial && fluid.defaultFluidState().is(net.minecraft.tags.FluidTags.LAVA)), ignored -> new ArrayList<>()).add(new Cell(shape, null, appearance,
                        appearance.light(level.hasChunkAt(position) ? LevelRenderer.getLightColor(level, position) : LightTexture.FULL_BRIGHT), neighbors));
                } else {
                    FluidVoxelGeometry.forEachFlooded(voxel, (id, box) -> {
                        final var fluid = MassDatapackResolver.INSTANCE.getFlowingFluid(id, true);
                        if (fluid == null) return;
                        currentTransform.transformPosition((box.minX + box.maxX) * 0.5, box.maxY,
                            (box.minZ + box.maxZ) * 0.5, surface);
                        final BlockPos position = BlockPos.containing(surface.x, surface.y, surface.z);
                        final var appearance = FluidAppearance.resolve(level, position, fluid.defaultFluidState());
                        if (appearance == null) return;
                        next.computeIfAbsent(new SectionKey(key, nativeMaterial && fluid.defaultFluidState().is(net.minecraft.tags.FluidTags.LAVA)), ignored -> new ArrayList<>()).add(new Cell(shape, box, appearance,
                            appearance.light(level.hasChunkAt(position) ? LevelRenderer.getLightColor(level, position) : LightTexture.FULL_BRIGHT), List.of()));
                    });
                }
            }
            sections.entrySet().removeIf(entry -> {
                if (next.containsKey(entry.getKey())) return false;
                entry.getValue().close(); return true;
            });
            for (final var entry : next.entrySet()) {
                final var key = entry.getKey();
                sections.computeIfAbsent(key, ignored -> new Mesh(key.position.getX() * 16, key.position.getY() * 16, key.position.getZ() * 16, key.opaque))
                    .update(entry.getValue(), flooding);
            }
            snapshot = flooding; topologyIdentity = domain; tick = level.getGameTime(); transform.set(currentTransform);
            nativeFormat = nativeMaterial;
        }

        @Override public void close() {
            for (final var mesh : sections.values()) mesh.close();
            sections.clear();
        }
    }

    static final class Mesh implements AutoCloseable {
        final Vector3d origin;
        private List<Cell> cells = List.of();
        private final java.util.Set<net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites = new HashSet<>();
        private boolean built;
        private boolean packFormat;
        private VertexBuffer buffer;
        private BufferBuilder.SortState sortState;
        private final Vector3d lastCamera = new Vector3d(Double.NaN);

        private final boolean opaque;

        Mesh(final int x, final int y, final int z, final boolean opaque) {
            origin = new Vector3d(x, y, z); this.opaque = opaque;
        }

        void update(final List<Cell> next, final FloodedFluidClientCache.CachedSnapshot flooding) {
            final boolean nativeMaterial = LoadedMods.getIris() && ComplementaryFluidCompat.active();
            if (built && packFormat == nativeMaterial && (buffer == null || !buffer.isInvalid()) && cells.equals(next)) return;
            packFormat = nativeMaterial;
            BUILDER.begin(VertexFormat.Mode.QUADS, packFormat ? DefaultVertexFormat.BLOCK : DefaultVertexFormat.NEW_ENTITY);
            try {
                for (final var cell : next) {
                    final var a = cell.appearance;
                    if (packFormat) {
                        final var fluid = ClientFluidTypes.get(cell.surface == null ? cell.voxel.getFluidId()
                            : FluidVoxelGeometry.dominantFluid(cell.surface, true));
                        ComplementaryFluidCompat.beginFluid(BUILDER, fluid == null ? null : fluid.defaultFluidState(),
                            cell.voxel.getPositionX() & 15, cell.voxel.getPositionY() & 15, cell.voxel.getPositionZ() & 15);
                    }
                    if (cell.surface != null) renderSurface(cell, origin);
                    else if (cell.box == null) FloodedVoxelRenderer.renderExposedFaces(IDENTITY, NORMAL, BUILDER,
                        flooding, cell.voxel, origin, a.red(), a.green(), a.blue(), cell.light, false, a.still(), a.flowing(), a.alpha());
                    else FloodedVoxelRenderer.renderPartialBox(IDENTITY, NORMAL, BUILDER, cell.box, origin,
                        a.red(), a.green(), a.blue(), a.alpha(), cell.light, a.still(), a.flowing());
                }
                if (BUILDER.isCurrentBatchEmpty()) {
                    BUILDER.endOrDiscardIfEmpty(); close(); cells = List.copyOf(next); built = true; return;
                }
                BUILDER.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
                final var nextSort = BUILDER.getSortState();
                final var rendered = BUILDER.end();
                final var replacement = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try { replacement.bind(); replacement.upload(rendered); }
                catch (final RuntimeException failure) { replacement.close(); throw failure; }
                finally { VertexBuffer.unbind(); }
                close(); buffer = replacement; sortState = nextSort; cells = List.copyOf(next);
                for (final var cell : next) { sprites.add(cell.appearance.still()); sprites.add(cell.appearance.flowing()); }
                built = true;
            } finally {
                if (packFormat) ComplementaryFluidCompat.endFluid(BUILDER);
                if (BUILDER.building()) BUILDER.discard();
            }
        }

        void sort(final Vector3d camera) {
            if (buffer == null || lastCamera.equals(camera)) return;
            BUILDER.begin(VertexFormat.Mode.QUADS, packFormat ? DefaultVertexFormat.BLOCK : DefaultVertexFormat.NEW_ENTITY);
            BUILDER.restoreSortState(sortState);
            BUILDER.setQuadSorting(VertexSorting.byDistance((float) (camera.x - origin.x),
                (float) (camera.y - origin.y), (float) (camera.z - origin.z)));
            try { buffer.bind(); buffer.upload(BUILDER.end()); lastCamera.set(camera); }
            finally { VertexBuffer.unbind(); if (BUILDER.building()) BUILDER.discard(); }
        }

        @Override public void close() {
            if (buffer != null) buffer.close();
            buffer = null; sortState = null; cells = List.of(); lastCamera.set(Double.NaN);
            sprites.clear(); built = false;
        }
    }

    private static void renderSurface(final Cell cell, final Vector3d origin) {
        if (cell.reconstructed != null) {
            for (final var face : cell.reconstructed) {
                if (!face.internal()) renderSurfaceFace(cell, origin, face.points(), face.normals(), face.top(), false);
            }
        } else for (final var face : FluidVoxelGeometry.faces(cell.surface)) {
            final var normal = new Vector3d(face.get(1)).sub(face.get(0)).cross(new Vector3d(face.get(2)).sub(face.get(0)));
            final boolean top = normal.lengthSquared() > 1e-24 && normal.normalize()
                .dot(cell.surface.getNormalX(), cell.surface.getNormalY(), cell.surface.getNormalZ()) > 0.99;
            renderSurfaceFace(cell, origin, face, List.of(), top, true);
        }
    }

    private static void renderSurfaceFace(final Cell cell, final Vector3d origin, final List<Vector3d> face,
        final List<Vector3d> normals, final boolean top, final boolean includeSelf) {
        if (FluidSolidFaces.covered(face, cell.solidFaces)) return;
        final var a = cell.appearance;
        final var normal = new Vector3d(face.get(1)).sub(face.get(0))
            .cross(new Vector3d(face.get(2)).sub(face.get(0)));
        if (normal.lengthSquared() < 1e-24) return;
        normal.normalize();
        if (coveredSurfaceFace(cell, face, normal, includeSelf)) return;
        final var sprite = top ? a.still() : a.flowing();
        final double textureX = top ? cell.surface.getNormalX() : normal.x;
        final double textureY = top ? cell.surface.getNormalY() : normal.y;
        final double textureZ = top ? cell.surface.getNormalZ() : normal.z;
        final boolean horizontal = Math.abs(textureY) >= Math.max(Math.abs(textureX), Math.abs(textureZ));
        final double textureScale = top ? 16 : 8;
        for (final var piece : FluidInterfaceGeometry.partition(cell, face, normal, includeSelf)) {
            if (LoadedMods.getIris() && ComplementaryFluidCompat.active())
                ComplementaryFluidCompat.interfaceFace(BUILDER, piece.interfaceFace());
            final var points = piece.points();
            for (int i = 1; i + 1 < points.size(); i++) {
                for (final int index : new int[]{0, i, i + 1, i + 1}) {
                    final var p = points.get(index);
                    final var vertexNormal = normals.isEmpty() ? normal : interpolatedNormal(p, face, normals);
                    final double u = !horizontal && Math.abs(textureX) > Math.abs(textureZ) ? p.z : p.x;
                    final double v = horizontal ? p.z : 1.0 - p.y;
                    BUILDER.vertex(p.x + cell.voxel.getPositionX() - origin.x,
                            p.y + cell.voxel.getPositionY() - origin.y, p.z + cell.voxel.getPositionZ() - origin.z)
                        .color(a.red(), a.green(), a.blue(), a.alpha())
                        .uv(sprite.getU(u * textureScale), sprite.getV(v * textureScale))
                        .overlayCoords(piece.interfaceFace() ? 1 : 0, 0).uv2(cell.light)
                        .normal((float) vertexNormal.x, (float) vertexNormal.y, (float) vertexNormal.z).endVertex();
                }
            }
        }
    }

    private static Vector3d interpolatedNormal(final Vector3d point, final List<Vector3d> face,
        final List<Vector3d> normals) {
        final int index = face.indexOf(point);
        if (index >= 0) return normals.get(index);
        final var delta = new Vector3d(point).sub(face.get(0));
        for (int i = 1; i + 1 < face.size(); i++) {
            final var u = new Vector3d(face.get(i)).sub(face.get(0));
            final var v = new Vector3d(face.get(i + 1)).sub(face.get(0));
            final double uu = u.dot(u), uv = u.dot(v), vv = v.dot(v);
            final double determinant = uu * vv - uv * uv;
            if (determinant <= 1e-24) continue;
            final double b = (vv * delta.dot(u) - uv * delta.dot(v)) / determinant;
            final double c = (uu * delta.dot(v) - uv * delta.dot(u)) / determinant;
            if (b >= -1e-8 && c >= -1e-8 && b + c <= 1.0 + 1e-8) {
                return new Vector3d(normals.get(0)).mul(1.0 - b - c)
                    .fma(b, normals.get(i)).fma(c, normals.get(i + 1)).normalize();
            }
        }
        return normals.get(0);
    }

    private static boolean coveredSurfaceFace(final Cell cell, final List<Vector3d> face, final Vector3d normal,
        final boolean includeSelf) {
        final var candidates = new ArrayList<VsiFluidFloodedVoxel>(cell.neighbors);
        if (includeSelf) candidates.add(cell.voxel);
        final int material = FluidVoxelGeometry.dominantFluid(cell.surface, true);
        for (final var other : candidates) {
            if (other == null) continue;
            for (final var region : other.getSurfaceRegions()) {
                if (!FluidVoxelGeometry.sameMaterial(FluidVoxelGeometry.dominantFluid(region, true), material, true)) continue;
                final var bounds = FluidVoxelGeometry.localBounds(region.getBounds());
                boolean covered = true;
                for (final var point : face) {
                    final double x = point.x + cell.voxel.getPositionX() - other.getPositionX() + normal.x * 1e-7;
                    final double y = point.y + cell.voxel.getPositionY() - other.getPositionY() + normal.y * 1e-7;
                    final double z = point.z + cell.voxel.getPositionZ() - other.getPositionZ() + normal.z * 1e-7;
                    final double projection = region.getNormalX() * x + region.getNormalY() * y + region.getNormalZ() * z;
                    if (x < bounds.minX - 1e-9 || x > bounds.maxX + 1e-9 || y < bounds.minY - 1e-9 || y > bounds.maxY + 1e-9
                        || z < bounds.minZ - 1e-9 || z > bounds.maxZ + 1e-9
                        || projection < region.getLowerProjection() - 1e-9 || projection > region.getUpperProjection() + 1e-9) {
                        covered = false; break;
                    }
                }
                if (covered) return true;
            }
        }
        return false;
    }
}
