package org.valkyrienskies.mod.common.fluid.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.Uniform;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.primitives.AABBdc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Per-ship air-pocket masks for the interior fog post-process. Mask bits live in a single shared
 * atlas texture and per-ship metadata (AABB, grid size, world-to-ship matrix, atlas row offset)
 * lives in a second {@code RGBA32F} texture with one row per ship. The shader reads both via
 * {@code texelFetch} and iterates over {@code ValkyrienAir_ShipCount} without any compile-time cap.
 *
 * <p>The remaining hard limit is the GL max-texture-height (≈16384), which bounds both the mask
 * atlas height (data-dependent — large interiors burn many rows each) and the metadata texture
 * height (≈one row per ship, so ~16k ships). Mask atlas overflow drops the tail ships from a frame
 * with a log warning; metadata overflow is far enough away that we don't track it explicitly.</p>
 */
public final class ShipWaterPocketExternalWaterCull {

    private ShipWaterPocketExternalWaterCull() {}

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir ShipWaterCull");

    private static final int SUB = 8;
    private static final int OCC_WORDS_PER_VOXEL = (SUB * SUB * SUB) / 32; // 512 bits / 32 = 16

    // Must match shader constants (power-of-two).
    private static final int MASK_TEX_WIDTH = 4096;

    // GL texture-height ceiling. We refuse to grow the atlas past this; ships that would push past
    // it are dropped from the slot list with a warning.
    private static final int ATLAS_MAX_HEIGHT = 16384;

    // Width of the per-ship metadata texture (RGBA32F texels per ship row).
    //   col 0 = aabbMin (xyz, _)
    //   col 1 = aabbMax (xyz, _)
    //   col 2 = (sizeX, sizeY, sizeZ, atlasRowOffset)
    //   col 3..6 = worldToShip mat4, one column per texel
    private static final int META_TEX_WIDTH = 7;
    private static final int META_FLOATS_PER_SHIP = META_TEX_WIDTH * 4;

    private static final long INTERIOR_VOLUME_DIAG_LOG_INTERVAL_MS = 3000L;

    private static ClientLevel lastLevel = null;
    private static long lastInteriorVolumeDiagLogAtMs = 0L;

    private static final class ShipMasks {
        private final long shipId;
        private long geometryRevision;

        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;

        private long lastMaskBuildRevision = Long.MIN_VALUE;

        private final Matrix4f worldToShip = new Matrix4f();

        private int[] maskData;
        private byte[] maskBytes;
        private ByteBuffer maskByteBuffer;
        private int maskWordCount;
        private int maskRowCount;
        private CompletableFuture<int[]> pendingMaskWordsFuture;
        private long pendingMaskBuildRevision = Long.MIN_VALUE;

        // Atlas placement: row index where this ship's mask currently lives in the shared atlas.
        // -1 means "not currently placed".
        private int atlasRowStart = -1;
        // Revision of the data uploaded at atlasRowStart. If this drifts from lastMaskBuildRevision
        // (or atlasRowStart drifts from the desired row), we reupload.
        private long atlasUploadRevision = Long.MIN_VALUE;

        private ShipMasks(final long shipId) {
            this.shipId = shipId;
        }

        private void close() {
            if (pendingMaskWordsFuture != null) {
                pendingMaskWordsFuture.cancel(true);
                pendingMaskWordsFuture = null;
            }
            atlasRowStart = -1;
            atlasUploadRevision = Long.MIN_VALUE;
        }
    }

    private static final Map<Long, ShipMasks> SHIP_MASKS = new HashMap<>();

    // Single shared mask atlas — every active ship's mask bits stacked vertically.
    private static int atlasTexId = 0;
    private static int atlasTexHeight = 0;

    // Per-ship metadata texture — RGBA32F, META_TEX_WIDTH wide × (active ship count) tall.
    private static int metaTexId = 0;
    private static int metaTexHeight = 0;
    private static FloatBuffer metaUploadBuffer;
    private static int metaUploadBufferCapacityShips = 0;

    // Scratch lists reused across calls.
    private static final List<LoadedShip> ACTIVE_SHIPS = new ArrayList<>();
    private static final List<ShipMasks> ACTIVE_MASKS = new ArrayList<>();

    private static boolean isLiveTextureId(final int texId) {
        return texId != 0 && GL11.glIsTexture(texId);
    }

