package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;

import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * Per-frame list of every solid ship voxel, packed as
 * {@code vec4(worldX, worldY, worldZ, padding)} entries into a buffer
 * texture. Mirrors {@link VsShipEmitterList} structurally but populates
 * from solid (full-cube opaque) voxels instead of light-emitting voxels.
 *
 * <p>Used by the world FSH for ship-to-world AO. Iterating voxel centers
 * directly (rather than reading from the world-grid-aligned cell strengths
 * in {@link VsWorldFromShipLightStorage}) lets the AO shadow follow the
 * ship's transform continuously — including rotation — because the world
 * coords are continuous floats, not grid-quantized. With the cell-storage
 * approach, the AO pattern was anchored to world-aligned cells and
 * couldn't truly rotate; voxel-list AO is rotation-aware by construction.
 */
public class VsShipOccluderList {
    /** Cap on occluders tracked per frame. The shader's loop is bounded
     *  too; keep these in sync. 1024 entries × 32 bytes = 32 KB GPU buffer
     *  (2 RGBA32F texels per voxel: position, quaternion). */
    public static final int MAX_OCCLUDERS = 1024;
    /** 8 floats per voxel, two vec4 texels:
     *    [i*2 + 0] = (worldX, worldY, worldZ, shipIndex)
     *    [i*2 + 1] = (qx, qy, qz, qw) — ship rotation, used so each
     *                voxel's octagon stays oriented with the ship
     *                instead of becoming a world-axis box. */
    private static final int BYTES_PER_OCCLUDER = 32;
    private static final float CARDINAL_MIN_DISTANCE = 0.75f;
    private static final float CARDINAL_MAX_DISTANCE = 2.10f;
    private static final float CARDINAL_CROSS_AXIS_EPSILON = 0.18f;

    private final long arenaPtr;
    private int count = 0;

    private int buffer = 0;
    private int texture = 0;
    private int currentByteSize = 0;

    private final Vector3d scratch = new Vector3d();
    private final Quaterniond scratchQuat = new Quaterniond();
    private final BlockPos.MutableBlockPos scratchBlockPos = new BlockPos.MutableBlockPos();

    /** Per-frame map from ship.getId() → small dense index used as the
     *  shader's per-voxel ship tag. Reset every frame in beginFrame; index
     *  0 is reserved for "no ship" so the ship FSH can compare against
     *  -1 / unset values without false matches. */
    private final Long2IntMap shipIdToIndex = new Long2IntOpenHashMap();
    private int nextShipIndex = 1;

    public VsShipOccluderList() {
        arenaPtr = MemoryUtil.nmemAlloc((long) MAX_OCCLUDERS * BYTES_PER_OCCLUDER);
    }

    public void delete() {
        if (arenaPtr != 0L) MemoryUtil.nmemFree(arenaPtr);
        if (buffer != 0) { GL15.glDeleteBuffers(buffer); buffer = 0; }
        if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
    }

    public void beginFrame() {
        count = 0;
        shipIdToIndex.clear();
        nextShipIndex = 1;
    }

    public int size() {
        return count;
    }

    /** Returns the per-frame dense index assigned to {@code shipId} during
     *  populateFromShip, or 0 if the ship wasn't populated this frame.
     *  Used by setupShipShaderState to tell the ship FSH which voxels in
     *  u_VsShipOccluders belong to the currently rendering ship so they
     *  can be skipped (their AO is already baked into v_Color.a). */
    public int getShipIndex(long shipId) {
        return shipIdToIndex.getOrDefault(shipId, 0);
    }

    /** Assigns a fresh per-frame dense index to {@code shipId} if it doesn't
     *  already have one, and returns it. Callers populating occluders from
     *  outside {@link #populateFromShip} (e.g. {@link VsWorldFromShipLightStorage})
     *  use this to tag voxels with the ship they belong to. */
    public int assignShipIndex(long shipId) {
        return shipIdToIndex.computeIfAbsent(shipId, id -> nextShipIndex++);
    }

