package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.world.level.LevelAccessor;

import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
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
    public static final int MAX_RENDER_EMITTERS = 128;
    private static final int BYTES_PER_EMITTER = 32; // 8 floats: vec4(worldX, worldY, worldZ, lightLevel) + vec4(qx, qy, qz, qw)
    private static final int CAPACITY_BYTES = MAX_EMITTERS * BYTES_PER_EMITTER;

    private static final double[] NO_EMITTERS = new double[0];

    private final long arenaPtr;
    private int count = 0;

    private int buffer = 0;
    private int texture = 0;
    private int currentByteSize = 0;

    private final Vector3d scratch = new Vector3d();

    public VsShipEmitterList() {
        arenaPtr = MemoryUtil.nmemAlloc((long) MAX_EMITTERS * BYTES_PER_EMITTER);
    }

    public void delete() {
        if (arenaPtr != 0L) MemoryUtil.nmemFree(arenaPtr);
        if (buffer != 0) { GL15.glDeleteBuffers(buffer); buffer = 0; }
        if (texture != 0) { GL11.glDeleteTextures(texture); texture = 0; }
        currentByteSize = 0;
    }

    public void beginFrame() {
        count = 0;
    }

    public int size() {
        return count;
    }

    /** Sample the same emitter range that the terrain shaders use. */
    public float sampleLight(final double x, final double y, final double z) {
        return sampleLight(x, y, z, false);
    }

    public float sampleLight(final double x, final double y, final double z, final boolean useShipAxes) {
        double best = 0.0;
        for (int i = 0; i < Math.min(count, MAX_RENDER_EMITTERS); i++) {
            final long offset = arenaPtr + (long) i * BYTES_PER_EMITTER;
            final double dx = x - MemoryUtil.memGetFloat(offset);
            final double dy = y - MemoryUtil.memGetFloat(offset + 4);
            final double dz = z - MemoryUtil.memGetFloat(offset + 8);
            final double level = MemoryUtil.memGetFloat(offset + 12);
            if (Math.abs(dx) >= level || Math.abs(dy) >= level || Math.abs(dz) >= level) continue;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (useShipAxes) {
                final double qx = -MemoryUtil.memGetFloat(offset + 16);
                final double qy = -MemoryUtil.memGetFloat(offset + 20);
                final double qz = -MemoryUtil.memGetFloat(offset + 24);
                final double qw = MemoryUtil.memGetFloat(offset + 28);
                final double tx = 2.0 * (qy * dz - qz * dy);
                final double ty = 2.0 * (qz * dx - qx * dz);
                final double tz = 2.0 * (qx * dy - qy * dx);
                distance = Math.abs(dx + qw * tx + qy * tz - qz * ty)
                    + Math.abs(dy + qw * ty + qz * tx - qx * tz)
                    + Math.abs(dz + qw * tz + qx * ty - qy * tx);
            }
            best = Math.max(best, level - distance);
        }
        return (float) Math.min(15.0, best);
    }

    public static double[] scanShipEmitters(final LevelAccessor level, final ClientShip ship) {
        final AABBic shipyardAabb = ship.getShipAABB();
        if (shipyardAabb == null) return NO_EMITTERS;

        final DoubleArrayList out = new DoubleArrayList();
        ship.getActiveChunksSet().forEach((cx, cz) -> {
            if (out.size() >= MAX_EMITTERS * 4) return;
            final var chunk = level.getChunk(cx, cz, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
            if (chunk == null) return;
            for (int sy = Math.max(level.getMinSection(), shipyardAabb.minY() >> 4);
                sy <= Math.min(level.getMaxSection() - 1, (shipyardAabb.maxY() - 1) >> 4); sy++) {
                final var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                if (!section.maybeHas(state -> state.getLightEmission() > 0)) continue;
                for (int y = Math.max(shipyardAabb.minY(), sy << 4); y < Math.min(shipyardAabb.maxY(), (sy + 1) << 4); y++)
                    for (int z = Math.max(shipyardAabb.minZ(), cz << 4); z < Math.min(shipyardAabb.maxZ(), (cz + 1) << 4); z++)
                        for (int x = Math.max(shipyardAabb.minX(), cx << 4); x < Math.min(shipyardAabb.maxX(), (cx + 1) << 4); x++) {
                            final int light = section.getBlockState(x & 15, y & 15, z & 15).getLightEmission();
                            if (light <= 0) continue;
                            out.add(x + 0.5);
                            out.add(y + 0.5);
                            out.add(z + 0.5);
                            out.add(light);
                            if (out.size() >= MAX_EMITTERS * 4) return;
                        }
            }
        });
        return out.isEmpty() ? NO_EMITTERS : out.toDoubleArray();
    }
    public void appendShipEmitters(final ClientShip ship, final double[] shipyardEmitters) {
        if (shipyardEmitters.length == 0 || count >= MAX_EMITTERS) return;

        final ShipTransform xform = ship.getRenderTransform();
        final Matrix4dc shipToWorld = xform.getShipToWorld();
        final Quaterniondc rot = xform.getRotation();
        final float qx = (float) rot.x();
        final float qy = (float) rot.y();
        final float qz = (float) rot.z();
        final float qw = (float) rot.w();

        for (int i = 0; i + 3 < shipyardEmitters.length; i += 4) {
            if (count >= MAX_EMITTERS) return;
            scratch.set(shipyardEmitters[i], shipyardEmitters[i + 1], shipyardEmitters[i + 2]);
            shipToWorld.transformPosition(scratch);
            appendEmitter(scratch.x, scratch.y, scratch.z, (int) shipyardEmitters[i + 3], qx, qy, qz, qw);
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
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        final boolean respec = currentByteSize != CAPACITY_BYTES;
        if (respec) {
            GL15.nglBufferData(GL31.GL_TEXTURE_BUFFER, CAPACITY_BYTES, MemoryUtil.NULL, GL15.GL_DYNAMIC_DRAW);
            currentByteSize = CAPACITY_BYTES;
        }
        if (count > 0) {
            GL15.nglBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, (long) count * BYTES_PER_EMITTER, arenaPtr);
        }
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        if (respec) {
            // Some drivers cache the buffer-data-store reference at glTexBuffer
            // time; re-associate after (re)allocation so the texture sees the new
            // store.
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
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        }
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, buffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }
}