    public static void clear() {
        for (final ShipMasks masks : SHIP_MASKS.values()) {
            masks.close();
        }
        SHIP_MASKS.clear();

        if (isLiveTextureId(atlasTexId)) {
            TextureUtil.releaseTextureId(atlasTexId);
        }
        atlasTexId = 0;
        atlasTexHeight = 0;

        if (isLiveTextureId(metaTexId)) {
            TextureUtil.releaseTextureId(metaTexId);
        }
        metaTexId = 0;
        metaTexHeight = 0;

        lastLevel = null;
    }

    public static int bindInteriorVolumeSamplersAndUniforms(final EffectInstance effect, final ClientLevel level,
        final double cameraX, final double cameraY, final double cameraZ) {
        if (effect == null || level == null) return 0;

        RenderSystem.assertOnRenderThread();

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        final Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        final List<LoadedShip> candidates = collectShipsByDistance(level, cameraPos);

        // Pass 1: ensure mask data is built for each candidate and gather slots whose data is ready.
        ACTIVE_SHIPS.clear();
        ACTIVE_MASKS.clear();
        for (final LoadedShip ship : candidates) {
            final long shipId = ship.getId();
            final ShipFluidRenderSnapshot snapshot =
                ShipFluidRenderSnapshot.get(level, shipId);
            if (snapshot == null) continue;

            final ShipMasks masks = SHIP_MASKS.computeIfAbsent(shipId, ShipMasks::new);

            final int minX = snapshot.getMinX();
            final int minY = snapshot.getMinY();
            final int minZ = snapshot.getMinZ();
            final int sizeX = snapshot.getSizeX();
            final int sizeY = snapshot.getSizeY();
            final int sizeZ = snapshot.getSizeZ();
            final long geometryRevision = snapshot.getGeometryRevision();

            final boolean boundsChanged =
                masks.minX != minX || masks.minY != minY || masks.minZ != minZ ||
                    masks.sizeX != sizeX || masks.sizeY != sizeY || masks.sizeZ != sizeZ;

            if (boundsChanged || masks.geometryRevision != geometryRevision) {
                rebuildMaskBytes(level, masks, snapshot, minX, minY, minZ, sizeX, sizeY, sizeZ, geometryRevision);
            }
            applyPendingMaskBuild(masks, geometryRevision);

            if (masks.lastMaskBuildRevision != geometryRevision) continue;
            if (masks.maskRowCount <= 0 || masks.maskBytes == null) continue;

            ACTIVE_SHIPS.add(ship);
            ACTIVE_MASKS.add(masks);
        }

        // Pass 2: layout the mask atlas. Drop tail slots that would overflow ATLAS_MAX_HEIGHT.
        int totalRows = 0;
        int kept = 0;
        for (int i = 0; i < ACTIVE_MASKS.size(); i++) {
            final int next = totalRows + ACTIVE_MASKS.get(i).maskRowCount;
            if (next > ATLAS_MAX_HEIGHT) {
                LOGGER.warn(
                    "Atlas height limit reached at slot {} (need {} rows, max {}). Dropping {} ships from this frame.",
                    i, next, ATLAS_MAX_HEIGHT, ACTIVE_MASKS.size() - i
                );
                break;
            }
            totalRows = next;
            kept = i + 1;
        }
        if (kept < ACTIVE_MASKS.size()) {
            ACTIVE_MASKS.subList(kept, ACTIVE_MASKS.size()).clear();
            ACTIVE_SHIPS.subList(kept, ACTIVE_SHIPS.size()).clear();
        }

        if (totalRows == 0) {
            final Uniform shipCount = effect.getUniform("ValkyrienAir_ShipCount");
            if (shipCount != null) shipCount.set(0);
            // Atlas / meta samplers stay stale — the shader's loop does not run when ShipCount == 0.
            return 0;
        }

        if (!ensureAtlasTexture(totalRows)) {
            final Uniform shipCount = effect.getUniform("ValkyrienAir_ShipCount");
            if (shipCount != null) shipCount.set(0);
            return 0;
        }

        // Pass 3: bump-allocate row starts and re-upload mask data for each ship whose row or
        // revision changed.
        int currentRow = 0;
        for (int slot = 0; slot < ACTIVE_MASKS.size(); slot++) {
            final ShipMasks masks = ACTIVE_MASKS.get(slot);
            if (masks.atlasRowStart != currentRow || masks.atlasUploadRevision != masks.lastMaskBuildRevision) {
                uploadMaskToAtlas(masks, currentRow);
                masks.atlasRowStart = currentRow;
                masks.atlasUploadRevision = masks.lastMaskBuildRevision;
            }
            currentRow += masks.maskRowCount;
        }

        // Pass 4: pack metadata FloatBuffer (one row of META_FLOATS_PER_SHIP per ship) and upload.
        final int activeCount = ACTIVE_MASKS.size();
        ensureMetaUploadBufferCapacity(activeCount);
        ensureMetaTexture(activeCount);
        if (metaTexId == 0 || metaUploadBuffer == null) {
            final Uniform shipCount = effect.getUniform("ValkyrienAir_ShipCount");
            if (shipCount != null) shipCount.set(0);
            return 0;
        }

        metaUploadBuffer.clear();
        final boolean shouldLogDiag = shouldLogInteriorVolumeDiag();
        final StringBuilder diag = shouldLogDiag
            ? new StringBuilder("Interior volume effect bind camera=(")
                .append(formatDiagDouble(cameraX)).append(",")
                .append(formatDiagDouble(cameraY)).append(",")
                .append(formatDiagDouble(cameraZ)).append(")")
                .append(" atlasH=").append(atlasTexHeight)
                .append(" metaH=").append(metaTexHeight)
                .append(" active=").append(activeCount)
            : null;

        int boundShipCount = 0;
        for (int slot = 0; slot < activeCount; slot++) {
            final LoadedShip ship = ACTIVE_SHIPS.get(slot);
            final ShipMasks masks = ACTIVE_MASKS.get(slot);

            final AABBdc worldAabb = getShipWorldAabb(ship).orElse(null);
            if (worldAabb == null) {
                writeEmptyMetaRow(metaUploadBuffer);
                continue;
            }

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc worldToShipMatrix = shipTransform.getWorldToShip();
            final double biasedM30 = worldToShipMatrix.m30() - (double) masks.minX;
            final double biasedM31 = worldToShipMatrix.m31() - (double) masks.minY;
            final double biasedM32 = worldToShipMatrix.m32() - (double) masks.minZ;
            final double camShipX =
                worldToShipMatrix.m00() * cameraX + worldToShipMatrix.m10() * cameraY + worldToShipMatrix.m20() * cameraZ + biasedM30;
            final double camShipY =
                worldToShipMatrix.m01() * cameraX + worldToShipMatrix.m11() * cameraY + worldToShipMatrix.m21() * cameraZ + biasedM31;
            final double camShipZ =
                worldToShipMatrix.m02() * cameraX + worldToShipMatrix.m12() * cameraY + worldToShipMatrix.m22() * cameraZ + biasedM32;
            masks.worldToShip.set(
                (float) worldToShipMatrix.m00(), (float) worldToShipMatrix.m01(), (float) worldToShipMatrix.m02(), (float) worldToShipMatrix.m03(),
                (float) worldToShipMatrix.m10(), (float) worldToShipMatrix.m11(), (float) worldToShipMatrix.m12(), (float) worldToShipMatrix.m13(),
                (float) worldToShipMatrix.m20(), (float) worldToShipMatrix.m21(), (float) worldToShipMatrix.m22(), (float) worldToShipMatrix.m23(),
                (float) biasedM30, (float) biasedM31, (float) biasedM32, (float) worldToShipMatrix.m33()
            );

            // col 0: aabbMin
            metaUploadBuffer.put((float) worldAabb.minX());
            metaUploadBuffer.put((float) worldAabb.minY());
            metaUploadBuffer.put((float) worldAabb.minZ());
            metaUploadBuffer.put(0.0f);
            // col 1: aabbMax
            metaUploadBuffer.put((float) worldAabb.maxX());
            metaUploadBuffer.put((float) worldAabb.maxY());
            metaUploadBuffer.put((float) worldAabb.maxZ());
            metaUploadBuffer.put(0.0f);
            // col 2: (gridSize.xyz, atlasRowOffset)
            metaUploadBuffer.put((float) masks.sizeX);
            metaUploadBuffer.put((float) masks.sizeY);
            metaUploadBuffer.put((float) masks.sizeZ);
            metaUploadBuffer.put((float) masks.atlasRowStart);
            // col 3..6: worldToShip mat4, column-major (Matrix4f.get(FloatBuffer) writes that way)
            masks.worldToShip.get(metaUploadBuffer);
            metaUploadBuffer.position(metaUploadBuffer.position() + 16);

            boundShipCount++;

            if (shouldLogDiag) {
                appendInteriorVolumeDiag(
                    diag, slot, masks.shipId, worldAabb,
                    masks.sizeX, masks.sizeY, masks.sizeZ, masks.geometryRevision, masks,
                    cameraX, cameraY, cameraZ, camShipX, camShipY, camShipZ
                );
            }
        }

        metaUploadBuffer.flip();
        uploadMetaTexture(activeCount);

        // Always rebind the samplers — the suppliers read the texture ids dynamically so they
        // survive recreation across atlas / metadata grow events and level reloads.
        effect.setSampler("ValkyrienAir_MaskAtlas", () -> atlasTexId);
        effect.setSampler("ValkyrienAir_ShipMetaTex", () -> metaTexId);

        final Uniform shipCount = effect.getUniform("ValkyrienAir_ShipCount");
        if (shipCount != null) shipCount.set(boundShipCount);

        if (shouldLogDiag) {
            diag.append(" boundShips=").append(boundShipCount);
            LOGGER.info(diag.toString());
        }

        return boundShipCount;
    }

