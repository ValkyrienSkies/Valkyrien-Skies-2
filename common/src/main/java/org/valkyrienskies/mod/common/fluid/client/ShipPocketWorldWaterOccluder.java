package org.valkyrienskies.mod.common.fluid.client;

import static org.valkyrienskies.mod.common.fluid.client.ShipExteriorFluidSampler.SURFACE_EPS;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.util.FluidStateManager;

/**
 * Writes opaque depth values for the ship's interior air-pocket *boundary* faces before world
 * translucent rendering, with color writes disabled, so world water (and other translucent chunk
 * geometry) z-fails against those faces and the pocket pixels keep whatever was already in the
 * framebuffer (sky / hull / scene). No shader injection — works with vanilla, Sodium, Embeddium
 * and Iris/Oculus shaderpacks because the only thing we touch is the shared depth buffer and the
 * color-write mask, both of which all chunk-rendering pipelines respect.
 *
 * <p>The mesh is built entirely by {@link #emitWaterSurfaceCaps}: one footprint-shaped polygon per
 * interior cell whose vertical span straddles its own locally sampled exterior water surface,
 * flattened onto that surface — the pocket's cross-section at the waterline. See that method's doc
 * for how the polygon is derived (a true cube ∩ plane intersection, not an approximation).</p>
 *
 * <p>Polygon offset biases our depth slightly toward the camera so water faces at the same world
 * position fail {@code GL_LEQUAL}. Backface culling is disabled so the trick works whether the
 * camera is outside the pocket (front faces visible) or inside it (back faces visible).</p>
 *
 * <p><b>Debug:</b> Set the system property {@code vsk.occluder.debug=true} to render the quads
 * with color writes enabled and a bright magenta tint so you can visually verify the geometry
 * is in the right place.</p>
 */
public final class ShipPocketWorldWaterOccluder {

    private ShipPocketWorldWaterOccluder() {}

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir PocketWaterOccluder");
    private static final long DIAG_INTERVAL_MS = 3000L;
    private static long lastDiagAtMs = 0L;
    private static final boolean DEBUG_VISUALIZE =
        Boolean.parseBoolean(System.getProperty("vsk.occluder.debug", "false"));

    private static final class ShipMesh {
        private final long shipId;
        private VertexBuffer vertexBuffer;
        private int vertexCount;
        private int faceCount;
        private int minX;
        private int minY;
        private int minZ;

        private ShipMesh(final long shipId) {
            this.shipId = shipId;
        }

        private void close() {
            if (vertexBuffer != null) {
                vertexBuffer.close();
                vertexBuffer = null;
            }
            vertexCount = 0;
            faceCount = 0;
        }
    }

    private static final Map<Long, ShipMesh> MESHES = new HashMap<>();
    private static ClientLevel lastLevel = null;

    // How far (in world-space blocks) each cap vertex is nudged toward the camera. Deliberately a
    // real per-vertex world-space displacement along the actual camera direction, not a GL
    // polygon-offset/NDC-depth trick: polygon offset's slope-scale term depends on the polygon's
    // *screen-space* depth gradient, which is derived from view angle and was observed to fail to
    // bias consistently from some viewing directions (e.g. looking up at the cap from underneath)
    // no matter how large the constant "units" term was made. Moving the vertex a fixed distance
    // toward the camera in true 3D space is correct by construction for every view angle — it always
    // shortens the vertex's distance along the view ray, so its NDC depth always decreases.
    private static final double VERTEX_CAMERA_BIAS = 0.02;

    // How far (in world-space blocks) each cap polygon is extruded downward, along -normal, to form
    // a thin "skirt" of side-wall quads around its perimeter. A perfectly flat cap has zero
    // thickness, so viewed edge-on — camera at almost exactly the local water-line height, looking
    // roughly horizontally through it — its screen-space footprint collapses toward a single line,
    // which is inherently prone to rasterization dropout no matter how correct the depth bias is:
    // there just aren't reliably any covered pixels to write depth into. The skirt gives the cap
    // real vertical extent, so there's always some near-vertical surface facing the camera to write
    // depth against, even at a perfectly grazing angle.
    private static final double SKIRT_DEPTH = 0.15;

