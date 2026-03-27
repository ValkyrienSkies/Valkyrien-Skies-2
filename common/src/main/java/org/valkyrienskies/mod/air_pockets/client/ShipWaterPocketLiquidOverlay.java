package org.valkyrienskies.mod.air_pockets.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
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

    private static final float OVERLAY_ALPHA = 0.75f;
    private static final float FACE_EPS = 0.0025f;
    private static final float OVERLAY_UV_SCALE = 0.5f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final double SURFACE_EPS = 1.0E-5;

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

    private static final Map<Long, ShipCache> SHIP_CACHE = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastSurfaceCacheLevel = null;
    private static final Long2ObjectOpenHashMap<FluidSurfaceSample> FLUID_SURFACE_CACHE = new Long2ObjectOpenHashMap<>();

    public static void clear() {
        SHIP_CACHE.clear();
        lastSurfaceCacheLevel = null;
        FLUID_SURFACE_CACHE.clear();
    }

    public static void render(final double camX, final double camY, final double camZ) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return;

        final Minecraft mc = Minecraft.getInstance();
        final var level = mc.level;
        if (level == null || mc.gameRenderer == null) return;

        final Camera camera = mc.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;

        if (lastSurfaceCacheLevel != level) {
            lastSurfaceCacheLevel = level;
            FLUID_SURFACE_CACHE.clear();
        }

        final Vec3 cameraPos = new Vec3(camX, camY, camZ);
        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, MAX_SHIPS);
        if (ships.isEmpty()) return;

        final MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
        final VertexConsumer consumer = bufferSource.getBuffer(getOverlayRenderType());

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

    private static RenderType getOverlayRenderType() {
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
                    }

                    idx++;
                }
            }
        }

        cache.overlaySolids = overlaySolids;
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

        final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();

        final int strideY = sizeX;
        final int strideZ = sizeX * sizeY;
        int quadsEmitted = 0;

        for (int outsideIdx = open.nextSetBit(0); outsideIdx >= 0; outsideIdx = open.nextSetBit(outsideIdx + 1)) {
            if (outsideIdx >= volume) break;
            if (interior.get(outsideIdx)) continue;
            if (!touchesOverlayBoundary(open, interior, open, overlaySolids, outsideIdx, sizeX, sizeY, sizeZ)) continue;

            final int lx = outsideIdx % sizeX;
            final int t = outsideIdx / sizeX;
            final int ly = t % sizeY;
            final int lz = t / sizeY;

            final double centerX = lx + 0.5;
            final double centerY = ly + 0.5;
            final double centerZ = lz + 0.5;

            final double worldX = m00 * centerX + m10 * centerY + m20 * centerZ + tX;
            final double worldY = m01 * centerX + m11 * centerY + m21 * centerZ + tY;
            final double worldZ = m02 * centerX + m12 * centerY + m22 * centerZ + tZ;

            final FluidSurfaceSample surface = findExteriorFluidSurface(level, fluidPos, scanPos, worldX, worldY, worldZ);
            if (surface == null) continue;
            final Fluid overlayFluid = chooseOverlayFluid(surface != null ? surface.fluid : null, snapshot.getFloodFluid());
            final FluidState overlayState = surface != null ? surface.fluidState : overlayFluid.defaultFluidState();
            final BlockPos tintPos = surface != null ? surface.pos : fluidPos.set(Mth.floor(worldX), Mth.floor(worldY), Mth.floor(worldZ));
            final ShipWaterPocketFluidVisualHelper.FluidVisual visual =
                ShipWaterPocketFluidVisualHelper.resolveFluidVisual(level, tintPos, overlayState, overlayFluid);
            final float r = ((visual.getTintRgb() >> 16) & 0xFF) / 255.0f;
            final float g = ((visual.getTintRgb() >> 8) & 0xFF) / 255.0f;
            final float b = (visual.getTintRgb() & 0xFF) / 255.0f;
            final double surfaceY = surface != null ? surface.surfaceY : Double.NaN;

            if (lx > 0) {
                final int n = outsideIdx - 1;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceXClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceXClipped(matrix, consumer, lx, ly, lz, +1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
            if (lx + 1 < sizeX) {
                final int n = outsideIdx + 1;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceXClipped(matrix, consumer, lx + 1, ly, lz, -1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceXClipped(matrix, consumer, lx + 1, ly, lz, -1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
            if (ly > 0) {
                final int n = outsideIdx - strideY;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly, lz, +1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
            if (ly + 1 < sizeY) {
                final int n = outsideIdx + strideY;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly + 1, lz, -1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceYClipped(matrix, consumer, lx, ly + 1, lz, -1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
            if (lz > 0) {
                final int n = outsideIdx - strideZ;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz, +1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz, +1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
            if (lz + 1 < sizeZ) {
                final int n = outsideIdx + strideZ;
                if (isInteriorOpen(open, interior, n)) {
                    quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz + 1, -1.0f, false, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                } else if (!open.get(n) && overlaySolids != null && overlaySolids.get(n)) {
                    quadsEmitted += emitFaceZClipped(matrix, consumer, lx, ly, lz + 1, -1.0f, true, m01, m11, m21, tY, surfaceY,
                        visual.getSprite(), r, g, b, OVERLAY_ALPHA);
                }
            }
        }

        return quadsEmitted;
    }

    static boolean isOutsideSubmergedFluid(final BitSet open, final BitSet interior, final BitSet waterReachable, final int idx) {
        return open.get(idx) && !interior.get(idx) && waterReachable.get(idx);
    }

    static boolean isInteriorOpen(final BitSet open, final BitSet interior, final int idx) {
        return open.get(idx) && interior.get(idx);
    }

    static boolean touchesOverlayBoundary(final BitSet open, final BitSet interior, final BitSet waterReachable,
        final @Nullable BitSet overlaySolids, final int outsideIdx, final int sizeX, final int sizeY, final int sizeZ) {
        final int lx = outsideIdx % sizeX;
        final int t = outsideIdx / sizeX;
        final int ly = t % sizeY;
        final int lz = t / sizeY;
        final int strideY = sizeX;
        final int strideZ = sizeX * sizeY;

        boolean needsOverlay = false;
        if (lx > 0) {
            final int n = outsideIdx - 1;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        if (!needsOverlay && lx + 1 < sizeX) {
            final int n = outsideIdx + 1;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        if (!needsOverlay && ly > 0) {
            final int n = outsideIdx - strideY;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        if (!needsOverlay && ly + 1 < sizeY) {
            final int n = outsideIdx + strideY;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        if (!needsOverlay && lz > 0) {
            final int n = outsideIdx - strideZ;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        if (!needsOverlay && lz + 1 < sizeZ) {
            final int n = outsideIdx + strideZ;
            needsOverlay |= isInteriorOpen(open, interior, n) || (!open.get(n) && overlaySolids != null && overlaySolids.get(n));
        }
        return needsOverlay;
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
        final int blockX = Mth.floor(worldX);
        final int blockY = Mth.floor(worldY);
        final int blockZ = Mth.floor(worldZ);

        fluidPos.set(blockX, blockY, blockZ);
        FluidState sampleState = level.getFluidState(fluidPos);
        if (sampleState.isEmpty()) {
            fluidPos.move(0, -1, 0);
            sampleState = level.getFluidState(fluidPos);
            if (sampleState.isEmpty()) {
                fluidPos.move(0, 2, 0);
                sampleState = level.getFluidState(fluidPos);
                if (sampleState.isEmpty()) {
                    return null;
                }
            }
        }

        final long key = BlockPos.asLong(fluidPos.getX(), fluidPos.getY(), fluidPos.getZ());
        final FluidSurfaceSample cached = FLUID_SURFACE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        final Fluid canonicalFluid = canonicalSource(sampleState.getType());
        scanPos.set(fluidPos);
        final int maxYExclusive = level.getMaxBuildHeight();
        while (scanPos.getY() < maxYExclusive) {
            final FluidState current = level.getFluidState(scanPos);
            if (current.isEmpty() || canonicalSource(current.getType()) != canonicalFluid) break;
            scanPos.move(0, 1, 0);
        }
        scanPos.move(0, -1, 0);

        final FluidState topFluid = level.getFluidState(scanPos);
        if (topFluid.isEmpty()) return null;

        final FluidSurfaceSample sample = new FluidSurfaceSample(
            canonicalFluid,
            topFluid,
            scanPos.immutable(),
            scanPos.getY() + topFluid.getHeight(level, scanPos)
        );
        if (FLUID_SURFACE_CACHE.size() >= MAX_FLUID_SURFACE_CACHE) {
            FLUID_SURFACE_CACHE.clear();
        }
        FLUID_SURFACE_CACHE.put(key, sample);
        return sample;
    }

    private static double worldY(final double m01, final double m11, final double m21, final double tY, final float x,
        final float y, final float z) {
        return m01 * x + m11 * y + m21 * z + tY;
    }

    private static int emitFaceXClipped(final Matrix4f matrix, final VertexConsumer consumer, final int xPlane, final int y0,
        final int z0, final float normalX, final boolean biasTowardNormal, final double m01, final double m11, final double m21,
        final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float x = xPlane + (biasTowardNormal ? FACE_EPS * normalX : -FACE_EPS * normalX);
        final float y1 = y0 + 1.0f;
        final float z1 = z0 + 1.0f;

        final double wy0 = worldY(m01, m11, m21, tY, x, y0, z0);
        final double wy1 = worldY(m01, m11, m21, tY, x, y1, z0);
        final double wy2 = worldY(m01, m11, m21, tY, x, y1, z1);
        final double wy3 = worldY(m01, m11, m21, tY, x, y0, z1);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x, y0, z0, x, y1, z0, x, y1, z1, x, y0, z1, 0.0f, 0.0f, 1.0f, 1.0f, normalX, 0.0f, 0.0f,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x;
        CLIP_Y0[0] = y0;
        CLIP_Z0[0] = z0;
        CLIP_U0[0] = 0.0f;
        CLIP_V0[0] = 0.0f;
        CLIP_X0[1] = x;
        CLIP_Y0[1] = y1;
        CLIP_Z0[1] = z0;
        CLIP_U0[1] = 1.0f;
        CLIP_V0[1] = 0.0f;
        CLIP_X0[2] = x;
        CLIP_Y0[2] = y1;
        CLIP_Z0[2] = z1;
        CLIP_U0[2] = 1.0f;
        CLIP_V0[2] = 1.0f;
        CLIP_X0[3] = x;
        CLIP_Y0[3] = y0;
        CLIP_Z0[3] = z1;
        CLIP_U0[3] = 0.0f;
        CLIP_V0[3] = 1.0f;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, normalX, 0.0f, 0.0f, r, g, b, a);
    }

    private static int emitFaceYClipped(final Matrix4f matrix, final VertexConsumer consumer, final int x0, final int yPlane,
        final int z0, final float normalY, final boolean biasTowardNormal, final double m01, final double m11, final double m21,
        final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float y = yPlane + (biasTowardNormal ? FACE_EPS * normalY : -FACE_EPS * normalY);
        final float x1 = x0 + 1.0f;
        final float z1 = z0 + 1.0f;

        final double wy0 = worldY(m01, m11, m21, tY, x0, y, z0);
        final double wy1 = worldY(m01, m11, m21, tY, x1, y, z0);
        final double wy2 = worldY(m01, m11, m21, tY, x1, y, z1);
        final double wy3 = worldY(m01, m11, m21, tY, x0, y, z1);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, normalY, 0.0f,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x0;
        CLIP_Y0[0] = y;
        CLIP_Z0[0] = z0;
        CLIP_U0[0] = 0.0f;
        CLIP_V0[0] = 0.0f;
        CLIP_X0[1] = x1;
        CLIP_Y0[1] = y;
        CLIP_Z0[1] = z0;
        CLIP_U0[1] = 1.0f;
        CLIP_V0[1] = 0.0f;
        CLIP_X0[2] = x1;
        CLIP_Y0[2] = y;
        CLIP_Z0[2] = z1;
        CLIP_U0[2] = 1.0f;
        CLIP_V0[2] = 1.0f;
        CLIP_X0[3] = x0;
        CLIP_Y0[3] = y;
        CLIP_Z0[3] = z1;
        CLIP_U0[3] = 0.0f;
        CLIP_V0[3] = 1.0f;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, 0.0f, normalY, 0.0f, r, g, b, a);
    }

    private static int emitFaceZClipped(final Matrix4f matrix, final VertexConsumer consumer, final int x0, final int y0,
        final int zPlane, final float normalZ, final boolean biasTowardNormal, final double m01, final double m11,
        final double m21, final double tY,
        final double fluidSurfaceY, final TextureAtlasSprite sprite, final float r, final float g, final float b, final float a) {
        final float z = zPlane + (biasTowardNormal ? FACE_EPS * normalZ : -FACE_EPS * normalZ);
        final float x1 = x0 + 1.0f;
        final float y1 = y0 + 1.0f;

        final double wy0 = worldY(m01, m11, m21, tY, x0, y0, z);
        final double wy1 = worldY(m01, m11, m21, tY, x1, y0, z);
        final double wy2 = worldY(m01, m11, m21, tY, x1, y1, z);
        final double wy3 = worldY(m01, m11, m21, tY, x0, y1, z);

        if (Double.isNaN(fluidSurfaceY) || Math.max(Math.max(wy0, wy1), Math.max(wy2, wy3)) <= fluidSurfaceY + SURFACE_EPS) {
            quad(consumer, matrix, sprite, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, normalZ,
                r, g, b, a);
            return 1;
        }
        if (Math.min(Math.min(wy0, wy1), Math.min(wy2, wy3)) > fluidSurfaceY + SURFACE_EPS) return 0;

        CLIP_X0[0] = x0;
        CLIP_Y0[0] = y0;
        CLIP_Z0[0] = z;
        CLIP_U0[0] = 0.0f;
        CLIP_V0[0] = 0.0f;
        CLIP_X0[1] = x1;
        CLIP_Y0[1] = y0;
        CLIP_Z0[1] = z;
        CLIP_U0[1] = 1.0f;
        CLIP_V0[1] = 0.0f;
        CLIP_X0[2] = x1;
        CLIP_Y0[2] = y1;
        CLIP_Z0[2] = z;
        CLIP_U0[2] = 1.0f;
        CLIP_V0[2] = 1.0f;
        CLIP_X0[3] = x0;
        CLIP_Y0[3] = y1;
        CLIP_Z0[3] = z;
        CLIP_U0[3] = 0.0f;
        CLIP_V0[3] = 1.0f;

        return emitClippedPolygonAsQuads(consumer, matrix, sprite, CLIP_X0, CLIP_Y0, CLIP_Z0, CLIP_U0, CLIP_V0, 4,
            CLIP_X1, CLIP_Y1, CLIP_Z1, CLIP_U1, CLIP_V1, m01, m11, m21, tY, fluidSurfaceY, 0.0f, 0.0f, normalZ, r, g, b, a);
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
        final int maxCount) {
        final List<LoadedShip> candidates = new ArrayList<>();
        for (final LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
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