    private static void writeEmptyMetaRow(final FloatBuffer buf) {
        for (int i = 0; i < META_FLOATS_PER_SHIP; i++) {
            buf.put(0.0f);
        }
    }

    private static boolean shouldLogInteriorVolumeDiag() {
        final long now = System.currentTimeMillis();
        if (now - lastInteriorVolumeDiagLogAtMs < INTERIOR_VOLUME_DIAG_LOG_INTERVAL_MS) {
            return false;
        }
        lastInteriorVolumeDiagLogAtMs = now;
        return true;
    }

    private static void appendInteriorVolumeDiag(
        final StringBuilder diag,
        final int slot,
        final long shipId,
        final AABBdc worldAabb,
        final int sizeX,
        final int sizeY,
        final int sizeZ,
        final long geometryRevision,
        final ShipMasks masks,
        final double cameraX,
        final double cameraY,
        final double cameraZ,
        final double camShipX,
        final double camShipY,
        final double camShipZ
    ) {
        final boolean inAabb =
            cameraX >= worldAabb.minX() && cameraX <= worldAabb.maxX() &&
                cameraY >= worldAabb.minY() && cameraY <= worldAabb.maxY() &&
                cameraZ >= worldAabb.minZ() && cameraZ <= worldAabb.maxZ();
        final boolean inGrid =
            camShipX >= 0.0 && camShipY >= 0.0 && camShipZ >= 0.0 &&
                camShipX < sizeX && camShipY < sizeY && camShipZ < sizeZ;
        final int voxelX = Mth.floor(camShipX);
        final int voxelY = Mth.floor(camShipY);
        final int voxelZ = Mth.floor(camShipZ);
        final boolean cpuAir = inGrid && isAirVoxelSet(masks, voxelX, voxelY, voxelZ);
        final boolean cpuWaterReachable = inGrid && isWaterReachableVoxelSet(masks, voxelX, voxelY, voxelZ);
        final int nearbyWaterReachable = inGrid ? countNearbyWaterReachableVoxels(masks, voxelX, voxelY, voxelZ, 3) : 0;
        final RayProbeResult rayProbe = probeLookRayBoundary(masks, cameraX, cameraY, cameraZ);

        diag.append(" | slot=").append(slot)
            .append(" ship=").append(shipId)
            .append(" aabb=").append(inAabb)
            .append(" camShip=(")
            .append(formatDiagDouble(camShipX)).append(",")
            .append(formatDiagDouble(camShipY)).append(",")
            .append(formatDiagDouble(camShipZ)).append(")")
            .append(" voxel=(").append(voxelX).append(",").append(voxelY).append(",").append(voxelZ).append(")")
            .append(" inGrid=").append(inGrid)
            .append(" cpuAir=").append(cpuAir)
            .append(" cpuWater=").append(cpuWaterReachable)
            .append(" nearWater=").append(nearbyWaterReachable)
            .append(" rayExit=").append(rayProbe.exitFound)
            .append(" rayDist=").append(formatDiagDouble(rayProbe.exitDistance))
            .append(" rayVoxel=(").append(rayProbe.voxelX).append(",").append(rayProbe.voxelY).append(",").append(rayProbe.voxelZ).append(")")
            .append(" rayNearWater=").append(rayProbe.nearbyWaterCount)
            .append(" grid=(").append(sizeX).append(",").append(sizeY).append(",").append(sizeZ).append(")")
            .append(" atlasRow=").append(masks.atlasRowStart)
            .append(" rows=").append(masks.maskRowCount)
            .append(" geomRev=").append(geometryRevision);
    }