    /**
     * Whether the depth pre-pass is the occlusion path for the current frame.
     *
     * <p>It is only worth paying for under a shaderpack. Iris replaces the vanilla core shaders
     * wholesale, so the uniform-driven mask in
     * {@link org.valkyrienskies.mod.common.fluid.FluidOcclusionRenderer} never gets a chance to run
     * and the depth buffer is the one thing every pipeline still honours. Without a shaderpack the
     * mask does the job for free and this pre-pass would be pure redundant geometry.</p>
     */
    public static boolean isDepthPrepassActive() {
        return VSGameConfig.CLIENT.getUnderwater().getEnableWaterCulling()
            && LoadedMods.getIris()
            && IrisCompat.isIrisShaderActive();
    }

    public static void clear() {
        for (final ShipMesh m : MESHES.values()) {
            m.close();
        }
        MESHES.clear();
        ShipWaterPocketOverlayGeometry.clear();
        lastLevel = null;
    }

    public static void render(final double cameraX, final double cameraY, final double cameraZ,
        final Matrix4f projectionMatrix, final PoseStack poseStack) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null) return;

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        RenderSystem.assertOnRenderThread();

        // ----- Configure depth-only state -----
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        if (DEBUG_VISUALIZE) {
            RenderSystem.colorMask(true, true, true, true);
        } else {
            RenderSystem.colorMask(false, false, false, false);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Factor deliberately left at 0: the slope-scale term scales with the polygon's
        // screen-space depth gradient, which is derived from its (winding-dependent) front-facing
        // orientation. Backface culling is disabled below so the cap is visible — and must be
        // biased — from both sides (e.g. looking up at it from inside the pocket), and several
        // drivers don't treat the slope term symmetrically for the back-facing case, which showed
        // up as occlusion silently failing when viewed from underneath. A flat "units" bias has no
        // such direction dependency, so it's used alone (bumped up to compensate for losing the
        // slope term's extra push at grazing angles). Sign is negative = depth pulled toward camera
        // so water at the same world position loses GL_LEQUAL.
//        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
//        GL11.glPolygonOffset(0.0f, -16.0f);

        final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        final FluidStateManager.QueryCache fluidQueryCache = new FluidStateManager.QueryCache();

        int shipsWithSnapshot = 0;
        int shipsDrawn = 0;
        int totalQuads = 0;

        try {
            for (final LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
                final long shipId = ship.getId();
                final ShipFluidRenderSnapshot snapshot =
                    ShipFluidRenderSnapshot.get(level, shipId);
                if (snapshot == null) continue;
                shipsWithSnapshot++;

                final ShipWaterPocketOverlayGeometry.Cache cache = ShipWaterPocketOverlayGeometry.get(level, shipId, snapshot);
                if (cache.overlayBoundary == null || cache.overlayBoundary.isEmpty()) continue;

                final ShipTransform xform = (ship instanceof final ClientShip cs)
                    ? cs.getRenderTransform()
                    : ship.getShipTransform();
                final Matrix4dc shipToWorld = xform.getShipToWorld();

                final double m00 = shipToWorld.m00(), m10 = shipToWorld.m10(), m20 = shipToWorld.m20();
                final double m01 = shipToWorld.m01(), m11 = shipToWorld.m11(), m21 = shipToWorld.m21();
                final double m02 = shipToWorld.m02(), m12 = shipToWorld.m12(), m22 = shipToWorld.m22();

                final double tX = m00 * cache.minX + m10 * cache.minY + m20 * cache.minZ + shipToWorld.m30();
                final double tY = m01 * cache.minX + m11 * cache.minY + m21 * cache.minZ + shipToWorld.m31();
                final double tZ = m02 * cache.minX + m12 * cache.minY + m22 * cache.minZ + shipToWorld.m32();

                // Camera position in FULL ship-local coordinates — computed the exact same way
                // VS2's own ship-chunk renderer does (MixinLevelRendererVanilla#renderAllShipChunkLayers:
                // shipTransform.getWorldToShip().transformPosition(camera)) — used below as the
                // transformRenderWithShip pivot instead of cache.minX/Y/Z (a fixed corner of the
                // tracked snapshot volume, possibly tens of blocks from the camera). This ship's
                // local coordinates run into the tens of millions; the double-precision math in
                // transformRenderWithShip is accurate regardless of which local point is used as the
                // pivot in principle, but empirically the world water this cap needs to occlude only
                // lined up reliably once the pivot matched what VS2's own renderer uses.
                final Vector3dc cameraShipSpace = xform.getWorldToShip().transformPosition(new Vector3d(cameraX, cameraY, cameraZ));
                final double pivotDeltaX = cache.minX - cameraShipSpace.x();
                final double pivotDeltaY = cache.minY - cameraShipSpace.y();
                final double pivotDeltaZ = cache.minZ - cameraShipSpace.z();

                final ShipMesh mesh = MESHES.computeIfAbsent(shipId, ShipMesh::new);
                final int quads = rebuildMesh(mesh, level, cache, snapshot, fluidPos, scanPos, fluidQueryCache,
                    m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ,
                    pivotDeltaX, pivotDeltaY, pivotDeltaZ);
                if (mesh.vertexBuffer == null || mesh.vertexCount <= 0) continue;

                poseStack.pushPose();
                try {
                    // Pivot on the camera's own ship-local position (cameraShipSpace above), not
                    // mesh.minX/Y/Z. Mesh vertices were shifted by pivotDelta during rebuildMesh to
                    // stay expressed relative to this same point, so the net world position is
                    // unchanged — only which local point anchors the double-precision composition
                    // inside transformRenderWithShip (T(-cam) * shipToWorld * T(pivot)) differs.
                    VSClientGameUtils.transformRenderWithShip(
                        xform,
                        poseStack,
                        cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z(),
                        cameraX, cameraY, cameraZ
                    );
                    final Matrix4f modelView = new Matrix4f(poseStack.last().pose());
                    mesh.vertexBuffer.bind();
                    mesh.vertexBuffer.drawWithShader(modelView, projectionMatrix, GameRenderer.getPositionColorShader());
                } finally {
                    poseStack.popPose();
                }

                shipsDrawn++;
                totalQuads += quads;
            }
            VertexBuffer.unbind();
        } finally {
            // ----- Restore state -----
//            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
//            GL11.glPolygonOffset(0.0f, 0.0f);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();
            // Note: depthMask, depthFunc, and blend state will be implicitly overridden
            // by the RenderType setups that immediately follow this pass.
        }

        final long now = System.currentTimeMillis();
        if (now - lastDiagAtMs >= DIAG_INTERVAL_MS) {
            lastDiagAtMs = now;
            LOGGER.info("Occluder pass: {} ships with snapshot, {} drawn, {} waterline-clipped quads (debug={})",
                shipsWithSnapshot, shipsDrawn, totalQuads, DEBUG_VISUALIZE);
        }
    }

    /**
     * Rebuilds the ship's occluder mesh from {@link #emitWaterSurfaceCaps} — see that method for
     * how the per-cell cap polygons are derived.
     *
     * <p>Returns the number of GL quads written.</p>
     */
    private static int rebuildMesh(final ShipMesh mesh, final ClientLevel level,
        final ShipWaterPocketOverlayGeometry.Cache cache, final ShipFluidRenderSnapshot snapshot,
        final BlockPos.MutableBlockPos fluidPos, final BlockPos.MutableBlockPos scanPos,
        final FluidStateManager.QueryCache fluidQueryCache,
        final double m00, final double m10, final double m20,
        final double m01, final double m11, final double m21,
        final double m02, final double m12, final double m22,
        final double tX, final double tY, final double tZ,
        final double pivotDeltaX, final double pivotDeltaY, final double pivotDeltaZ) {

        final BufferBuilder bb = new BufferBuilder(4096);
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int dr = DEBUG_VISUALIZE ? 255 : 0;
        final int dg = 0;
        final int db = DEBUG_VISUALIZE ? 255 : 0;
        final int da = 255;

        final int[] quadCount = {0};

        emitWaterSurfaceCaps(bb, level, cache, snapshot, fluidPos, scanPos, fluidQueryCache,
            m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ,
            pivotDeltaX, pivotDeltaY, pivotDeltaZ, dr, dg, db, da, quadCount);

        if (quadCount[0] == 0) {
            bb.discard();
            resetMesh(mesh, cache);
            return 0;
        }

        final BufferBuilder.RenderedBuffer rendered = bb.end();
        if (mesh.vertexBuffer == null) {
            mesh.vertexBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        }
        mesh.vertexBuffer.bind();
        mesh.vertexBuffer.upload(rendered);
        VertexBuffer.unbind();

        mesh.vertexCount = quadCount[0] * 4;
        mesh.faceCount = quadCount[0];
        mesh.minX = cache.minX;
        mesh.minY = cache.minY;
        mesh.minZ = cache.minZ;

        return quadCount[0];
    }

    private static final double BOX_CLIP_EPS = 1.0E-4;

    /** Sutherland–Hodgman clip of a convex (planar) polygon against one axis-aligned plane. */
    private static int clipPolygonAgainstPlane(final double[] px, final double[] py, final double[] pz, final int count,
        final double[] outX, final double[] outY, final double[] outZ, final int axis, final double planeValue,
        final boolean keepGreaterEqual) {
        if (count == 0) return 0;

        int outCount = 0;
        double prevX = px[count - 1];
        double prevY = py[count - 1];
        double prevZ = pz[count - 1];
        double prevCoord = axis == 0 ? prevX : axis == 1 ? prevY : prevZ;
        boolean prevInside = keepGreaterEqual ? prevCoord >= planeValue - BOX_CLIP_EPS : prevCoord <= planeValue + BOX_CLIP_EPS;

        for (int i = 0; i < count; i++) {
            final double curX = px[i];
            final double curY = py[i];
            final double curZ = pz[i];
            final double curCoord = axis == 0 ? curX : axis == 1 ? curY : curZ;
            final boolean curInside = keepGreaterEqual ? curCoord >= planeValue - BOX_CLIP_EPS : curCoord <= planeValue + BOX_CLIP_EPS;

            if (curInside != prevInside) {
                final double denom = curCoord - prevCoord;
                final double t = Math.abs(denom) > 1.0E-12 ? (planeValue - prevCoord) / denom : 0.0;
                outX[outCount] = prevX + t * (curX - prevX);
                outY[outCount] = prevY + t * (curY - prevY);
                outZ[outCount] = prevZ + t * (curZ - prevZ);
                outCount++;
            }
            if (curInside) {
                outX[outCount] = curX;
                outY[outCount] = curY;
                outZ[outCount] = curZ;
                outCount++;
            }

            prevX = curX;
            prevY = curY;
            prevZ = curZ;
            prevCoord = curCoord;
            prevInside = curInside;
        }
        return outCount;
    }

    /**
     * Builds the horizontal "lid" over every submerged interior air pocket at its local water
     * surface — the entirety of this mesh. For every interior cell whose vertical span straddles
     * its own locally sampled exterior water surface, this contributes the <em>true</em> polygon
     * where the local water plane slices through that cell's unit cube — not an approximation.
     * A cube cut by an arbitrary plane
     * produces a triangle, quad, pentagon, or hexagon depending on orientation, so on a
     * multi-axis-rotated ship a single cell's cap is generally not a quad at all, and the union of
     * every straddling cell's cap approximates the pocket's true cross-section (e.g. a tilted square
     * shaft reads as a beveled diamond). The polygon is built by clipping an oversized quad — seeded
     * in the plane itself, using two tangent vectors orthogonal to the plane's local-space normal
     * {@code v = (m01, m11, m21)} — against the cell's six axis-aligned bounding faces with
     * {@link #clipPolygonAgainstPlane}, which is exactly a box ∩ plane intersection. Cells with no
     * nearby exterior fluid sample, or whose span doesn't straddle the surface (fully dry above it
     * or fully submerged below it), are skipped.</p>
     *
     * <p>Every vertex is emitted relative to the camera's own ship-local position (shifted by
     * {@code pivotDeltaX/Y/Z} — see the call site in {@link #render}), not cache.minX/Y/Z, and then
     * nudged {@link #VERTEX_CAMERA_BIAS} toward the origin of that same camera-relative space — i.e.
     * toward the camera itself — so the cap reliably wins the depth test against the real water
     * surface regardless of view angle (see {@link #VERTEX_CAMERA_BIAS} for why this replaced
     * relying on GL polygon offset alone). The polygon is also extruded into a thin skirt (see
     * {@link #SKIRT_DEPTH}) — one wall quad per rim edge — so it keeps real screen-space coverage
     * even viewed perfectly edge-on.</p>
     */
    private static void emitWaterSurfaceCaps(final BufferBuilder bb, final ClientLevel level,
        final ShipWaterPocketOverlayGeometry.Cache cache, final ShipFluidRenderSnapshot snapshot,
        final BlockPos.MutableBlockPos fluidPos, final BlockPos.MutableBlockPos scanPos,
        final FluidStateManager.QueryCache fluidQueryCache,
        final double m00, final double m10, final double m20,
        final double m01, final double m11, final double m21,
        final double m02, final double m12, final double m22,
        final double tX, final double tY, final double tZ,
        final double pivotDeltaX, final double pivotDeltaY, final double pivotDeltaZ,
        final int dr, final int dg, final int db, final int da,
        final int[] quadCount) {
        final double vLenSq = m01 * m01 + m11 * m11 + m21 * m21;
        if (vLenSq < 1.0E-9) return;

        // Local-space plane normal — a unit vector, since it's a row of an orthonormal rotation
        // matrix. Tangent basis: cross it with whichever standard axis it's least aligned with, to
        // keep the cross product well-conditioned regardless of ship orientation.
        final double nx = m01, ny = m11, nz = m21;
        final double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        final double refX, refY, refZ;
        if (ax <= ay && ax <= az) { refX = 1; refY = 0; refZ = 0; }
        else if (ay <= ax && ay <= az) { refX = 0; refY = 1; refZ = 0; }
        else { refX = 0; refY = 0; refZ = 1; }

        double t1x = ny * refZ - nz * refY;
        double t1y = nz * refX - nx * refZ;
        double t1z = nx * refY - ny * refX;
        final double t1Len = Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
        t1x /= t1Len;
        t1y /= t1Len;
        t1z /= t1Len;

        final double t2x = ny * t1z - nz * t1y;
        final double t2y = nz * t1x - nx * t1z;
        final double t2z = nx * t1y - ny * t1x;

        final BitSet interior = snapshot.getInterior();
        final int sizeX = cache.sizeX;
        final int sizeY = cache.sizeY;
        final int sizeZ = cache.sizeZ;
        final int volume = sizeX * sizeY * sizeZ;

        final double[] seedX = new double[4];
        final double[] seedY = new double[4];
        final double[] seedZ = new double[4];
        final double[] scratchX = new double[8];
        final double[] scratchY = new double[8];
        final double[] scratchZ = new double[8];
        final double[] clipX = new double[8];
        final double[] clipY = new double[8];
        final double[] clipZ = new double[8];
        final float[] topOutX = new float[8];
        final float[] topOutY = new float[8];
        final float[] topOutZ = new float[8];
        final float[] botOutX = new float[8];
        final float[] botOutY = new float[8];
        final float[] botOutZ = new float[8];
        final float[] wallX = new float[4];
        final float[] wallY = new float[4];
        final float[] wallZ = new float[4];

        // Large enough to fully contain any cross-section of a unit cube (max half-diagonal
        // sqrt(3)/2 ≈ 0.87), then whittled down to the exact shape by the six box-face clips below.
        final double halfSize = 3.0;

        for (int idx = interior.nextSetBit(0); idx >= 0 && idx < volume; idx = interior.nextSetBit(idx + 1)) {
            final int lx = idx % sizeX;
            final int t = idx / sizeX;
            final int ly = t % sizeY;
            final int lz = t / sizeY;

            final double cellCx = lx + 0.5;
            final double cellCy = ly + 0.5;
            final double cellCz = lz + 0.5;

            // True min/max world-Y over the whole cube, not just its center column: for a rotated
            // cell, X/Z offsets contribute to world-Y too (via m01/m21), so the corner reached by
            // picking the low/high local extent on every axis (per the sign of its coefficient) can
            // sit well outside the center-column's Y range. Using only the center column here missed
            // genuinely straddling cells near the plane's edge, leaving gaps in the cap mosaic.
            final double wyLo = tY
                + (m01 >= 0.0 ? m01 * lx : m01 * (lx + 1.0))
                + (m11 >= 0.0 ? m11 * ly : m11 * (ly + 1.0))
                + (m21 >= 0.0 ? m21 * lz : m21 * (lz + 1.0));
            final double wyHi = tY
                + (m01 >= 0.0 ? m01 * (lx + 1.0) : m01 * lx)
                + (m11 >= 0.0 ? m11 * (ly + 1.0) : m11 * ly)
                + (m21 >= 0.0 ? m21 * (lz + 1.0) : m21 * lz);

            final double sy = ShipExteriorFluidSampler.sampleSurfaceY(level, fluidPos, scanPos, fluidQueryCache,
                cellCx, cellCy, cellCz, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
            if (Double.isNaN(sy) || sy < wyLo - SURFACE_EPS || sy > wyHi + SURFACE_EPS) continue;

            // A point on the cutting plane, nearest the cell center: push the center along the
            // (unit) normal until its local-space dot with the normal matches the plane's constant.
            final double planeC = sy - tY;
            final double centerDot = nx * cellCx + ny * cellCy + nz * cellCz;
            final double offset = (planeC - centerDot) / vLenSq;
            final double p0x = cellCx + offset * nx;
            final double p0y = cellCy + offset * ny;
            final double p0z = cellCz + offset * nz;

            seedX[0] = p0x - halfSize * t1x - halfSize * t2x;
            seedY[0] = p0y - halfSize * t1y - halfSize * t2y;
            seedZ[0] = p0z - halfSize * t1z - halfSize * t2z;
            seedX[1] = p0x + halfSize * t1x - halfSize * t2x;
            seedY[1] = p0y + halfSize * t1y - halfSize * t2y;
            seedZ[1] = p0z + halfSize * t1z - halfSize * t2z;
            seedX[2] = p0x + halfSize * t1x + halfSize * t2x;
            seedY[2] = p0y + halfSize * t1y + halfSize * t2y;
            seedZ[2] = p0z + halfSize * t1z + halfSize * t2z;
            seedX[3] = p0x - halfSize * t1x + halfSize * t2x;
            seedY[3] = p0y - halfSize * t1y + halfSize * t2y;
            seedZ[3] = p0z - halfSize * t1z + halfSize * t2z;

            int count = clipPolygonAgainstPlane(seedX, seedY, seedZ, 4, scratchX, scratchY, scratchZ, 0, lx, true);
            count = clipPolygonAgainstPlane(scratchX, scratchY, scratchZ, count, clipX, clipY, clipZ, 0, lx + 1.0, false);
            count = clipPolygonAgainstPlane(clipX, clipY, clipZ, count, scratchX, scratchY, scratchZ, 1, ly, true);
            count = clipPolygonAgainstPlane(scratchX, scratchY, scratchZ, count, clipX, clipY, clipZ, 1, ly + 1.0, false);
            count = clipPolygonAgainstPlane(clipX, clipY, clipZ, count, scratchX, scratchY, scratchZ, 2, lz, true);
            count = clipPolygonAgainstPlane(scratchX, scratchY, scratchZ, count, clipX, clipY, clipZ, 2, lz + 1.0, false);
            if (count < 3) continue;

            for (int i = 0; i < count; i++) {
                // Shift from cache.minX/Y/Z-relative to camera-ship-space-relative (see render()) —
                // the camera therefore sits exactly at the origin of this coordinate frame, which
                // biasTowardCameraInto relies on.
                final double topX = clipX[i] + pivotDeltaX;
                final double topY = clipY[i] + pivotDeltaY;
                final double topZ = clipZ[i] + pivotDeltaZ;
                biasTowardCameraInto(topOutX, topOutY, topOutZ, i, topX, topY, topZ);
                biasTowardCameraInto(botOutX, botOutY, botOutZ, i,
                    topX - SKIRT_DEPTH * nx, topY - SKIRT_DEPTH * ny, topZ - SKIRT_DEPTH * nz);
            }
            quadCount[0] += emitLocalPolygon(bb, topOutX, topOutY, topOutZ, count, dr, dg, db, da);

            // Skirt: one wall quad per edge of the top rim, dropping straight down (along -normal)
            // to the corresponding bottom-rim vertex — see SKIRT_DEPTH for why.
            for (int i = 0; i < count; i++) {
                final int j = (i + 1) % count;
                wallX[0] = topOutX[i]; wallY[0] = topOutY[i]; wallZ[0] = topOutZ[i];
                wallX[1] = topOutX[j]; wallY[1] = topOutY[j]; wallZ[1] = topOutZ[j];
                wallX[2] = botOutX[j]; wallY[2] = botOutY[j]; wallZ[2] = botOutZ[j];
                wallX[3] = botOutX[i]; wallY[3] = botOutY[i]; wallZ[3] = botOutZ[i];
                quadCount[0] += emitLocalPolygon(bb, wallX, wallY, wallZ, 4, dr, dg, db, da);
            }
        }
    }

    /** Nudges (vx0, vy0, vz0) {@link #VERTEX_CAMERA_BIAS} toward the origin — the camera's own
     * position in this coordinate frame, see the call site — and writes the result into the given
     * output arrays at index {@code i}. */
    private static void biasTowardCameraInto(final float[] outX, final float[] outY, final float[] outZ, final int i,
        final double vx0, final double vy0, final double vz0) {
        double vx = vx0;
        double vy = vy0;
        double vz = vz0;
        final double dx = -vx;
        final double dy = -vy;
        final double dz = -vz;
        final double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > 1.0E-12) {
            final double invDist = VERTEX_CAMERA_BIAS / Math.sqrt(distSq);
            vx += dx * invDist;
            vy += dy * invDist;
            vz += dz * invDist;
        }
        outX[i] = (float) vx;
        outY[i] = (float) vy;
        outZ[i] = (float) vz;
    }

    /**
     * Emits a clipped local-space polygon (3–5 vertices, as produced by
     * {@link ShipWaterPocketLiquidOverlay#clipToSurfaceY}) into the buffer as one or more
     * {@code GL_QUADS}-mode entries. A 4-vertex result is written directly as a single quad;
     * anything else is triangle-fanned with the last vertex of each triangle duplicated to keep
     * every entry a valid quad. Returns the number of quad entries written.
     */
    private static int emitLocalPolygon(final BufferBuilder bb, final float[] x, final float[] y, final float[] z,
        final int count, final int dr, final int dg, final int db, final int da) {
        if (count == 4) {
            bb.vertex(x[0], y[0], z[0]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[1], y[1], z[1]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[2], y[2], z[2]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[3], y[3], z[3]).color(dr, dg, db, da).endVertex();
            return 1;
        }

        int quads = 0;
        for (int i = 1; i + 1 < count; i++) {
            bb.vertex(x[0], y[0], z[0]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[i], y[i], z[i]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[i + 1], y[i + 1], z[i + 1]).color(dr, dg, db, da).endVertex();
            bb.vertex(x[i + 1], y[i + 1], z[i + 1]).color(dr, dg, db, da).endVertex();
            quads++;
        }
        return quads;
    }

    private static void resetMesh(final ShipMesh mesh, final ShipWaterPocketOverlayGeometry.Cache cache) {
        mesh.vertexCount = 0;
        mesh.faceCount = 0;
        mesh.minX = cache.minX;
        mesh.minY = cache.minY;
        mesh.minZ = cache.minZ;
        if (mesh.vertexBuffer != null) {
            mesh.vertexBuffer.close();
            mesh.vertexBuffer = null;
        }
    }
}
