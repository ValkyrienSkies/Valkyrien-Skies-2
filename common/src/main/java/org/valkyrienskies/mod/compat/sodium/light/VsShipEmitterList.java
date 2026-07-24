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
import net.minecraft.world.level.block.state.BlockState;

import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;

import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * Per-frame list of every ship voxel that emits light, packed as
 * {@code vec4(worldX, worldY, worldZ, lightLevel)} entries into a buffer
 * texture. The shader iterates this list per fragment and computes
 * {@code max(0, L - distance(fragment, emitter))}, taking the max across all
 * emitters — so the lighting tracks ship motion at sub-block precision (the
 * world coords are continuous floats, not grid-quantized).
 *
 * <p>This replaces the BFS-based block-light dilation in
 * {@link VsWorldFromShipLightStorage} when smoothness is more important than
 * occluder-respecting propagation. The emitter list ignores opaque blocks —
 * light goes through walls — so it's the right tool when "occlusion" is off
 * and only the warm glow on nearby surfaces is wanted.
 */
public class VsShipEmitterList {
    /** Cap on emitters tracked per frame. The shader's loop is bounded too;
     *  keep these in sync. 1024 entries × 32 bytes = 32 KB GPU buffer
     *  (2 RGBA32F texels per emitter: position+light + ship rotation). */
    public static final int MAX_EMITTERS = 1024;
    private static final int BYTES_PER_EMITTER = 32; // 8 floats: vec4(worldX, worldY, worldZ, lightLevel) + vec4(qx, qy, qz, qw)

    private final long arenaPtr;
    private int count = 0;

    private int buffer = 0;
    private int texture = 0;
    private int currentByteSize = 0;

    private final Vector3d scratch = new Vector3d();
    private final Quaterniond scratchQuat = new Quaterniond();
    private final BlockPos.MutableBlockPos scratchBlockPos = new BlockPos.MutableBlockPos();

    public VsShipEmitterList() {
        arenaPtr = MemoryUtil.nmemAlloc((long) MAX_EMITTERS * BYTES_PER_EMITTER);
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

    /** Walk a ship's voxels, find emitters, transform to world coords, append. */
    public void populateFromShip(LevelAccessor level, ClientShip ship) {
        AABBic shipyardAabb = ship.getShipAABB();
        if (shipyardAabb == null) return;

        ShipTransform xform = ship.getRenderTransform();
        Matrix4dc shipToWorld = xform.getShipToWorld();
        // Pull the rotation once per ship; every emitter on this hull
        // shares the same quaternion. The shader applies its inverse
        // to the world-frame fragment-to-emitter offset so the
        // octahedral light bubble rotates with the hull.
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
                    if (count >= MAX_EMITTERS) return;
                    scratchBlockPos.set(sx, sy, sz);
                    BlockState state = level.getBlockState(scratchBlockPos);
                    int lightLevel = state.getLightEmission();
                    if (lightLevel <= 0) continue;

                    // Voxel center → world coords. Float precision preserved.
                    scratch.set(sx + 0.5, sy + 0.5, sz + 0.5);
                    shipToWorld.transformPosition(scratch);

                    appendEmitter(scratch.x, scratch.y, scratch.z, lightLevel, qx, qy, qz, qw);
                }
            }
        }
    }

    public void appendEmitter(final double worldX, final double worldY, final double worldZ, final int lightLevel,
        final float qx, final float qy, final float qz, final float qw) {
        if (lightLevel <= 0 || count >= MAX_EMITTERS) return;

        long offset = arenaPtr + (long) count * BYTES_PER_EMITTER;
        MemoryUtil.memPutFloat(offset,        (float) worldX);
        MemoryUtil.memPutFloat(offset + 4,    (float) worldY);
        MemoryUtil.memPutFloat(offset + 8,    (float) worldZ);
        MemoryUtil.memPutFloat(offset + 12,   (float) lightLevel);
        MemoryUtil.memPutFloat(offset + 16,   qx);
        MemoryUtil.memPutFloat(offset + 20,   qy);
        MemoryUtil.memPutFloat(offset + 24,   qz);
        MemoryUtil.memPutFloat(offset + 28,   qw);
        count++;
    }

    public void upload() {
        ensureGlObjects();
        int needed = Math.max(BYTES_PER_EMITTER, count * BYTES_PER_EMITTER);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        boolean orphaned = currentByteSize != needed;
        if (orphaned || count > 0) {
            GL15.nglBufferData(GL31.GL_TEXTURE_BUFFER, needed, arenaPtr, GL15.GL_DYNAMIC_DRAW);
            currentByteSize = needed;
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        if (orphaned) {
            // Some drivers cache the buffer-data-store reference at glTexBuffer
            // time; re-associate after orphaning so the texture sees the new data.
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