    private static RayProbeResult probeLookRayBoundary(final ShipMasks masks, final double cameraX, final double cameraY,
        final double cameraZ) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return RayProbeResult.EMPTY;
        }
        final Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
        final double lookLenSq = (double) look.x * look.x + (double) look.y * look.y + (double) look.z * look.z;
        if (lookLenSq <= 1.0e-6) {
            return RayProbeResult.EMPTY;
        }

        final double maxDistance = 64.0;
        final double step = 0.25;
        double lastInsideDistance = -1.0;
        int lastVoxelX = Integer.MIN_VALUE;
        int lastVoxelY = Integer.MIN_VALUE;
        int lastVoxelZ = Integer.MIN_VALUE;

        for (double travel = 0.0; travel <= maxDistance; travel += step) {
            final double worldX = cameraX + look.x * travel;
            final double worldY = cameraY + look.y * travel;
            final double worldZ = cameraZ + look.z * travel;
            final double shipX =
                masks.worldToShip.m00() * worldX + masks.worldToShip.m10() * worldY + masks.worldToShip.m20() * worldZ + masks.worldToShip.m30();
            final double shipY =
                masks.worldToShip.m01() * worldX + masks.worldToShip.m11() * worldY + masks.worldToShip.m21() * worldZ + masks.worldToShip.m31();
            final double shipZ =
                masks.worldToShip.m02() * worldX + masks.worldToShip.m12() * worldY + masks.worldToShip.m22() * worldZ + masks.worldToShip.m32();

            if (shipX < 0.0 || shipY < 0.0 || shipZ < 0.0 || shipX >= masks.sizeX || shipY >= masks.sizeY || shipZ >= masks.sizeZ) {
                break;
            }

            final int voxelX = Mth.floor(shipX);
            final int voxelY = Mth.floor(shipY);
            final int voxelZ = Mth.floor(shipZ);
            if (!isAirVoxelSet(masks, voxelX, voxelY, voxelZ)) {
                break;
            }

            lastInsideDistance = travel;
            lastVoxelX = voxelX;
            lastVoxelY = voxelY;
            lastVoxelZ = voxelZ;
        }

        if (lastInsideDistance < 0.0) {
            return RayProbeResult.EMPTY;
        }

        return new RayProbeResult(
            true,
            lastInsideDistance,
            lastVoxelX,
            lastVoxelY,
            lastVoxelZ,
            countNearbyWaterReachableVoxels(masks, lastVoxelX, lastVoxelY, lastVoxelZ, 3)
        );
    }

    private static boolean isAirVoxelSet(final ShipMasks masks, final int voxelX, final int voxelY, final int voxelZ) {
        if (masks.maskData == null) return false;
        if (voxelX < 0 || voxelY < 0 || voxelZ < 0) return false;
        if (voxelX >= masks.sizeX || voxelY >= masks.sizeY || voxelZ >= masks.sizeZ) return false;

        final int volume = masks.sizeX * masks.sizeY * masks.sizeZ;
        final int voxelIdx = voxelX + masks.sizeX * (voxelY + masks.sizeY * voxelZ);
        final int wordIndex = volume * OCC_WORDS_PER_VOXEL + (voxelIdx >> 5);
        if (wordIndex < 0 || wordIndex >= masks.maskData.length) return false;

        final int bit = voxelIdx & 31;
        final int word = masks.maskData[wordIndex];
        return ((word >>> bit) & 1) != 0;
    }

    private static boolean isWaterReachableVoxelSet(final ShipMasks masks, final int voxelX, final int voxelY, final int voxelZ) {
        if (masks.maskData == null) return false;
        if (voxelX < 0 || voxelY < 0 || voxelZ < 0) return false;
        if (voxelX >= masks.sizeX || voxelY >= masks.sizeY || voxelZ >= masks.sizeZ) return false;

        final int volume = masks.sizeX * masks.sizeY * masks.sizeZ;
        final int airWordCount = (volume + 31) >> 5;
        final int voxelIdx = voxelX + masks.sizeX * (voxelY + masks.sizeY * voxelZ);
        final int wordIndex = volume * OCC_WORDS_PER_VOXEL + airWordCount + (voxelIdx >> 5);
        if (wordIndex < 0 || wordIndex >= masks.maskData.length) return false;

        final int bit = voxelIdx & 31;
        final int word = masks.maskData[wordIndex];
        return ((word >>> bit) & 1) != 0;
    }

    private static int countNearbyWaterReachableVoxels(final ShipMasks masks, final int centerX, final int centerY, final int centerZ,
        final int radius) {
        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (isWaterReachableVoxelSet(masks, centerX + dx, centerY + dy, centerZ + dz)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private record RayProbeResult(boolean exitFound, double exitDistance, int voxelX, int voxelY, int voxelZ, int nearbyWaterCount) {
        private static final RayProbeResult EMPTY = new RayProbeResult(false, 0.0, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
    }

    private static String formatDiagDouble(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static List<LoadedShip> collectShipsByDistance(final ClientLevel level, final Vec3 cameraPos) {
        final List<LoadedShip> candidates = new ArrayList<>();
        for (final LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            candidates.add(ship);
        }
        candidates.sort(Comparator.comparingDouble(ship -> distanceSqToShipAabb(cameraPos, ship)));
        return candidates;
    }

    private static double distanceSqToShipAabb(final Vec3 cameraPos, final LoadedShip ship) {
        final AABBdc shipWorldAabbDc = getShipWorldAabb(ship).orElse(null);
        if (shipWorldAabbDc == null) return Double.POSITIVE_INFINITY;

        final double closestX = Mth.clamp(cameraPos.x, shipWorldAabbDc.minX(), shipWorldAabbDc.maxX());
        final double closestY = Mth.clamp(cameraPos.y, shipWorldAabbDc.minY(), shipWorldAabbDc.maxY());
        final double closestZ = Mth.clamp(cameraPos.z, shipWorldAabbDc.minZ(), shipWorldAabbDc.maxZ());
        final double dx = closestX - cameraPos.x;
        final double dy = closestY - cameraPos.y;
        final double dz = closestZ - cameraPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Optional<AABBdc> getShipWorldAabb(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return Optional.ofNullable(clientShip.getRenderAABB());
        }
        return Optional.ofNullable(ship.getWorldAABB());
    }

    private static ShipTransform getShipTransform(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return clientShip.getRenderTransform();
        }
        return ship.getShipTransform();
    }

    private static void rebuildMaskBytes(
        final ClientLevel level,
        final ShipMasks masks,
        final ShipFluidRenderSnapshot snapshot,
        final int minX,
        final int minY,
        final int minZ,
        final int sizeX,
        final int sizeY,
        final int sizeZ,
        final long geometryRevision
    ) {
        masks.geometryRevision = geometryRevision;
        masks.minX = minX;
        masks.minY = minY;
        masks.minZ = minZ;
        masks.sizeX = sizeX;
        masks.sizeY = sizeY;
        masks.sizeZ = sizeZ;

        final int volume = sizeX * sizeY * sizeZ;
        final int occWordCount = volume * OCC_WORDS_PER_VOXEL;
        final int airWordCount = (volume + 31) >> 5;
        final int waterWordCount = (volume + 31) >> 5;
        final int wordCount = occWordCount + airWordCount + waterWordCount;
        final int rowCount = Math.max(1, (wordCount + MASK_TEX_WIDTH - 1) / MASK_TEX_WIDTH);
        masks.maskWordCount = wordCount;
        masks.maskRowCount = rowCount;

        // Allocate (or grow) staging buffers. Capacity is rowCount * MASK_TEX_WIDTH so the last row
        // is fully covered with zeros for tail words past wordCount.
        final int wordCapacity = rowCount * MASK_TEX_WIDTH;
        final int byteCapacity = wordCapacity * 4;
        if (masks.maskData == null || masks.maskData.length != wordCapacity) {
            masks.maskData = new int[wordCapacity];
        } else {
            Arrays.fill(masks.maskData, 0);
        }
        if (masks.maskBytes == null || masks.maskBytes.length != byteCapacity) {
            masks.maskBytes = new byte[byteCapacity];
            masks.maskByteBuffer = BufferUtils.createByteBuffer(byteCapacity);
        } else {
            Arrays.fill(masks.maskBytes, (byte) 0);
        }
        // Force re-upload when the build finishes — staging buffers now hold a different shape.
        masks.atlasUploadRevision = Long.MIN_VALUE;
        masks.lastMaskBuildRevision = Long.MIN_VALUE;

        applyPendingMaskBuild(masks, geometryRevision);
        if (masks.lastMaskBuildRevision == geometryRevision) return;
        if (masks.pendingMaskWordsFuture != null && masks.pendingMaskBuildRevision == geometryRevision) return;

        final VoxelShape[] shapeSnapshot = new VoxelShape[volume];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int idx = 0;
        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++) {
                    pos.set(minX + lx, minY + ly, minZ + lz);
                    final BlockState state = level.getBlockState(pos);
                    shapeSnapshot[idx++] = ShipFluidCullBridge.buildCullVoxelShape(level, pos, state);
                }
            }
        }

        final BitSet interiorSnapshot =
            snapshot.getInterior() == null ? new BitSet() : (BitSet) snapshot.getInterior().clone();
        final BitSet openSnapshot =
            snapshot.getOpen() == null ? new BitSet() : (BitSet) snapshot.getOpen().clone();
        final BitSet waterReachableSnapshot =
            snapshot.getWaterReachable() == null ? new BitSet() : (BitSet) snapshot.getWaterReachable().clone();
        final BitSet submergedBoundarySnapshot = new BitSet(volume);
        for (int waterIdx = waterReachableSnapshot.nextSetBit(0); waterIdx >= 0 && waterIdx < volume;
             waterIdx = waterReachableSnapshot.nextSetBit(waterIdx + 1)) {
            if (!ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(openSnapshot, interiorSnapshot, waterReachableSnapshot, waterIdx)) {
                continue;
            }
            if (ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(
                openSnapshot,
                interiorSnapshot,
                waterReachableSnapshot,
                null,
                null,
                waterIdx,
                sizeX,
                sizeY,
                sizeZ
            )) {
                submergedBoundarySnapshot.set(waterIdx);
            }
        }

        final Supplier<int[]> task = () -> {
            final int[] occWords =
                ShipFluidCullMasks.buildOccMaskWords(shapeSnapshot, sizeX, sizeY, sizeZ, SUB);
            final int[] airWords = ShipFluidCullMasks.buildAirMaskWords(interiorSnapshot, volume);
            final int[] waterWords = ShipFluidCullMasks.buildAirMaskWords(submergedBoundarySnapshot, volume);
            final int[] out = new int[occWords.length + airWords.length + waterWords.length];
            System.arraycopy(occWords, 0, out, 0, occWords.length);
            System.arraycopy(airWords, 0, out, occWords.length, airWords.length);
            System.arraycopy(waterWords, 0, out, occWords.length + airWords.length, waterWords.length);
            return out;
        };

        final CompletableFuture<int[]> submitted = ShipFluidAsyncRuntime.trySubmit(task);
        if (submitted == null) {
            if (masks.pendingMaskWordsFuture != null) {
                masks.pendingMaskWordsFuture.cancel(true);
                masks.pendingMaskWordsFuture = null;
            }
            applyMaskWords(masks, task.get(), geometryRevision);
            return;
        }

        if (masks.pendingMaskWordsFuture != null) {
            masks.pendingMaskWordsFuture.cancel(true);
        }
        masks.pendingMaskWordsFuture = submitted;
        masks.pendingMaskBuildRevision = geometryRevision;
    }

    private static void applyPendingMaskBuild(final ShipMasks masks, final long currentGeometryRevision) {
        final CompletableFuture<int[]> pending = masks.pendingMaskWordsFuture;
        if (pending == null || !pending.isDone()) return;

        final long uploadRevision = masks.pendingMaskBuildRevision;
        masks.pendingMaskWordsFuture = null;
        if (uploadRevision != currentGeometryRevision) return;

        final int[] words;
        try {
            words = pending.join();
        } catch (final Throwable ignored) {
            return;
        }
        applyMaskWords(masks, words, uploadRevision);
    }

    private static void applyMaskWords(final ShipMasks masks, final int[] words, final long uploadRevision) {
        if (masks.maskData == null || masks.maskBytes == null || masks.maskByteBuffer == null) return;

        Arrays.fill(masks.maskData, 0);
        final int maxWords = Math.min(words.length, masks.maskData.length);
        for (int wordIdx = 0; wordIdx < maxWords; wordIdx++) {
            masks.maskData[wordIdx] = words[wordIdx];
        }

        packMaskBytes(masks);
        masks.lastMaskBuildRevision = uploadRevision;
        masks.atlasUploadRevision = Long.MIN_VALUE;
    }

    private static void packMaskBytes(final ShipMasks masks) {
        final int wordCount = Math.min(masks.maskData.length, masks.maskBytes.length / 4);
        for (int i = 0; i < wordCount; i++) {
            final int word = masks.maskData[i];
            final int base = i * 4;
            masks.maskBytes[base] = (byte) (word & 0xFF);
            masks.maskBytes[base + 1] = (byte) ((word >>> 8) & 0xFF);
            masks.maskBytes[base + 2] = (byte) ((word >>> 16) & 0xFF);
            masks.maskBytes[base + 3] = (byte) ((word >>> 24) & 0xFF);
        }
        masks.maskByteBuffer.clear();
        masks.maskByteBuffer.put(masks.maskBytes);
        masks.maskByteBuffer.flip();
    }

    private static boolean ensureAtlasTexture(final int requiredHeight) {
        // Grow-only.
        if (atlasTexId != 0 && (!isLiveTextureId(atlasTexId) || atlasTexHeight < requiredHeight)) {
            if (isLiveTextureId(atlasTexId)) {
                TextureUtil.releaseTextureId(atlasTexId);
            }
            atlasTexId = 0;
            atlasTexHeight = 0;
        }

        if (atlasTexId == 0) {
            final int height = Math.min(ATLAS_MAX_HEIGHT, Math.max(1, requiredHeight));
            final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                final int id = TextureUtil.generateTextureId();
                GlStateManager._bindTexture(id);

                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, MASK_TEX_WIDTH, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

                atlasTexId = id;
                atlasTexHeight = height;

                for (final ShipMasks m : SHIP_MASKS.values()) {
                    m.atlasRowStart = -1;
                    m.atlasUploadRevision = Long.MIN_VALUE;
                }
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
                GlStateManager._bindTexture(prevBinding);
            }
        }

        return atlasTexId != 0 && atlasTexHeight >= requiredHeight;
    }

    private static void uploadMaskToAtlas(final ShipMasks masks, final int rowStart) {
        if (!isLiveTextureId(atlasTexId)) return;
        if (masks.maskByteBuffer == null) return;
        if (rowStart + masks.maskRowCount > atlasTexHeight) return;

        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GlStateManager._bindTexture(atlasTexId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            masks.maskByteBuffer.clear();
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, rowStart, MASK_TEX_WIDTH, masks.maskRowCount,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, masks.maskByteBuffer);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }

    private static void ensureMetaUploadBufferCapacity(final int activeShips) {
        if (metaUploadBuffer != null && metaUploadBufferCapacityShips >= activeShips) return;
        // Round up to nearest 16 ships to avoid thrashing on small fluctuations.
        final int target = Math.max(16, ((activeShips + 15) / 16) * 16);
        metaUploadBuffer = BufferUtils.createFloatBuffer(target * META_FLOATS_PER_SHIP);
        metaUploadBufferCapacityShips = target;
    }

    private static void ensureMetaTexture(final int activeShips) {
        // Grow-only.
        if (metaTexId != 0 && (!isLiveTextureId(metaTexId) || metaTexHeight < activeShips)) {
            if (isLiveTextureId(metaTexId)) {
                TextureUtil.releaseTextureId(metaTexId);
            }
            metaTexId = 0;
            metaTexHeight = 0;
        }

        if (metaTexId == 0) {
            final int height = Math.max(1, activeShips);
            final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                final int id = TextureUtil.generateTextureId();
                GlStateManager._bindTexture(id);

                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, META_TEX_WIDTH, height, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);

                metaTexId = id;
                metaTexHeight = height;
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
                GlStateManager._bindTexture(prevBinding);
            }
        }
    }

    private static void uploadMetaTexture(final int activeShips) {
        if (!isLiveTextureId(metaTexId) || metaUploadBuffer == null || activeShips <= 0) return;
        if (activeShips > metaTexHeight) return;

        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GlStateManager._bindTexture(metaTexId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, META_TEX_WIDTH, activeShips,
                GL11.GL_RGBA, GL11.GL_FLOAT, metaUploadBuffer);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }
}
