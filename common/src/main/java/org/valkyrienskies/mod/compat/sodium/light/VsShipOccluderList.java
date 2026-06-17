package org.valkyrienskies.mod.compat.sodium.light;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
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
     *  (each voxel uses 2 RGBA32F texels: vec4 position + vec4 quaternion). */
    public static final int MAX_OCCLUDERS = 1024;
    /** 8 floats per voxel: position vec4 (worldX, worldY, worldZ, payload)
     *  followed by ship-frame rotation quaternion (qx, qy, qz, qw). The
     *  shader fetches both texels per voxel and rotates the
     *  fragment-to-voxel offset into ship frame so the Manhattan SDF
     *  axes line up with the ship's axes — that way the AO octagon
     *  visibly rotates with the ship instead of staying world-aligned. */
    private static final int BYTES_PER_OCCLUDER = 32;

    private final long arenaPtr;
    private int count = 0;

    private int buffer = 0;
    private int texture = 0;
    private int currentByteSize = 0;

    private final Vector3d scratch = new Vector3d();
    private final Quaterniond scratchQuat = new Quaterniond();
    private final BlockPos.MutableBlockPos scratchBlockPos = new BlockPos.MutableBlockPos();

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
    }

    public int size() {
        return count;
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
                            && Block.isShapeFullBlock(state.getOcclusionShape(level, scratchBlockPos));
                    if (!isSolid) continue;

                    // Voxel center → world coords. Float precision preserved
                    // through the matrix multiply, so a rotating ship's voxel
                    // centers move along smooth arcs rather than snapping
                    // cell-to-cell.
                    scratch.set(sx + 0.5, sy + 0.5, sz + 0.5);
                    shipToWorld.transformPosition(scratch);

                    appendOccluder(scratch.x, scratch.y, scratch.z, qx, qy, qz, qw);
                }
            }
        }
    }

    public void appendOccluder(final double worldX, final double worldY, final double worldZ,
        final float qx, final float qy, final float qz, final float qw) {
        if (count >= MAX_OCCLUDERS) return;

        long offset = arenaPtr + (long) count * BYTES_PER_OCCLUDER;
        MemoryUtil.memPutFloat(offset,        (float) worldX);
        MemoryUtil.memPutFloat(offset + 4,    (float) worldY);
        MemoryUtil.memPutFloat(offset + 8,    (float) worldZ);
        MemoryUtil.memPutFloat(offset + 12,   0.0f);
        MemoryUtil.memPutFloat(offset + 16,   qx);
        MemoryUtil.memPutFloat(offset + 20,   qy);
        MemoryUtil.memPutFloat(offset + 24,   qz);
        MemoryUtil.memPutFloat(offset + 28,   qw);
        count++;
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
