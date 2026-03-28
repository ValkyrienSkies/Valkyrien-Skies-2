package org.valkyrienskies.mod.air_pockets.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.primitives.AABBdc;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * Geometry-based liquid overlay for submerged ship windows and openings.
 */
public final class ShipWaterPocketLiquidOverlay {

    private ShipWaterPocketLiquidOverlay() {}

    private static final int MAX_SHIPS = 8;
    private static final int MAX_FLUID_SURFACE_CACHE = 8192;
    private static final int MAX_FLUID_SURFACE_POINT_CACHE = 16384;
    private static final int MAX_RAW_EXTERIOR_FLUID_CACHE = 32768;
    private static final double MAX_OVERLAY_SHIP_DISTANCE_BLOCKS_FALLBACK = 192.0;
    private static final double OVERLAY_SHIP_DISTANCE_MARGIN_BLOCKS = 48.0;
    private static final double OVERLAY_NEAR_VIEW_CULL_DISTANCE_BLOCKS = 24.0;
    private static final double OVERLAY_VIEW_ANGLE_MARGIN_DEGREES = 20.0;

    private static final float OVERLAY_ALPHA = 1.0f;
    private static final float FACE_EPS = 0.0025f;
    private static final float FACE_SAMPLE_OFFSET = 0.15f;
    private static final float OVERLAY_UV_SCALE = 1.0f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double SURFACE_EPS = 1.0E-5;
    private static final double[][] FLUID_SAMPLE_OFFSETS = {
        {0.0, 0.0, 0.0},
        {0.0, -0.35, 0.0},
        {0.35, 0.0, 0.0},
        {-0.35, 0.0, 0.0},
        {0.0, 0.0, 0.35},
        {0.0, 0.0, -0.35},
        {0.35, -0.35, 0.0},
        {-0.35, -0.35, 0.0},
        {0.0, -0.35, 0.35},
        {0.0, -0.35, -0.35},
        {0.35, 0.0, 0.35},
        {0.35, 0.0, -0.35},
        {-0.35, 0.0, 0.35},
        {-0.35, 0.0, -0.35},
    };
    private static final int SHAPE_SUBCELL_RES = 8;
    private static final int SHAPE_SUBCELL_COUNT = SHAPE_SUBCELL_RES * SHAPE_SUBCELL_RES * SHAPE_SUBCELL_RES;
    private static final int SHAPE_FACE_NEG_X = 0;
    private static final int SHAPE_FACE_POS_X = 1;
    private static final int SHAPE_FACE_NEG_Y = 2;
    private static final int SHAPE_FACE_POS_Y = 3;
    private static final int SHAPE_FACE_NEG_Z = 4;
    private static final int SHAPE_FACE_POS_Z = 5;
    private static final int SHAPE_MAX_COMPONENTS = 64;
    private static final byte SHAPE_COMPONENT_SOLID = -1;
    private static final byte SHAPE_COMPONENT_UNASSIGNED = -2;

    private static final float[] CLIP_X0 = new float[6];
    private static final float[] CLIP_Y0 = new float[6];
    private static final float[] CLIP_Z0 = new float[6];
    private static final float[] CLIP_U0 = new float[6];
    private static final float[] CLIP_V0 = new float[6];
    private static final float[] CLIP_X1 = new float[6];
    private static final float[] CLIP_Y1 = new float[6];
    private static final float[] CLIP_Z1 = new float[6];
    private static final float[] CLIP_U1 = new float[6];
    private static final float[] CLIP_V1 = new float[6];

    private static final class ShipCache {
        private final long shipId;
        private long geometryRevision;
        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;
        private BitSet overlaySolids;
        private BitSet fullCellOverlaySolids;
        private BitSet overlayBoundary;

        private ShipCache(final long shipId) {
            this.shipId = shipId;
        }
    }

    private static final class FluidSurfaceSample {
        private final Fluid fluid;
        private final FluidState fluidState;
        private final BlockPos pos;
        private final double surfaceY;

        private FluidSurfaceSample(final Fluid fluid, final FluidState fluidState, final BlockPos pos, final double surfaceY) {
            this.fluid = fluid;
            this.fluidState = fluidState;
            this.pos = pos;
            this.surfaceY = surfaceY;
        }
    }

    private static final class ChunkTrackedFluidState {
        private final long chunkRevision;
        private final FluidState fluidState;

        private ChunkTrackedFluidState(final long chunkRevision, final FluidState fluidState) {
            this.chunkRevision = chunkRevision;
            this.fluidState = fluidState;
        }
    }

    private static final class ChunkTrackedFluidSurface {
        private final long chunkRevision;
        private final FluidSurfaceSample sample;

        private ChunkTrackedFluidSurface(final long chunkRevision, final FluidSurfaceSample sample) {
            this.chunkRevision = chunkRevision;
            this.sample = sample;
        }
    }

    private static final class OverlayFaceSample {
        private final TextureAtlasSprite sprite;
        private final float r;
        private final float g;
        private final float b;
        private final double surfaceY;

        private OverlayFaceSample(final TextureAtlasSprite sprite, final float r, final float g, final float b, final double surfaceY) {
            this.sprite = sprite;
            this.r = r;
            this.g = g;
            this.b = b;
            this.surfaceY = surfaceY;
        }
    }

    private static final class OverlayShapeTemplate {
        private final long[] occupancyMask;
        private final byte[] componentBySubcell;
        private final long[] faceComponentMask;
        private final boolean hasOpenVolume;
        private final boolean fullSolid;

        private OverlayShapeTemplate(final long[] occupancyMask, final byte[] componentBySubcell,
            final long[] faceComponentMask, final boolean hasOpenVolume, final boolean fullSolid) {
            this.occupancyMask = occupancyMask;
            this.componentBySubcell = componentBySubcell;
            this.faceComponentMask = faceComponentMask;
            this.hasOpenVolume = hasOpenVolume;
            this.fullSolid = fullSolid;
        }
    }

    private static final Map<Long, ShipCache> SHIP_CACHE = new HashMap<>();
    private static final Map<BlockState, OverlayShapeTemplate> SHAPE_TEMPLATE_CACHE = new HashMap<>();
    private static final FluidState RAW_EXTERIOR_FLUID_MISS = net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
    private static final FluidSurfaceSample FLUID_SURFACE_MISS = new FluidSurfaceSample(
        net.minecraft.world.level.material.Fluids.EMPTY,
        net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState(),
        BlockPos.ZERO,
        Double.NaN
    );
    private static net.minecraft.client.multiplayer.ClientLevel lastSurfaceCacheLevel = null;
    private static final Long2LongOpenHashMap EXTERIOR_FLUID_CHUNK_REVISIONS = new Long2LongOpenHashMap();
    private static final Long2ObjectOpenHashMap<ChunkTrackedFluidSurface> FLUID_SURFACE_CACHE = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<ChunkTrackedFluidSurface> FLUID_SURFACE_POINT_CACHE =
        new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<ChunkTrackedFluidState> RAW_EXTERIOR_FLUID_CACHE =
        new Long2ObjectOpenHashMap<>();

    public static void clear() {
        SHIP_CACHE.clear();
        SHAPE_TEMPLATE_CACHE.clear();
        lastSurfaceCacheLevel = null;
        clearExteriorFluidCaches();
    }

    public static void invalidateExteriorFluidChunk(final @Nullable net.minecraft.client.multiplayer.ClientLevel level, final int chunkX,
        final int chunkZ) {
        if (level == null) return;
        if (lastSurfaceCacheLevel != null && lastSurfaceCacheLevel != level) {
            clearExteriorFluidCaches();
        }
        lastSurfaceCacheLevel = level;
        invalidateExteriorFluidChunk(chunkX, chunkZ);
    }

    public static void invalidateExteriorFluidChunkForTests(final int chunkX, final int chunkZ) {
        invalidateExteriorFluidChunk(chunkX, chunkZ);
    }

    public static long getExteriorFluidChunkRevisionForTests(final int chunkX, final int chunkZ) {
        return currentExteriorFluidChunkRevision(exteriorFluidChunkKeyFromChunk(chunkX, chunkZ));
    }

    private static void clearExteriorFluidCaches() {
        EXTERIOR_FLUID_CHUNK_REVISIONS.clear();
        FLUID_SURFACE_CACHE.clear();
        FLUID_SURFACE_POINT_CACHE.clear();
        RAW_EXTERIOR_FLUID_CACHE.clear();
    }

    public static void render(final double camX, final double camY, final double camZ) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Minecraft mc = Minecraft.getInstance();
        final var level = mc.level;
        if (level == null || mc.gameRenderer == null) return;

        final Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;

        ensureExteriorFluidCacheLevel(level);