    /** Walk a ship's voxels, find solid blocks, transform centers to world. */
    public void populateFromShip(LevelAccessor level, ClientShip ship) {
        AABBic shipyardAabb = ship.getShipAABB();
        if (shipyardAabb == null) return;

        ShipTransform xform = ship.getRenderTransform();
        Matrix4dc shipToWorld = xform.getShipToWorld();
        // Pull the rotation out of the ship's transform once per ship —
        // every voxel of this ship shares the same quaternion. The shader
        // applies its inverse to the fragment-to-voxel offset to express
        // the SDF in the ship's local frame, so the Manhattan tent
        // (octagonal shadow) rotates with the ship.
        shipToWorld.getNormalizedRotation(scratchQuat);
        float qx = (float) scratchQuat.x;
        float qy = (float) scratchQuat.y;
        float qz = (float) scratchQuat.z;
        float qw = (float) scratchQuat.w;

        // Assign or look up this ship's per-frame dense index. Stored in
        // voxel.w so the ship FSH can compare against u_VsCurrentShipIndex
        // and skip same-ship voxels (their AO is already baked into
        // v_Color.a — counting them again double-darkens self-shadows).
        long shipId = ship.getId();
        int shipIdx = shipIdToIndex.computeIfAbsent(shipId, id -> nextShipIndex++);
        float shipIndexFloat = (float) shipIdx;

        int xMin = shipyardAabb.minX();
        int yMin = shipyardAabb.minY();
        int zMin = shipyardAabb.minZ();
        int xMax = shipyardAabb.maxX();
        int yMax = shipyardAabb.maxY();
        int zMax = shipyardAabb.maxZ();

        for (int sy = yMin; sy <= yMax; sy++) {
            for (int sz = zMin; sz <= zMax; sz++) {
                for (int sx = xMin; sx <= xMax; sx++) {
                    if (count >= MAX_OCCLUDERS) return;
                    scratchBlockPos.set(sx, sy, sz);
                    BlockState state = level.getBlockState(scratchBlockPos);
                    if (state.isAir()) continue;
                    boolean isSolid = state.canOcclude()
                            && state.isCollisionShapeFullBlock(level, scratchBlockPos);
                    if (!isSolid) continue;

                    // Voxel center → world coords. Float precision preserved
                    // through the matrix multiply, so a rotating ship's voxel
                    // centers move along smooth arcs rather than snapping
                    // cell-to-cell.
                    scratch.set(sx + 0.5, sy + 0.5, sz + 0.5);
                    shipToWorld.transformPosition(scratch);

                    appendOccluder(scratch.x, scratch.y, scratch.z, shipIndexFloat, qx, qy, qz, qw);
                }
            }
        }
    }

    public void appendOccluder(final double worldX, final double worldY, final double worldZ, final float shipIndex,
        final float qx, final float qy, final float qz, final float qw) {
        if (count >= MAX_OCCLUDERS) return;

        long offset = arenaPtr + (long) count * BYTES_PER_OCCLUDER;
        // Texel 0: position + ship index
        MemoryUtil.memPutFloat(offset,        (float) worldX);
        MemoryUtil.memPutFloat(offset + 4,    (float) worldY);
        MemoryUtil.memPutFloat(offset + 8,    (float) worldZ);
        MemoryUtil.memPutFloat(offset + 12,   shipIndex);
        // Texel 1: quaternion
        MemoryUtil.memPutFloat(offset + 16,   qx);
        MemoryUtil.memPutFloat(offset + 20,   qy);
        MemoryUtil.memPutFloat(offset + 24,   qz);
        MemoryUtil.memPutFloat(offset + 28,   qw);
        count++;
    }