        final Vec3 cameraPos = new Vec3(camX, camY, camZ);
        final Vec3 cameraView = camera.getEntity() != null ? camera.getEntity().getViewVector(1.0F) : new Vec3(0.0, 0.0, 1.0);
        final double maxShipDistanceBlocks = getMaxOverlayShipDistanceBlocks(mc);
        final double minViewDot = getOverlayViewMinDot(mc);
        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, cameraView, maxShipDistanceBlocks, minViewDot, MAX_SHIPS);
        if (ships.isEmpty()) return;

        final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final VertexConsumer consumer = bufferSource.getBuffer(OVERLAY_RENDER_TYPE);

        for (final LoadedShip ship : ships) {
            final long shipId = ship.getId();
            final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot =
                ShipWaterPocketManager.getClientWaterReachableSnapshot(level, shipId);
            if (snapshot == null) continue;

            final ShipCache cache = SHIP_CACHE.computeIfAbsent(shipId, ShipCache::new);
            ensureOverlaySolids(level, cache, snapshot);

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc shipToWorld = shipTransform.getShipToWorld();
            final Matrix4f shipToWorldF = new Matrix4f(shipToWorld);

            final double minX = cache.minX;
            final double minY = cache.minY;
            final double minZ = cache.minZ;

            final double biasedM30 = shipToWorld.m00() * minX + shipToWorld.m10() * minY + shipToWorld.m20() * minZ + shipToWorld.m30();
            final double biasedM31 = shipToWorld.m01() * minX + shipToWorld.m11() * minY + shipToWorld.m21() * minZ + shipToWorld.m31();
            final double biasedM32 = shipToWorld.m02() * minX + shipToWorld.m12() * minY + shipToWorld.m22() * minZ + shipToWorld.m32();

            shipToWorldF.m30((float) (biasedM30 - camX));
            shipToWorldF.m31((float) (biasedM31 - camY));
            shipToWorldF.m32((float) (biasedM32 - camZ));

            emitOverlayFaces(
                level,
                shipToWorldF,
                consumer,
                cache,
                snapshot,
                shipToWorld.m00(),
                shipToWorld.m10(),
                shipToWorld.m20(),
                shipToWorld.m01(),
                shipToWorld.m11(),
                shipToWorld.m21(),
                shipToWorld.m02(),
                shipToWorld.m12(),
                shipToWorld.m22(),
                biasedM30,
                biasedM31,
                biasedM32
            );
        }

        bufferSource.endBatch();
    }

    private static RenderType OVERLAY_RENDER_TYPE = new RenderStateShard(null, null, null) {
        private static RenderType createOverlayRenderType() {
            return RenderType.create(
                "valkyrienskies_ship_liquid_overlay",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true)
            );
        }
    }.createOverlayRenderType();

    private static void ensureOverlaySolids(final net.minecraft.client.multiplayer.ClientLevel level, final ShipCache cache,
        final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot) {
        final int minX = snapshot.getMinX();
        final int minY = snapshot.getMinY();
        final int minZ = snapshot.getMinZ();
        final int sizeX = snapshot.getSizeX();
        final int sizeY = snapshot.getSizeY();
        final int sizeZ = snapshot.getSizeZ();
        final int volume = sizeX * sizeY * sizeZ;

        final boolean boundsChanged =
            cache.minX != minX || cache.minY != minY || cache.minZ != minZ ||
                cache.sizeX != sizeX || cache.sizeY != sizeY || cache.sizeZ != sizeZ;

        if (!boundsChanged && cache.geometryRevision == snapshot.getGeometryRevision() && cache.overlaySolids != null) {
            return;
        }

        cache.geometryRevision = snapshot.getGeometryRevision();
        cache.minX = minX;
        cache.minY = minY;
        cache.minZ = minZ;
        cache.sizeX = sizeX;
        cache.sizeY = sizeY;
        cache.sizeZ = sizeZ;

        final BitSet overlaySolids = new BitSet(volume);
        final BitSet fullCellOverlaySolids = new BitSet(volume);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int idx = 0;
        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++) {
                    pos.set(minX + lx, minY + ly, minZ + lz);
                    final BlockState state = level.getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) {
                        idx++;
                        continue;
                    }

                    if (isOverlaySolidCandidate(level, pos, state)) {
                        overlaySolids.set(idx);
                        if (shouldUseFullCellSolidOverlay(state)) {
                            fullCellOverlaySolids.set(idx);
                        }
                    }

                    idx++;
                }
            }
        }

        cache.overlaySolids = overlaySolids;
        cache.fullCellOverlaySolids = fullCellOverlaySolids;
        cache.overlayBoundary = buildOverlayBoundaryMask(snapshot.getOpen(), snapshot.getInterior(), overlaySolids,
            fullCellOverlaySolids, sizeX, sizeY, sizeZ);
    }

    private static int emitOverlayFaces(
        final net.minecraft.client.multiplayer.ClientLevel level,
        final Matrix4f matrix,
        final VertexConsumer consumer,
        final ShipCache cache,
        final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot,
        final double m00,
        final double m10,
        final double m20,
        final double m01,
        final double m11,
        final double m21,
        final double m02,
        final double m12,
        final double m22,
        final double tX,
        final double tY,
        final double tZ
    ) {
        final int sizeX = cache.sizeX;
        final int sizeY = cache.sizeY;
        final int sizeZ = cache.sizeZ;
        final int volume = sizeX * sizeY * sizeZ;
        if (volume <= 0) return 0;

        final BitSet open = snapshot.getOpen();
        final BitSet interior = snapshot.getInterior();
        final BitSet overlaySolids = cache.overlaySolids;
        final BitSet fullCellOverlaySolids = cache.fullCellOverlaySolids;
        final BitSet overlayBoundary = cache.overlayBoundary;

        final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos solidPos = new BlockPos.MutableBlockPos();

        final int strideY = sizeX;
        final int strideZ = sizeX * sizeY;
        int quadsEmitted = 0;

        for (int outsideIdx = open.nextSetBit(0); outsideIdx >= 0; outsideIdx = open.nextSetBit(outsideIdx + 1)) {
            if (outsideIdx >= volume) break;
            if (interior.get(outsideIdx)) continue;
            if (isFullCellOverlaySolid(fullCellOverlaySolids, outsideIdx)) continue;
            if (overlayBoundary == null || !overlayBoundary.get(outsideIdx)) {
                continue;
            }

            final int lx = outsideIdx % sizeX;
            final int t = outsideIdx / sizeX;
            final int ly = t % sizeY;
            final int lz = t / sizeY;
            if (lx > 0) {
                final int n = outsideIdx - 1;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + FACE_SAMPLE_OFFSET, ly + 0.5, lz + 0.5, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx - 1, cache.minY + ly, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceX(level, solidPos, matrix, consumer, lx - 1, ly, lz, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceXClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx - 1, cache.minY + ly, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceX(level, solidPos, matrix, consumer, lx - 1, ly, lz, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
            if (lx + 1 < sizeX) {
                final int n = outsideIdx + 1;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + 1.0 - FACE_SAMPLE_OFFSET, ly + 0.5, lz + 0.5, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx + 1, cache.minY + ly, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceX(level, solidPos, matrix, consumer, lx + 1, ly, lz, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceXClipped(matrix, consumer, lx + 1, ly, lz, -1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx + 1, cache.minY + ly, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceX(level, solidPos, matrix, consumer, lx + 1, ly, lz, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
            if (ly > 0) {
                final int n = outsideIdx - strideY;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + 0.5, ly + FACE_SAMPLE_OFFSET, lz + 0.5, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx, cache.minY + ly - 1, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceY(level, solidPos, matrix, consumer, lx, ly - 1, lz, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx, cache.minY + ly - 1, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceY(level, solidPos, matrix, consumer, lx, ly - 1, lz, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
            if (ly + 1 < sizeY) {
                final int n = outsideIdx + strideY;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + 0.5, ly + 1.0 - FACE_SAMPLE_OFFSET, lz + 0.5, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx, cache.minY + ly + 1, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceY(level, solidPos, matrix, consumer, lx, ly + 1, lz, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly + 1, lz, -1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx, cache.minY + ly + 1, cache.minZ + lz);
                            quadsEmitted += emitSolidFaceY(level, solidPos, matrix, consumer, lx, ly + 1, lz, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
            if (lz > 0) {
                final int n = outsideIdx - strideZ;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + 0.5, ly + 0.5, lz + FACE_SAMPLE_OFFSET, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx, cache.minY + ly, cache.minZ + lz - 1);
                            quadsEmitted += emitSolidFaceZ(level, solidPos, matrix, consumer, lx, ly, lz - 1, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx, cache.minY + ly, cache.minZ + lz - 1);
                            quadsEmitted += emitSolidFaceZ(level, solidPos, matrix, consumer, lx, ly, lz - 1, true, +1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
            if (lz + 1 < sizeZ) {
                final int n = outsideIdx + strideZ;
                final boolean fullCell = isFullCellOverlaySolid(fullCellOverlaySolids, n);
                final boolean interiorFace = isInteriorOpen(open, interior, n);
                final boolean solidFace = !open.get(n) && overlaySolids != null && overlaySolids.get(n);
                if (fullCell || interiorFace || solidFace) {
                    final @Nullable OverlayFaceSample sample = resolveOverlayFaceSample(level, snapshot, fluidPos, scanPos,
                        lx + 0.5, ly + 0.5, lz + 1.0 - FACE_SAMPLE_OFFSET, m00, m10, m20, m01, m11, m21, m02, m12, m22, tX, tY, tZ);
                    if (sample != null) {
                        if (fullCell) {
                            solidPos.set(cache.minX + lx, cache.minY + ly, cache.minZ + lz + 1);
                            quadsEmitted += emitSolidFaceZ(level, solidPos, matrix, consumer, lx, ly, lz + 1, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else if (interiorFace) {
                            quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz + 1, -1.0f, false, m01, m11, m21, tY,
                                sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        } else {
                            solidPos.set(cache.minX + lx, cache.minY + ly, cache.minZ + lz + 1);
                            quadsEmitted += emitSolidFaceZ(level, solidPos, matrix, consumer, lx, ly, lz + 1, false, -1.0f, m01, m11, m21,
                                tY, sample.surfaceY, sample.sprite, sample.r, sample.g, sample.b, OVERLAY_ALPHA);
                        }
                    }
                }
            }
        }

        return quadsEmitted;
    }

    static BitSet buildOverlayBoundaryMask(final BitSet open, final BitSet interior, final @Nullable BitSet overlaySolids,
        final @Nullable BitSet fullCellOverlaySolids, final int sizeX, final int sizeY, final int sizeZ) {
        final int volume = sizeX * sizeY * sizeZ;
        final BitSet overlayBoundary = new BitSet(volume);
        for (int outsideIdx = open.nextSetBit(0); outsideIdx >= 0 && outsideIdx < volume; outsideIdx = open.nextSetBit(outsideIdx + 1)) {
            if (interior.get(outsideIdx)) continue;
            if (isFullCellOverlaySolid(fullCellOverlaySolids, outsideIdx)) continue;
            if (touchesOverlayBoundary(open, interior, open, overlaySolids, fullCellOverlaySolids, outsideIdx, sizeX, sizeY, sizeZ)) {
                overlayBoundary.set(outsideIdx);
            }
        }
        return overlayBoundary;
    }

    static boolean isOutsideSubmergedFluid(final BitSet open, final BitSet interior, final BitSet waterReachable, final int idx) {
        return open.get(idx) && !interior.get(idx) && waterReachable.get(idx);
    }

    static boolean isInteriorOpen(final BitSet open, final BitSet interior, final int idx) {
        return open.get(idx) && interior.get(idx);
    }

    static boolean isFullCellOverlaySolid(final @Nullable BitSet fullCellOverlaySolids, final int idx) {
        return fullCellOverlaySolids != null && fullCellOverlaySolids.get(idx);
    }

    static boolean touchesOverlayBoundary(final BitSet open, final BitSet interior, final BitSet waterReachable,
        final @Nullable BitSet overlaySolids, final @Nullable BitSet fullCellOverlaySolids, final int outsideIdx, final int sizeX,
        final int sizeY, final int sizeZ) {
        final int lx = outsideIdx % sizeX;
        final int t = outsideIdx / sizeX;
        final int ly = t % sizeY;
        final int lz = t / sizeY;
        final int strideY = sizeX;
        final int strideZ = sizeX * sizeY;

        boolean needsOverlay = false;
        if (lx > 0) {
            final int n = outsideIdx - 1;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        if (!needsOverlay && lx + 1 < sizeX) {
            final int n = outsideIdx + 1;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        if (!needsOverlay && ly > 0) {
            final int n = outsideIdx - strideY;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        if (!needsOverlay && ly + 1 < sizeY) {
            final int n = outsideIdx + strideY;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        if (!needsOverlay && lz > 0) {
            final int n = outsideIdx - strideZ;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        if (!needsOverlay && lz + 1 < sizeZ) {
            final int n = outsideIdx + strideZ;
            needsOverlay |= isInteriorOpen(open, interior, n) || isOverlaySolidBoundary(open, overlaySolids, fullCellOverlaySolids, n);
        }
        return needsOverlay;
    }

    private static boolean isOverlaySolidBoundary(final BitSet open, final @Nullable BitSet overlaySolids,
        final @Nullable BitSet fullCellOverlaySolids, final int idx) {
        return isFullCellOverlaySolid(fullCellOverlaySolids, idx) || (!open.get(idx) && overlaySolids != null && overlaySolids.get(idx));
    }

    static Fluid chooseOverlayFluid(final @Nullable Fluid sampledFluid, final @Nullable Fluid fallbackFluid) {
        if (sampledFluid != null) return sampledFluid;
        if (fallbackFluid != null) return fallbackFluid;
        return net.minecraft.world.level.material.Fluids.WATER;
    }

    private static Fluid canonicalSource(final Fluid fluid) {
        return fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    static boolean isOverlaySolidCandidate(final boolean transparentRenderType, final boolean solidRender,
        final boolean propagatesSkylight, final int lightBlock) {
        return transparentRenderType || !solidRender || propagatesSkylight || lightBlock < 15;
    }

    private static boolean isOverlaySolidCandidate(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos,
        final BlockState state) {
        return isOverlaySolidCandidate(
            isOverlaySolidRenderType(ItemBlockRenderTypes.getChunkRenderType(state)),
            state.isSolidRender(level, pos),
            state.propagatesSkylightDown(level, pos),
            state.getLightBlock(level, pos)
        );
    }

    private static boolean isOverlaySolidRenderType(final RenderType renderType) {
        return renderType == RenderType.translucent() || renderType == RenderType.cutout() || renderType == RenderType.cutoutMipped();
    }

    private static @Nullable FluidSurfaceSample findExteriorFluidSurface(final net.minecraft.client.multiplayer.ClientLevel level,
        final BlockPos.MutableBlockPos fluidPos, final BlockPos.MutableBlockPos scanPos, final double worldX, final double worldY,
        final double worldZ) {
        FluidSurfaceSample best = null;
        for (final double[] offset : FLUID_SAMPLE_OFFSETS) {
            final FluidSurfaceSample candidate = findExteriorFluidSurfaceAtSamplePoint(
                level,
                fluidPos,
                scanPos,
                worldX + offset[0],
                worldY + offset[1],
                worldZ + offset[2]
            );
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.surfaceY > best.surfaceY + SURFACE_EPS) {
                best = candidate;
            }
        }
        return best;
    }

    private static @Nullable OverlayFaceSample resolveOverlayFaceSample(final net.minecraft.client.multiplayer.ClientLevel level,
        final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot, final BlockPos.MutableBlockPos fluidPos,
        final BlockPos.MutableBlockPos scanPos, final double localX, final double localY, final double localZ, final double m00,
        final double m10, final double m20, final double m01, final double m11, final double m21, final double m02,
        final double m12, final double m22, final double tX, final double tY, final double tZ) {
        final double worldX = m00 * localX + m10 * localY + m20 * localZ + tX;
        final double worldY = m01 * localX + m11 * localY + m21 * localZ + tY;
        final double worldZ = m02 * localX + m12 * localY + m22 * localZ + tZ;

        final FluidSurfaceSample surface = findExteriorFluidSurface(level, fluidPos, scanPos, worldX, worldY, worldZ);
        if (surface == null) {
            return null;
        }
        final Fluid overlayFluid = chooseOverlayFluid(surface != null ? surface.fluid : null, snapshot.getFloodFluid());
        final FluidState overlayFluidState =
            surface != null && !surface.fluidState.isEmpty() ? surface.fluidState : overlayFluid.defaultFluidState();
        final BlockPos tintPos;
        if (surface != null) {
            tintPos = surface.pos;
        } else {
            fluidPos.set(Mth.floor(worldX), Mth.floor(worldY), Mth.floor(worldZ));
            tintPos = fluidPos.immutable();
        }

        final ShipWaterPocketFluidVisualHelper.FluidVisual visual =
            ShipWaterPocketFluidVisualHelper.resolveFluidVisual(level, tintPos, overlayFluidState, overlayFluid);
        final int rgb = visual.getTintRgb();
        return new OverlayFaceSample(
            visual.getSprite(),
            ((rgb >> 16) & 0xFF) / 255.0f,
            ((rgb >> 8) & 0xFF) / 255.0f,
            (rgb & 0xFF) / 255.0f,
            surface != null ? surface.surfaceY : Double.NaN
        );
    }

    private static @Nullable FluidSurfaceSample findExteriorFluidSurfaceAtSamplePoint(
        final net.minecraft.client.multiplayer.ClientLevel level,
        final BlockPos.MutableBlockPos fluidPos,
        final BlockPos.MutableBlockPos scanPos,
        final double worldX,
        final double worldY,
        final double worldZ
    ) {
        final int blockX = Mth.floor(worldX);
        final int blockY = Mth.floor(worldY);
        final int blockZ = Mth.floor(worldZ);
        final long chunkRevision = currentExteriorFluidChunkRevision(exteriorFluidChunkKey(blockX, blockZ));
        final long pointKey = BlockPos.asLong(blockX, blockY, blockZ);
        final ChunkTrackedFluidSurface pointCached = FLUID_SURFACE_POINT_CACHE.get(pointKey);
        if (pointCached != null) {
            if (pointCached.chunkRevision == chunkRevision) {
                return pointCached.sample == FLUID_SURFACE_MISS ? null : pointCached.sample;
            }
            FLUID_SURFACE_POINT_CACHE.remove(pointKey);
        }

        fluidPos.set(blockX, blockY, blockZ);
        FluidState sampleState = getRawExteriorFluidState(level, fluidPos);
        if (sampleState == null) {
            fluidPos.move(0, -1, 0);
            sampleState = getRawExteriorFluidState(level, fluidPos);
            if (sampleState == null) {
                fluidPos.move(0, 2, 0);
                sampleState = getRawExteriorFluidState(level, fluidPos);
                if (sampleState == null) {
                    cacheFluidSurfacePoint(pointKey, chunkRevision, FLUID_SURFACE_MISS);
                    return null;
                }
            }
        }

        final long key = BlockPos.asLong(fluidPos.getX(), fluidPos.getY(), fluidPos.getZ());
        final ChunkTrackedFluidSurface cached = FLUID_SURFACE_CACHE.get(key);
        if (cached != null) {
            if (cached.chunkRevision == chunkRevision) {
                cacheFluidSurfacePoint(pointKey, chunkRevision, cached.sample);
                return cached.sample == FLUID_SURFACE_MISS ? null : cached.sample;
            }
            FLUID_SURFACE_CACHE.remove(key);
        }

        final Fluid canonicalFluid = canonicalSource(sampleState.getType());
        scanPos.set(fluidPos);
        final int maxYExclusive = level.getMaxBuildHeight();
        while (scanPos.getY() < maxYExclusive) {
            final FluidState current = getRawExteriorFluidState(level, scanPos);
            if (current == null || canonicalSource(current.getType()) != canonicalFluid) break;
            scanPos.move(0, 1, 0);
        }
        scanPos.move(0, -1, 0);

        final FluidState topFluid = getRawExteriorFluidState(level, scanPos);
        if (topFluid == null) {
            cacheFluidSurfacePoint(pointKey, chunkRevision, FLUID_SURFACE_MISS);
            return null;
        }

        final FluidSurfaceSample sample = new FluidSurfaceSample(
            canonicalFluid,
            topFluid,
            scanPos.immutable(),
            scanPos.getY() + rawExteriorFluidHeight(topFluid)
        );
        if (FLUID_SURFACE_CACHE.size() >= MAX_FLUID_SURFACE_CACHE) {
            FLUID_SURFACE_CACHE.clear();
        }
        FLUID_SURFACE_CACHE.put(key, new ChunkTrackedFluidSurface(chunkRevision, sample));
        cacheFluidSurfacePoint(pointKey, chunkRevision, sample);
        return sample;
    }

    private static void cacheFluidSurfacePoint(final long pointKey, final long chunkRevision, final FluidSurfaceSample sample) {
        if (FLUID_SURFACE_POINT_CACHE.size() >= MAX_FLUID_SURFACE_POINT_CACHE) {
            FLUID_SURFACE_POINT_CACHE.clear();
        }
        FLUID_SURFACE_POINT_CACHE.put(pointKey, new ChunkTrackedFluidSurface(chunkRevision, sample));
    }

    private static @Nullable FluidState getRawExteriorFluidState(
        final net.minecraft.client.multiplayer.ClientLevel level,
        final BlockPos pos
    ) {
        final long chunkRevision = currentExteriorFluidChunkRevision(exteriorFluidChunkKey(pos.getX(), pos.getZ()));
        final long key = pos.asLong();
        final ChunkTrackedFluidState cached = RAW_EXTERIOR_FLUID_CACHE.get(key);
        if (cached != null) {
            if (cached.chunkRevision == chunkRevision) {
                return cached.fluidState == RAW_EXTERIOR_FLUID_MISS ? null : cached.fluidState;
            }
            RAW_EXTERIOR_FLUID_CACHE.remove(key);
        }

        final FluidState result;
        if (!level.hasChunkAt(pos)) {
            result = null;
        } else {
            final boolean inShipyard = VSGameUtilsKt.isBlockInShipyard(level, pos);
            final FluidState rawFluid = level.getChunkAt(pos).getFluidState(pos);
            result = shouldUseExteriorFluidSample(inShipyard, rawFluid.isEmpty()) ? rawFluid : null;
        }

        if (RAW_EXTERIOR_FLUID_CACHE.size() >= MAX_RAW_EXTERIOR_FLUID_CACHE) {
            RAW_EXTERIOR_FLUID_CACHE.clear();
        }
        RAW_EXTERIOR_FLUID_CACHE.put(
            key,
            new ChunkTrackedFluidState(chunkRevision, result != null ? result : RAW_EXTERIOR_FLUID_MISS)
        );
        return result;
    }

    private static void ensureExteriorFluidCacheLevel(final net.minecraft.client.multiplayer.ClientLevel level) {
        if (lastSurfaceCacheLevel == level) return;
        lastSurfaceCacheLevel = level;
        clearExteriorFluidCaches();
    }

    private static void invalidateExteriorFluidChunk(final int chunkX, final int chunkZ) {
        final long chunkKey = exteriorFluidChunkKeyFromChunk(chunkX, chunkZ);
        EXTERIOR_FLUID_CHUNK_REVISIONS.put(chunkKey, currentExteriorFluidChunkRevision(chunkKey) + 1L);
    }

    private static long exteriorFluidChunkKey(final int blockX, final int blockZ) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    private static long exteriorFluidChunkKeyFromChunk(final int chunkX, final int chunkZ) {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    private static long currentExteriorFluidChunkRevision(final long chunkKey) {
        return EXTERIOR_FLUID_CHUNK_REVISIONS.get(chunkKey);
    }

    static boolean shouldUseExteriorFluidSample(final boolean inShipyard, final boolean emptyFluid) {
        return !inShipyard && !emptyFluid;
    }

    static float rawExteriorFluidHeight(final FluidState fluidState) {
        return fluidState.getOwnHeight();
    }

    private static double worldY(final double m01, final double m11, final double m21, final double tY, final float x,
        final float y, final float z) {
        return m01 * x + m11 * y + m21 * z + tY;
    }

    private static int emitFaceXClipped(final Matrix4f matrix, final VertexConsumer consumer, final int xPlane, final int y0,
        final int z0, final float normalX, final boolean biasTowardNormal, final double m01, final double m11, final double m21,
        final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        return emitFaceXRectClipped(matrix, consumer, xPlane, y0, y0 + 1.0f, z0, z0 + 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, normalX,
            biasTowardNormal, m01, m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
    }

    private static int emitFaceXRectClipped(final Matrix4f matrix, final VertexConsumer consumer, final float xPlane, final float y0,
        final float y1, final float z0, final float z1, final float u0, final float u1, final float v0, final float v1,
        final float normalX, final boolean biasTowardNormal, final double m01, final double m11, final double m21, final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float x = xPlane + (biasTowardNormal ? FACE_EPS * normalX : -FACE_EPS * normalX);

        final double wy0 = worldY(m01, m11, m21, tY, x, y0, z0);
        final double wy1 = worldY(m01, m11, m21, tY, x, y1, z0);
        final double wy2 = worldY(m01, m11, m21, tY, x, y1, z1);
        final double wy3 = worldY(m01, m11, m21, tY, x, y0, z1);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0, u0, v0, u1, v1, normalX, 0.0f, 0.0f,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x;
        CLIP_Y0[0] = y0;
        CLIP_Z0[0] = z0;
        CLIP_U0[0] = u0;
        CLIP_V0[0] = v0;
        CLIP_X0[1] = x;
        CLIP_Y0[1] = y0;
        CLIP_Z0[1] = z1;
        CLIP_U0[1] = u1;
        CLIP_V0[1] = v0;
        CLIP_X0[2] = x;
        CLIP_Y0[2] = y1;
        CLIP_Z0[2] = z1;
        CLIP_U0[2] = u1;
        CLIP_V0[2] = v1;
        CLIP_X0[3] = x;
        CLIP_Y0[3] = y1;
        CLIP_Z0[3] = z0;
        CLIP_U0[3] = u0;
        CLIP_V0[3] = v1;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, normalX, 0.0f, 0.0f, r, g, b, a);
    }

    private static int emitFaceYClipped(final Matrix4f matrix, final VertexConsumer consumer, final int x0, final int yPlane,
        final int z0, final float normalY, final boolean biasTowardNormal, final double m01, final double m11, final double m21,
        final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        return emitFaceYRectClipped(matrix, consumer, x0, x0 + 1.0f, yPlane, z0, z0 + 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, normalY,
            biasTowardNormal, m01, m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
    }

    private static int emitFaceYRectClipped(final Matrix4f matrix, final VertexConsumer consumer, final float x0, final float x1,
        final float yPlane, final float z0, final float z1, final float u0, final float u1, final float v0, final float v1,
        final float normalY, final boolean biasTowardNormal, final double m01, final double m11, final double m21, final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float y = yPlane + (biasTowardNormal ? FACE_EPS * normalY : -FACE_EPS * normalY);

        final double wy0 = worldY(m01, m11, m21, tY, x0, y, z0);
        final double wy1 = worldY(m01, m11, m21, tY, x1, y, z0);
        final double wy2 = worldY(m01, m11, m21, tY, x1, y, z1);
        final double wy3 = worldY(m01, m11, m21, tY, x0, y, z1);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, u0, v0, u1, v1, 0.0f, normalY, 0.0f,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x0;
        CLIP_Y0[0] = y;
        CLIP_Z0[0] = z0;
        CLIP_U0[0] = u0;
        CLIP_V0[0] = v0;
        CLIP_X0[1] = x1;
        CLIP_Y0[1] = y;
        CLIP_Z0[1] = z0;
        CLIP_U0[1] = u1;
        CLIP_V0[1] = v0;
        CLIP_X0[2] = x1;
        CLIP_Y0[2] = y;
        CLIP_Z0[2] = z1;
        CLIP_U0[2] = u1;
        CLIP_V0[2] = v1;
        CLIP_X0[3] = x0;
        CLIP_Y0[3] = y;
        CLIP_Z0[3] = z1;
        CLIP_U0[3] = u0;
        CLIP_V0[3] = v1;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, 0.0f, normalY, 0.0f, r, g, b, a);
    }

    private static int emitFaceZClipped(final Matrix4f matrix, final VertexConsumer consumer, final int x0, final int y0,
        final int zPlane, final float normalZ, final boolean biasTowardNormal, final double m01, final double m11,
        final double m21, final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        return emitFaceZRectClipped(matrix, consumer, x0, x0 + 1.0f, y0, y0 + 1.0f, zPlane, 0.0f, 1.0f, 0.0f, 1.0f, normalZ,
            biasTowardNormal, m01, m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
    }

    private static int emitFaceZRectClipped(final Matrix4f matrix, final VertexConsumer consumer, final float x0, final float x1,
        final float y0, final float y1, final float zPlane, final float u0, final float u1, final float v0, final float v1,
        final float normalZ, final boolean biasTowardNormal, final double m01, final double m11, final double m21, final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float z = zPlane + (biasTowardNormal ? FACE_EPS * normalZ : -FACE_EPS * normalZ);

        final double wy0 = worldY(m01, m11, m21, tY, x0, y0, z);
        final double wy1 = worldY(m01, m11, m21, tY, x1, y0, z);
        final double wy2 = worldY(m01, m11, m21, tY, x1, y1, z);
        final double wy3 = worldY(m01, m11, m21, tY, x0, y1, z);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, u0, v0, u1, v1, 0.0f, 0.0f, normalZ,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x0;
        CLIP_Y0[0] = y0;
        CLIP_Z0[0] = z;
        CLIP_U0[0] = u0;
        CLIP_V0[0] = v0;
        CLIP_X0[1] = x1;
        CLIP_Y0[1] = y0;
        CLIP_Z0[1] = z;
        CLIP_U0[1] = u1;
        CLIP_V0[1] = v0;
        CLIP_X0[2] = x1;
        CLIP_Y0[2] = y1;
        CLIP_Z0[2] = z;
        CLIP_U0[2] = u1;
        CLIP_V0[2] = v1;
        CLIP_X0[3] = x0;
        CLIP_Y0[3] = y1;
        CLIP_Z0[3] = z;
        CLIP_U0[3] = u0;
        CLIP_V0[3] = v1;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, 0.0f, 0.0f, normalZ, r, g, b, a);
    }

    private static int emitSolidFaceX(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos,
        final Matrix4f matrix, final VertexConsumer consumer, final int solidX, final int solidY, final int solidZ,
        final boolean towardsPositiveAxis, final float normalX, final double m01, final double m11, final double m21,
        final double tY, final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b,
        final float a) {
        final BlockState state = level.getBlockState(pos);
        if (shouldUseFullCellSolidOverlay(state)) {
            return emitFaceXClipped(matrix, consumer, towardsPositiveAxis ? solidX + 1 : solidX, solidY, solidZ, normalX, true, m01,
                m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
        }
        final OverlayShapeTemplate template = getOverlayShapeTemplate(level, pos);
        if (!template.fullSolid && template.hasOpenVolume) {
            return emitSolidAccessibleFaces(
                matrix,
                consumer,
                template,
                shouldRenderOverlaySolidInterfaces(state),
                solidX,
                solidY,
                solidZ,
                towardsPositiveAxis ? SHAPE_FACE_POS_X : SHAPE_FACE_NEG_X,
                m01,
                m11,
                m21,
                tY,
                fluidSurfaceY,
                sprite,
                r,
                g,
                b,
                a
            );
        }

        final AABB bounds = getOverlayShapeBounds(level, pos);
        if (bounds == null || !touchesFace(bounds.minX, bounds.maxX, towardsPositiveAxis)) {
            return 0;
        }
        return emitFaceXRectClipped(
            matrix,
            consumer,
            solidX + (towardsPositiveAxis ? (float) bounds.maxX : (float) bounds.minX),
            solidY + (float) bounds.minY,
            solidY + (float) bounds.maxY,
            solidZ + (float) bounds.minZ,
            solidZ + (float) bounds.maxZ,
            (float) bounds.minY,
            (float) bounds.maxY,
            (float) bounds.minZ,
            (float) bounds.maxZ,
            normalX,
            true,
            m01,
            m11,
            m21,
            tY,
            fluidSurfaceY,
            sprite,
            r,
            g,
            b,
            a
        );
    }

    private static int emitSolidFaceY(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos,
        final Matrix4f matrix, final VertexConsumer consumer, final int solidX, final int solidY, final int solidZ,
        final boolean towardsPositiveAxis, final float normalY, final double m01, final double m11, final double m21,
        final double tY, final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b,
        final float a) {
        final BlockState state = level.getBlockState(pos);
        if (shouldUseFullCellSolidOverlay(state)) {
            return emitFaceYClipped(matrix, consumer, solidX, towardsPositiveAxis ? solidY + 1 : solidY, solidZ, normalY, true, m01,
                m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
        }
        final OverlayShapeTemplate template = getOverlayShapeTemplate(level, pos);
        if (!template.fullSolid && template.hasOpenVolume) {
            return emitSolidAccessibleFaces(
                matrix,
                consumer,
                template,
                shouldRenderOverlaySolidInterfaces(state),
                solidX,
                solidY,
                solidZ,
                towardsPositiveAxis ? SHAPE_FACE_POS_Y : SHAPE_FACE_NEG_Y,
                m01,
                m11,
                m21,
                tY,
                fluidSurfaceY,
                sprite,
                r,
                g,
                b,
                a
            );
        }

        final AABB bounds = getOverlayShapeBounds(level, pos);
        if (bounds == null || !touchesFace(bounds.minY, bounds.maxY, towardsPositiveAxis)) {
            return 0;
        }
        return emitFaceYRectClipped(
            matrix,
            consumer,
            solidX + (float) bounds.minX,
            solidX + (float) bounds.maxX,
            solidY + (towardsPositiveAxis ? (float) bounds.maxY : (float) bounds.minY),
            solidZ + (float) bounds.minZ,
            solidZ + (float) bounds.maxZ,
            (float) bounds.minX,
            (float) bounds.maxX,
            (float) bounds.minZ,
            (float) bounds.maxZ,
            normalY,
            true,
            m01,
            m11,
            m21,
            tY,
            fluidSurfaceY,
            sprite,
            r,
            g,
            b,
            a
        );
    }

    private static int emitSolidFaceZ(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos,
        final Matrix4f matrix, final VertexConsumer consumer, final int solidX, final int solidY, final int solidZ,
        final boolean towardsPositiveAxis, final float normalZ, final double m01, final double m11, final double m21,
        final double tY, final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b,
        final float a) {
        final BlockState state = level.getBlockState(pos);
        if (shouldUseFullCellSolidOverlay(state)) {
            return emitFaceZClipped(matrix, consumer, solidX, solidY, towardsPositiveAxis ? solidZ + 1 : solidZ, normalZ, true, m01,
                m11, m21, tY, fluidSurfaceY, sprite, r, g, b, a);
        }
        final OverlayShapeTemplate template = getOverlayShapeTemplate(level, pos);
        if (!template.fullSolid && template.hasOpenVolume) {
            return emitSolidAccessibleFaces(
                matrix,
                consumer,
                template,
                shouldRenderOverlaySolidInterfaces(state),
                solidX,
                solidY,
                solidZ,
                towardsPositiveAxis ? SHAPE_FACE_POS_Z : SHAPE_FACE_NEG_Z,
                m01,
                m11,
                m21,
                tY,
                fluidSurfaceY,
                sprite,
                r,
                g,
                b,
                a
            );
        }

        final AABB bounds = getOverlayShapeBounds(level, pos);
        if (bounds == null || !touchesFace(bounds.minZ, bounds.maxZ, towardsPositiveAxis)) {
            return 0;
        }
        return emitFaceZRectClipped(
            matrix,
            consumer,
            solidX + (float) bounds.minX,
            solidX + (float) bounds.maxX,
            solidY + (float) bounds.minY,
            solidY + (float) bounds.maxY,
            solidZ + (towardsPositiveAxis ? (float) bounds.maxZ : (float) bounds.minZ),
            (float) bounds.minX,
            (float) bounds.maxX,
            (float) bounds.minY,
            (float) bounds.maxY,
            normalZ,
            true,
            m01,
            m11,
            m21,
            tY,
            fluidSurfaceY,
            sprite,
            r,
            g,
            b,
            a
        );
    }

    static boolean shouldUseFullCellSolidOverlay(final BlockState state) {
        return state.getBlock() instanceof DoorBlock;
    }

    static boolean shouldRenderOverlaySolidInterfaces(final BlockState state) {
        return true;
    }

    private static boolean touchesFace(final double min, final double max, final boolean towardsPositiveAxis) {
        return towardsPositiveAxis ? max >= 1.0 - SURFACE_EPS : min <= SURFACE_EPS;
    }

    private static int emitSolidAccessibleFaces(final Matrix4f matrix, final VertexConsumer consumer,
        final OverlayShapeTemplate template, final boolean renderSolidInterfaces, final int solidX, final int solidY,
        final int solidZ, final int entryFace,
        final double m01, final double m11, final double m21, final double tY, final double fluidSurfaceY,
        final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final long accessibleMask = template.faceComponentMask[entryFace];
        if (accessibleMask == 0L) {
            return 0;
        }

        int quadsEmitted = 0;
        final float inv = 1.0f / SHAPE_SUBCELL_RES;

        for (int sz = 0; sz < SHAPE_SUBCELL_RES; sz++) {
            for (int sy = 0; sy < SHAPE_SUBCELL_RES; sy++) {
                for (int sx = 0; sx < SHAPE_SUBCELL_RES; sx++) {
                    final int subIdx = subcellIndex(sx, sy, sz);
                    final int component = template.componentBySubcell[subIdx];
                    if (!componentMaskContains(accessibleMask, component)) {
                        continue;
                    }

                    final float x0 = sx * inv;
                    final float x1 = x0 + inv;
                    final float y0 = sy * inv;
                    final float y1 = y0 + inv;
                    final float z0 = sz * inv;
                    final float z1 = z0 + inv;

                    if (entryFace == SHAPE_FACE_NEG_X && sx == 0) {
                        quadsEmitted += emitFaceXRectClipped(
                            matrix,
                            consumer,
                            solidX,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z0,
                            solidZ + z1,
                            y0,
                            y1,
                            z0,
                            z1,
                            -1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (entryFace == SHAPE_FACE_POS_X && sx + 1 == SHAPE_SUBCELL_RES) {
                        quadsEmitted += emitFaceXRectClipped(
                            matrix,
                            consumer,
                            solidX + 1.0f,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z0,
                            solidZ + z1,
                            y0,
                            y1,
                            z0,
                            z1,
                            +1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (entryFace == SHAPE_FACE_NEG_Y && sy == 0) {
                        quadsEmitted += emitFaceYRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY,
                            solidZ + z0,
                            solidZ + z1,
                            x0,
                            x1,
                            z0,
                            z1,
                            -1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (entryFace == SHAPE_FACE_POS_Y && sy + 1 == SHAPE_SUBCELL_RES) {
                        quadsEmitted += emitFaceYRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + 1.0f,
                            solidZ + z0,
                            solidZ + z1,
                            x0,
                            x1,
                            z0,
                            z1,
                            +1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (entryFace == SHAPE_FACE_NEG_Z && sz == 0) {
                        quadsEmitted += emitFaceZRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y0,
                            solidY + y1,
                            solidZ,
                            x0,
                            x1,
                            y0,
                            y1,
                            -1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (entryFace == SHAPE_FACE_POS_Z && sz + 1 == SHAPE_SUBCELL_RES) {
                        quadsEmitted += emitFaceZRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y0,
                            solidY + y1,
                            solidZ + 1.0f,
                            x0,
                            x1,
                            y0,
                            y1,
                            +1.0f,
                            false,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }

                    if (!renderSolidInterfaces) {
                        continue;
                    }

                    if (sx > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx - 1, sy, sz))) {
                        quadsEmitted += emitFaceXRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z0,
                            solidZ + z1,
                            y0,
                            y1,
                            z0,
                            z1,
                            +1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (sx + 1 < SHAPE_SUBCELL_RES && subcellSolid(template.occupancyMask, subcellIndex(sx + 1, sy, sz))) {
                        quadsEmitted += emitFaceXRectClipped(
                            matrix,
                            consumer,
                            solidX + x1,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z0,
                            solidZ + z1,
                            y0,
                            y1,
                            z0,
                            z1,
                            -1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (sy > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx, sy - 1, sz))) {
                        quadsEmitted += emitFaceYRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y0,
                            solidZ + z0,
                            solidZ + z1,
                            x0,
                            x1,
                            z0,
                            z1,
                            +1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (sy + 1 < SHAPE_SUBCELL_RES && subcellSolid(template.occupancyMask, subcellIndex(sx, sy + 1, sz))) {
                        quadsEmitted += emitFaceYRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y1,
                            solidZ + z0,
                            solidZ + z1,
                            x0,
                            x1,
                            z0,
                            z1,
                            -1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (sz > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx, sy, sz - 1))) {
                        quadsEmitted += emitFaceZRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z0,
                            x0,
                            x1,
                            y0,
                            y1,
                            +1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                    if (sz + 1 < SHAPE_SUBCELL_RES && subcellSolid(template.occupancyMask, subcellIndex(sx, sy, sz + 1))) {
                        quadsEmitted += emitFaceZRectClipped(
                            matrix,
                            consumer,
                            solidX + x0,
                            solidX + x1,
                            solidY + y0,
                            solidY + y1,
                            solidZ + z1,
                            x0,
                            x1,
                            y0,
                            y1,
                            -1.0f,
                            true,
                            m01,
                            m11,
                            m21,
                            tY,
                            fluidSurfaceY,
                            sprite,
                            r,
                            g,
                            b,
                            a
                        );
                    }
                }
            }
        }

        return quadsEmitted;
    }

    private static int subcellIndex(final int sx, final int sy, final int sz) {
        return sx + SHAPE_SUBCELL_RES * (sy + SHAPE_SUBCELL_RES * sz);
    }

    private static boolean subcellSolid(final long[] occupancyMask, final int subIdx) {
        final int word = subIdx >>> 6;
        final int bit = subIdx & 63;
        return ((occupancyMask[word] >>> bit) & 1L) != 0L;
    }

    private static void setSubcellSolid(final long[] occupancyMask, final int subIdx) {
        final int word = subIdx >>> 6;
        final int bit = subIdx & 63;
        occupancyMask[word] |= 1L << bit;
    }

    private static int faceSampleSubcellIndex(final int face, final int u, final int v) {
        return switch (face) {
            case SHAPE_FACE_NEG_X -> subcellIndex(0, u, v);
            case SHAPE_FACE_POS_X -> subcellIndex(SHAPE_SUBCELL_RES - 1, u, v);
            case SHAPE_FACE_NEG_Y -> subcellIndex(u, 0, v);
            case SHAPE_FACE_POS_Y -> subcellIndex(u, SHAPE_SUBCELL_RES - 1, v);
            case SHAPE_FACE_NEG_Z -> subcellIndex(u, v, 0);
            default -> subcellIndex(u, v, SHAPE_SUBCELL_RES - 1);
        };
    }

    private static boolean componentMaskContains(final long mask, final int component) {
        return component >= 0 && component < Long.SIZE && ((mask >>> component) & 1L) != 0L;
    }

    private static OverlayShapeTemplate getOverlayShapeTemplate(final net.minecraft.client.multiplayer.ClientLevel level,
        final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        return SHAPE_TEMPLATE_CACHE.computeIfAbsent(state, ignored -> buildOverlayShapeTemplate(level, pos, state));
    }

    private static OverlayShapeTemplate buildOverlayShapeTemplate(final net.minecraft.client.multiplayer.ClientLevel level,
        final BlockPos pos, final BlockState state) {
        final VoxelShape shape = resolveOverlayShape(level, pos, state);
        if (shape.isEmpty()) {
            return new OverlayShapeTemplate(new long[(SHAPE_SUBCELL_COUNT + 63) >>> 6], new byte[SHAPE_SUBCELL_COUNT], new long[6],
                false, false);
        }

        return buildOverlayShapeTemplate(shape.toAabbs());
    }

    private static OverlayShapeTemplate buildOverlayShapeTemplate(final List<AABB> boxes) {
        if (boxes.isEmpty()) {
            return new OverlayShapeTemplate(new long[(SHAPE_SUBCELL_COUNT + 63) >>> 6], new byte[SHAPE_SUBCELL_COUNT], new long[6],
                false, false);
        }

        final long[] occupancyMask = new long[(SHAPE_SUBCELL_COUNT + 63) >>> 6];
        final byte[] componentBySubcell = new byte[SHAPE_SUBCELL_COUNT];
        java.util.Arrays.fill(componentBySubcell, SHAPE_COMPONENT_UNASSIGNED);

        boolean hasOpen = false;
        for (int sz = 0; sz < SHAPE_SUBCELL_RES; sz++) {
            final double z = (sz + 0.5) / (double) SHAPE_SUBCELL_RES;
            for (int sy = 0; sy < SHAPE_SUBCELL_RES; sy++) {
                final double y = (sy + 0.5) / (double) SHAPE_SUBCELL_RES;
                for (int sx = 0; sx < SHAPE_SUBCELL_RES; sx++) {
                    final double x = (sx + 0.5) / (double) SHAPE_SUBCELL_RES;
                    final int subIdx = subcellIndex(sx, sy, sz);
                    if (isSolidAt(boxes, x, y, z)) {
                        componentBySubcell[subIdx] = SHAPE_COMPONENT_SOLID;
                        setSubcellSolid(occupancyMask, subIdx);
                    } else {
                        hasOpen = true;
                    }
                }
            }
        }

        if (!hasOpen) {
            return new OverlayShapeTemplate(occupancyMask, componentBySubcell, new long[6], false, true);
        }

        int componentCount = 0;
        final int[] queue = new int[SHAPE_SUBCELL_COUNT];
        for (int start = 0; start < SHAPE_SUBCELL_COUNT; start++) {
            if (subcellSolid(occupancyMask, start)) {
                continue;
            }
            if (componentBySubcell[start] != SHAPE_COMPONENT_UNASSIGNED) {
                continue;
            }

            final int componentId = componentCount < SHAPE_MAX_COMPONENTS ? componentCount : SHAPE_MAX_COMPONENTS - 1;
            if (componentCount < SHAPE_MAX_COMPONENTS) {
                componentCount++;
            }

            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            componentBySubcell[start] = (byte) componentId;

            while (head < tail) {
                final int cur = queue[head++];
                final int sx = cur % SHAPE_SUBCELL_RES;
                final int t = cur / SHAPE_SUBCELL_RES;
                final int sy = t % SHAPE_SUBCELL_RES;
                final int sz = t / SHAPE_SUBCELL_RES;

                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx - 1, sy, sz);
                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx + 1, sy, sz);
                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx, sy - 1, sz);
                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx, sy + 1, sz);
                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx, sy, sz - 1);
                tail = tryEnqueue(queue, tail, occupancyMask, componentBySubcell, componentId, sx, sy, sz + 1);
            }
        }

        final long[] faceComponentMask = new long[6];
        for (int face = 0; face < 6; face++) {
            for (int v = 0; v < SHAPE_SUBCELL_RES; v++) {
                for (int u = 0; u < SHAPE_SUBCELL_RES; u++) {
                    final int component = componentBySubcell[faceSampleSubcellIndex(face, u, v)];
                    if (component >= 0) {
                        faceComponentMask[face] |= 1L << component;
                    }
                }
            }
        }

        return new OverlayShapeTemplate(occupancyMask, componentBySubcell, faceComponentMask, true, false);
    }

    static int countAccessibleSolidInterfacesForBoxes(final List<AABB> boxes, final int entryFace, final int normalFace) {
        final OverlayShapeTemplate template = buildOverlayShapeTemplate(boxes);
        final long accessibleMask = template.faceComponentMask[entryFace];
        if (!template.hasOpenVolume || accessibleMask == 0L) {
            return 0;
        }

        int count = 0;
        for (int sz = 0; sz < SHAPE_SUBCELL_RES; sz++) {
            for (int sy = 0; sy < SHAPE_SUBCELL_RES; sy++) {
                for (int sx = 0; sx < SHAPE_SUBCELL_RES; sx++) {
                    final int subIdx = subcellIndex(sx, sy, sz);
                    final int component = template.componentBySubcell[subIdx];
                    if (!componentMaskContains(accessibleMask, component)) {
                        continue;
                    }

                    count += switch (normalFace) {
                        case SHAPE_FACE_POS_X -> sx > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx - 1, sy, sz)) ? 1 : 0;
                        case SHAPE_FACE_NEG_X -> sx + 1 < SHAPE_SUBCELL_RES &&
                            subcellSolid(template.occupancyMask, subcellIndex(sx + 1, sy, sz)) ? 1 : 0;
                        case SHAPE_FACE_POS_Y -> sy > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx, sy - 1, sz)) ? 1 : 0;
                        case SHAPE_FACE_NEG_Y -> sy + 1 < SHAPE_SUBCELL_RES &&
                            subcellSolid(template.occupancyMask, subcellIndex(sx, sy + 1, sz)) ? 1 : 0;
                        case SHAPE_FACE_POS_Z -> sz > 0 && subcellSolid(template.occupancyMask, subcellIndex(sx, sy, sz - 1)) ? 1 : 0;
                        default -> sz + 1 < SHAPE_SUBCELL_RES &&
                            subcellSolid(template.occupancyMask, subcellIndex(sx, sy, sz + 1)) ? 1 : 0;
                    };
                }
            }
        }
        return count;
    }

    private static int tryEnqueue(final int[] queue, final int tail, final long[] occupancyMask, final byte[] componentBySubcell,
        final int componentId, final int sx, final int sy, final int sz) {
        if (sx < 0 || sx >= SHAPE_SUBCELL_RES || sy < 0 || sy >= SHAPE_SUBCELL_RES || sz < 0 || sz >= SHAPE_SUBCELL_RES) {
            return tail;
        }

        final int subIdx = subcellIndex(sx, sy, sz);
        if (subcellSolid(occupancyMask, subIdx) || componentBySubcell[subIdx] != SHAPE_COMPONENT_UNASSIGNED) {
            return tail;
        }

        componentBySubcell[subIdx] = (byte) componentId;
        queue[tail] = subIdx;
        return tail + 1;
    }

    private static boolean isSolidAt(final List<AABB> boxes, final double x, final double y, final double z) {
        for (final AABB box : boxes) {
            if (x >= box.minX - SURFACE_EPS && x <= box.maxX + SURFACE_EPS &&
                y >= box.minY - SURFACE_EPS && y <= box.maxY + SURFACE_EPS &&
                z >= box.minZ - SURFACE_EPS && z <= box.maxZ + SURFACE_EPS) {
                return true;
            }
        }
        return false;
    }

    private static double shapeBlockingScore(final VoxelShape shape) {
        if (shape.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.0;
        for (final AABB box : shape.toAabbs()) {
            final double dx = Mth.clamp(box.maxX - box.minX, 0.0, 1.0);
            final double dy = Mth.clamp(box.maxY - box.minY, 0.0, 1.0);
            final double dz = Mth.clamp(box.maxZ - box.minZ, 0.0, 1.0);
            score += dx * dy * dz;
        }
        return score;
    }

    private static VoxelShape resolveOverlayShape(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos,
        final BlockState state) {
        final VoxelShape collision = state.getCollisionShape(level, pos);
        final VoxelShape occlusion = state.getOcclusionShape(level, pos);
        final VoxelShape union;
        if (collision.isEmpty() && occlusion.isEmpty()) {
            union = Shapes.empty();
        } else if (collision.isEmpty()) {
            union = occlusion;
        } else if (occlusion.isEmpty()) {
            union = collision;
        } else {
            union = Shapes.or(collision, occlusion);
        }

        VoxelShape best = Shapes.empty();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (final VoxelShape candidate : new VoxelShape[] {collision, occlusion, union}) {
            final double score = shapeBlockingScore(candidate);
            if (score > bestScore + 1.0E-9) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static @Nullable AABB getOverlayShapeBounds(final net.minecraft.client.multiplayer.ClientLevel level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        final VoxelShape shape = resolveOverlayShape(level, pos, state);
        if (shape.isEmpty()) {
            return null;
        }

        final AABB bounds = shape.bounds();
        if (bounds.getXsize() <= SURFACE_EPS || bounds.getYsize() <= SURFACE_EPS || bounds.getZsize() <= SURFACE_EPS) {
            return null;
        }
        return bounds;
    }

    private static int emitClippedPolygonAsQuads(final VertexConsumer consumer, final Matrix4f matrix, final TextureAtlasSprite sprite,
        final float[] inX, final float[] inY, final float[] inZ, final float[] inU, final float[] inV, final int inCount,
        final float[] outX, final float[] outY, final float[] outZ, final float[] outU, final float[] outV, final double m01,
        final double m11, final double m21, final double tY, final double fluidSurfaceY, final float nx, final float ny,
        final float nz, final float r, final float g, final float b, final float a) {
        final int outCount = clipToSurfaceY(inX, inY, inZ, inU, inV, inCount, outX, outY, outZ, outU, outV, m01, m11, m21, tY,
            fluidSurfaceY);
        if (outCount < 3) return 0;

        if (outCount == 4) {
            vertex(consumer, matrix, sprite, outX[0], outY[0], outZ[0], outU[0], outV[0], nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[1], outY[1], outZ[1], outU[1], outV[1], nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[2], outY[2], outZ[2], outU[2], outV[2], nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[3], outY[3], outZ[3], outU[3], outV[3], nx, ny, nz, r, g, b, a);
            return 1;
        }

        int quadsEmitted = 0;
        final float x0 = outX[0];
        final float y0 = outY[0];
        final float z0 = outZ[0];
        final float u0 = outU[0];
        final float v0 = outV[0];
        for (int i = 1; i + 1 < outCount; i++) {
            vertex(consumer, matrix, sprite, x0, y0, z0, u0, v0, nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[i], outY[i], outZ[i], outU[i], outV[i], nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[i + 1], outY[i + 1], outZ[i + 1], outU[i + 1], outV[i + 1], nx, ny, nz, r, g, b, a);
            vertex(consumer, matrix, sprite, outX[i + 1], outY[i + 1], outZ[i + 1], outU[i + 1], outV[i + 1], nx, ny, nz, r, g, b, a);
            quadsEmitted++;
        }
        return quadsEmitted;
    }

    static int clipToSurfaceY(final float[] inX, final float[] inY, final float[] inZ, final float[] inU, final float[] inV,
        final int inCount, final float[] outX, final float[] outY, final float[] outZ, final float[] outU, final float[] outV,
        final double m01, final double m11, final double m21, final double tY, final double fluidSurfaceY) {
        int outCount = 0;

        float prevX = inX[inCount - 1];
        float prevY = inY[inCount - 1];
        float prevZ = inZ[inCount - 1];
        float prevU = inU[inCount - 1];
        float prevV = inV[inCount - 1];
        double prevWorldY = worldY(m01, m11, m21, tY, prevX, prevY, prevZ);
        boolean prevInside = prevWorldY <= fluidSurfaceY + SURFACE_EPS;

        for (int i = 0; i < inCount; i++) {
            final float curX = inX[i];
            final float curY = inY[i];
            final float curZ = inZ[i];
            final float curU = inU[i];
            final float curV = inV[i];
            final double curWorldY = worldY(m01, m11, m21, tY, curX, curY, curZ);
            final boolean curInside = curWorldY <= fluidSurfaceY + SURFACE_EPS;

            if (prevInside && curInside) {
                outX[outCount] = curX;
                outY[outCount] = curY;
                outZ[outCount] = curZ;
                outU[outCount] = curU;
                outV[outCount] = curV;
                outCount++;
            } else if (prevInside && !curInside) {
                final double denom = curWorldY - prevWorldY;
                if (Math.abs(denom) > 1.0E-12) {
                    final float t = (float) Mth.clamp((fluidSurfaceY - prevWorldY) / denom, 0.0, 1.0);
                    outX[outCount] = Mth.lerp(t, prevX, curX);
                    outY[outCount] = Mth.lerp(t, prevY, curY);
                    outZ[outCount] = Mth.lerp(t, prevZ, curZ);
                    outU[outCount] = Mth.lerp(t, prevU, curU);
                    outV[outCount] = Mth.lerp(t, prevV, curV);
                    outCount++;
                }
            } else if (!prevInside && curInside) {
                final double denom = curWorldY - prevWorldY;
                if (Math.abs(denom) > 1.0E-12) {
                    final float t = (float) Mth.clamp((fluidSurfaceY - prevWorldY) / denom, 0.0, 1.0);
                    outX[outCount] = Mth.lerp(t, prevX, curX);
                    outY[outCount] = Mth.lerp(t, prevY, curY);
                    outZ[outCount] = Mth.lerp(t, prevZ, curZ);
                    outU[outCount] = Mth.lerp(t, prevU, curU);
                    outV[outCount] = Mth.lerp(t, prevV, curV);
                    outCount++;
                }

                outX[outCount] = curX;
                outY[outCount] = curY;
                outZ[outCount] = curZ;
                outU[outCount] = curU;
                outV[outCount] = curV;
                outCount++;
            }

            prevX = curX;
            prevY = curY;
            prevZ = curZ;
            prevU = curU;
            prevV = curV;
            prevWorldY = curWorldY;
            prevInside = curInside;
        }

        return outCount;
    }

    static float scaleOverlayUv(final float coordinate) {
        return coordinate * OVERLAY_UV_SCALE;
    }

    private static void vertex(final VertexConsumer consumer, final Matrix4f matrix, final TextureAtlasSprite sprite, final float x,
        final float y, final float z, final float u, final float v, final float nx, final float ny, final float nz, final float r,
        final float g, final float b, final float a) {
        final float scaledU = scaleOverlayUv(u);
        final float scaledV = scaleOverlayUv(v);
        consumer.vertex(matrix, x, y, z)
            .color(r, g, b, a)
            .uv(Mth.lerp(scaledU, sprite.getU0(), sprite.getU1()), Mth.lerp(scaledV, sprite.getV0(), sprite.getV1()))
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(FULL_BRIGHT)
            .normal(nx, ny, nz)
            .endVertex();
    }

    private static void quad(final VertexConsumer consumer, final Matrix4f matrix, final TextureAtlasSprite sprite, final float x0,
        final float y0, final float z0, final float x1, final float y1, final float z1, final float x2, final float y2,
        final float z2, final float x3, final float y3, final float z3, final float u0, final float v0, final float u1,
        final float v1, final float nx, final float ny, final float nz, final float r, final float g, final float b,
        final float a) {
        vertex(consumer, matrix, sprite, x0, y0, z0, u0, v0, nx, ny, nz, r, g, b, a);
        vertex(consumer, matrix, sprite, x1, y1, z1, u1, v0, nx, ny, nz, r, g, b, a);
        vertex(consumer, matrix, sprite, x2, y2, z2, u1, v1, nx, ny, nz, r, g, b, a);
        vertex(consumer, matrix, sprite, x3, y3, z3, u0, v1, nx, ny, nz, r, g, b, a);
    }

    private static List<LoadedShip> selectClosestShips(final net.minecraft.client.multiplayer.ClientLevel level, final Vec3 cameraPos,
        final Vec3 cameraView, final double maxDistanceBlocks, final double minViewDot, final int maxCount) {
        final List<LoadedShip> candidates = new ArrayList<>();
        for (final LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            final AABBdc shipWorldAabb = getShipWorldAabb(ship);
            if (!isShipWithinOverlayView(cameraPos, cameraView, shipWorldAabb, maxDistanceBlocks, minViewDot)) {
                continue;
            }
            candidates.add(ship);
        }

        candidates.sort(Comparator.comparingDouble(ship -> distanceSqToShipAabb(cameraPos, ship)));
        if (candidates.size() > maxCount) {
            return candidates.subList(0, maxCount);
        }
        return candidates;
    }

    private static double distanceSqToShipAabb(final Vec3 cameraPos, final LoadedShip ship) {
        final AABBdc shipWorldAabb = getShipWorldAabb(ship);
        if (shipWorldAabb == null) return Double.POSITIVE_INFINITY;

        final double closestX = Mth.clamp(cameraPos.x, shipWorldAabb.minX(), shipWorldAabb.maxX());
        final double closestY = Mth.clamp(cameraPos.y, shipWorldAabb.minY(), shipWorldAabb.maxY());
        final double closestZ = Mth.clamp(cameraPos.z, shipWorldAabb.minZ(), shipWorldAabb.maxZ());
        final double dx = closestX - cameraPos.x;
        final double dy = closestY - cameraPos.y;
        final double dz = closestZ - cameraPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    static boolean isShipWithinOverlayView(final Vec3 cameraPos, final Vec3 cameraView, final @Nullable AABBdc shipWorldAabb,
        final double maxDistanceBlocks, final double minViewDot) {
        if (shipWorldAabb == null) return false;

        final double closestX = Mth.clamp(cameraPos.x, shipWorldAabb.minX(), shipWorldAabb.maxX());
        final double closestY = Mth.clamp(cameraPos.y, shipWorldAabb.minY(), shipWorldAabb.maxY());
        final double closestZ = Mth.clamp(cameraPos.z, shipWorldAabb.minZ(), shipWorldAabb.maxZ());
        final double dx = closestX - cameraPos.x;
        final double dy = closestY - cameraPos.y;
        final double dz = closestZ - cameraPos.z;
        final double distanceSqToAabb = dx * dx + dy * dy + dz * dz;
        if (distanceSqToAabb <= 1.0E-6) return true;

        final double centerX = (shipWorldAabb.minX() + shipWorldAabb.maxX()) * 0.5;
        final double centerY = (shipWorldAabb.minY() + shipWorldAabb.maxY()) * 0.5;
        final double centerZ = (shipWorldAabb.minZ() + shipWorldAabb.maxZ()) * 0.5;
        final double halfX = (shipWorldAabb.maxX() - shipWorldAabb.minX()) * 0.5;
        final double halfY = (shipWorldAabb.maxY() - shipWorldAabb.minY()) * 0.5;
        final double halfZ = (shipWorldAabb.maxZ() - shipWorldAabb.minZ()) * 0.5;
        final double radius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

        final double maxDistance = Math.max(maxDistanceBlocks, radius + OVERLAY_SHIP_DISTANCE_MARGIN_BLOCKS);
        if (distanceSqToAabb > maxDistance * maxDistance) return false;

        if (distanceSqToAabb <= OVERLAY_NEAR_VIEW_CULL_DISTANCE_BLOCKS * OVERLAY_NEAR_VIEW_CULL_DISTANCE_BLOCKS) {
            return true;
        }

        final double toCenterX = centerX - cameraPos.x;
        final double toCenterY = centerY - cameraPos.y;
        final double toCenterZ = centerZ - cameraPos.z;
        final double centerDistanceSq = toCenterX * toCenterX + toCenterY * toCenterY + toCenterZ * toCenterZ;
        if (centerDistanceSq <= 1.0E-6) return true;

        final double centerDistance = Math.sqrt(centerDistanceSq);
        final double dot = cameraView.x * toCenterX + cameraView.y * toCenterY + cameraView.z * toCenterZ;
        return dot >= minViewDot * centerDistance - radius;
    }

    static double getMaxOverlayShipDistanceBlocks(final Minecraft mc) {
        final int renderDistanceChunks = mc.options != null ? mc.options.renderDistance().get() : 0;
        if (renderDistanceChunks <= 0) return MAX_OVERLAY_SHIP_DISTANCE_BLOCKS_FALLBACK;
        return renderDistanceChunks * 16.0 + OVERLAY_SHIP_DISTANCE_MARGIN_BLOCKS;
    }

    static double getOverlayViewMinDot(final Minecraft mc) {
        final double configuredFov = mc.options != null ? mc.options.fov().get() : 70.0;
        final double halfAngleRadians = Math.toRadians(Math.min(170.0, configuredFov + OVERLAY_VIEW_ANGLE_MARGIN_DEGREES) * 0.5);
        return Math.cos(halfAngleRadians);
    }

    private static @Nullable AABBdc getShipWorldAabb(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return clientShip.getRenderAABB();
        }
        return ship.getWorldAABB();
    }

    private static ShipTransform getShipTransform(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return clientShip.getRenderTransform();
        }
        return ship.getShipTransform();
    }
}