    /** O(N²) scan: for each occluder, check whether it has a cardinal
     *  neighbour along each WORLD axis (X, Y, Z). A "neighbour" is
     *  any other ship voxel (regardless of which ship) OR any solid
     *  world block within 1-2 cells along a cardinal direction. The
     *  detection runs in world frame (no ship-local rotation) so
     *  ship-to-ship and ship-to-world cardinal pairs are detected
     *  consistently. Per-axis flags are packed into bits 16/17/18 of
     *  the float-bits of the shipIndex slot:
     *    bit 16 = has neighbour along world X
     *    bit 17 = has neighbour along world Y
     *    bit 18 = has neighbour along world Z
     *  Must be called after every populate / appendOccluder for the
     *  frame and before {@link #upload()}. */
    public void computeCardinalFlags(LevelAccessor level) {
        for (int i = 0; i < count; i++) {
            long iOff = arenaPtr + (long) i * BYTES_PER_OCCLUDER;
            float ix = MemoryUtil.memGetFloat(iOff);
            float iy = MemoryUtil.memGetFloat(iOff + 4);
            float iz = MemoryUtil.memGetFloat(iOff + 8);

            boolean cardX = false;
            boolean cardY = false;
            boolean cardZ = false;

            // Pass 1: other ship voxels (any ship) cardinally aligned
            // in world frame.
            for (int j = 0; j < count; j++) {
                if (j == i) continue;
                long jOff = arenaPtr + (long) j * BYTES_PER_OCCLUDER;
                float dx = (float) Math.abs(MemoryUtil.memGetFloat(jOff)     - ix);
                float dy = (float) Math.abs(MemoryUtil.memGetFloat(jOff + 4) - iy);
                float dz = (float) Math.abs(MemoryUtil.memGetFloat(jOff + 8) - iz);

                if (dx >= CARDINAL_MIN_DISTANCE && dx <= CARDINAL_MAX_DISTANCE
                        && dy < CARDINAL_CROSS_AXIS_EPSILON && dz < CARDINAL_CROSS_AXIS_EPSILON) {
                    cardX = true;
                }
                if (dy >= CARDINAL_MIN_DISTANCE && dy <= CARDINAL_MAX_DISTANCE
                        && dx < CARDINAL_CROSS_AXIS_EPSILON && dz < CARDINAL_CROSS_AXIS_EPSILON) {
                    cardY = true;
                }
                if (dz >= CARDINAL_MIN_DISTANCE && dz <= CARDINAL_MAX_DISTANCE
                        && dx < CARDINAL_CROSS_AXIS_EPSILON && dy < CARDINAL_CROSS_AXIS_EPSILON) {
                    cardZ = true;
                }
            }

            // Pass 2: solid world blocks at cardinal offsets +/-1, +/-2.
            if (level != null) {
                int bx = Math.round(ix - 0.5f);
                int by = Math.round(iy - 0.5f);
                int bz = Math.round(iz - 0.5f);
                boolean nearBlockCenter = Math.abs(ix - (bx + 0.5f)) < CARDINAL_CROSS_AXIS_EPSILON
                        && Math.abs(iy - (by + 0.5f)) < CARDINAL_CROSS_AXIS_EPSILON
                        && Math.abs(iz - (bz + 0.5f)) < CARDINAL_CROSS_AXIS_EPSILON;
                if (!nearBlockCenter) {
                    int origBits = Float.floatToRawIntBits(MemoryUtil.memGetFloat(iOff + 12));
                    int packed = origBits
                        | (cardX ? 0x10000 : 0)
                        | (cardY ? 0x20000 : 0)
                        | (cardZ ? 0x40000 : 0);
                    MemoryUtil.memPutFloat(iOff + 12, Float.intBitsToFloat(packed));
                    continue;
                }
                for (int d = 1; d <= 2; d++) {
                    if (isSolidWorldBlock(level, bx + d, by, bz)
                            || isSolidWorldBlock(level, bx - d, by, bz)) {
                        cardX = true;
                    }
                    if (isSolidWorldBlock(level, bx, by + d, bz)
                            || isSolidWorldBlock(level, bx, by - d, bz)) {
                        cardY = true;
                    }
                    if (isSolidWorldBlock(level, bx, by, bz + d)
                            || isSolidWorldBlock(level, bx, by, bz - d)) {
                        cardZ = true;
                    }
                }
            }

            int origBits = Float.floatToRawIntBits(MemoryUtil.memGetFloat(iOff + 12));
            int packed = origBits
                | (cardX ? 0x10000 : 0)
                | (cardY ? 0x20000 : 0)
                | (cardZ ? 0x40000 : 0);
            MemoryUtil.memPutFloat(iOff + 12, Float.intBitsToFloat(packed));
        }
        prioritizeCardinalOccluders();
    }

    private void prioritizeCardinalOccluders() {
        int write = 0;
        for (int read = 0; read < count; read++) {
            long readOff = arenaPtr + (long) read * BYTES_PER_OCCLUDER;
            int flags = Float.floatToRawIntBits(MemoryUtil.memGetFloat(readOff + 12));
            if ((flags & 0x70000) == 0) continue;
            if (read != write) {
                swapOccluders(read, write);
            }
            write++;
        }
    }

    private void swapOccluders(int a, int b) {
        long aOff = arenaPtr + (long) a * BYTES_PER_OCCLUDER;
        long bOff = arenaPtr + (long) b * BYTES_PER_OCCLUDER;
        for (int byteOffset = 0; byteOffset < BYTES_PER_OCCLUDER; byteOffset += Float.BYTES) {
            float tmp = MemoryUtil.memGetFloat(aOff + byteOffset);
            MemoryUtil.memPutFloat(aOff + byteOffset, MemoryUtil.memGetFloat(bOff + byteOffset));
            MemoryUtil.memPutFloat(bOff + byteOffset, tmp);
        }
    }

    private boolean isSolidWorldBlock(LevelAccessor level, int x, int y, int z) {
        scratchBlockPos.set(x, y, z);
        BlockState state = level.getBlockState(scratchBlockPos);
        if (state.isAir()) return false;
        return state.canOcclude() && state.isCollisionShapeFullBlock(level, scratchBlockPos);
    }

    public void upload() {
        ensureGlObjects();
        int needed = Math.max(BYTES_PER_OCCLUDER, count * BYTES_PER_OCCLUDER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        boolean orphaned = currentByteSize != needed;
        if (orphaned || count > 0) {
            GL15.nglBufferData(GL31.GL_TEXTURE_BUFFER, needed, arenaPtr, GL15.GL_DYNAMIC_DRAW);
            currentByteSize = needed;
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        if (orphaned) {
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }

    public void bind(int textureUnit) {
        ensureGlObjects();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void ensureGlObjects() {
        if (buffer == 0) buffer = GL15.glGenBuffers();
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }
}
