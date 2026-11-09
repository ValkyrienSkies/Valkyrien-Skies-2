package org.valkyrienskies.mod.common.fluid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

final class ExteriorFluidField implements AutoCloseable {
    static final int SIZE = 128;
    private static final int SECTIONS = SIZE / 16;
    private final Map<BlockPos, byte[]> sections = new HashMap<>();
    private final Map<Long, LevelChunk> chunks = new HashMap<>();
    private final LinkedHashSet<BlockPos> pending = new LinkedHashSet<>();
    private final ByteBuffer upload = BufferUtils.createByteBuffer(4096);
    private BlockPos minimum;
    private ClientLevel level;
    private int texture;
    private boolean initialized;

    BlockPos minimum() { return minimum; }
    int texture() { return texture; }

    void dirty(final int x, final int y, final int z) {
        if (minimum == null) return;
        for (int below = 0; below <= 1; below++) {
            final var section = new BlockPos(x, y - below, z);
            if (inside(section)) pending.add(section);
        }
    }

    private boolean inside(final BlockPos section) {
        return section.getX() >= minimum.getX() && section.getX() < minimum.getX() + SECTIONS
            && section.getY() >= minimum.getY() && section.getY() < minimum.getY() + SECTIONS
            && section.getZ() >= minimum.getZ() && section.getZ() < minimum.getZ() + SECTIONS;
    }

    boolean update(final ClientLevel current, final BlockPos camera) {
        final var origin = new BlockPos(Math.floorDiv(camera.getX(), 16) - SECTIONS / 2,
            Math.floorDiv(camera.getY(), 16) - SECTIONS / 2, Math.floorDiv(camera.getZ(), 16) - SECTIONS / 2);
        if (level != current) { close(); level = current; }
        if (!origin.equals(minimum) || texture == 0) {
            minimum = origin;
            sections.keySet().removeIf(section -> !inside(section));
            pending.removeIf(section -> !inside(section));
            chunks.keySet().removeIf(key -> (int) (key >> 32) < minimum.getX()
                || (int) (key >> 32) >= minimum.getX() + SECTIONS
                || (int) (long) key < minimum.getZ() || (int) (long) key >= minimum.getZ() + SECTIONS);
            initialized = false;
            if (texture == 0) {
                texture = GL11.glGenTextures();
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_R8, SIZE, SIZE, SIZE, 0,
                    GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            for (int z = 0; z < SECTIONS; z++) for (int y = 0; y < SECTIONS; y++) for (int x = 0; x < SECTIONS; x++) {
                final var section = minimum.offset(x, y, z);
                if (!sections.containsKey(section)) pending.add(section);
            }
        }
        for (int z = 0; z < SECTIONS; z++) for (int x = 0; x < SECTIONS; x++) {
            final int sx = minimum.getX() + x, sz = minimum.getZ() + z;
            final long key = ((long) sx << 32) ^ (sz & 0xffffffffL);
            final var chunk = level.getChunkSource().getChunk(sx, sz, ChunkStatus.FULL, false);
            if (!chunks.containsKey(key) || chunks.get(key) != chunk) {
                chunks.put(key, chunk);
                for (int y = 0; y < SECTIONS; y++) pending.add(new BlockPos(sx, minimum.getY() + y, sz));
            }
        }
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        final int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        try {
            final var iterator = pending.iterator();
            for (int work = 0; work < 32 && iterator.hasNext(); work++) {
                final var section = iterator.next(); iterator.remove();
                final byte[] data = sample(section);
                if (!Arrays.equals(data, sections.get(section))) {
                    upload.clear(); upload.put(data).flip();
                    GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0,
                        Math.floorMod(section.getX(), SECTIONS) * 16, Math.floorMod(section.getY(), SECTIONS) * 16,
                        Math.floorMod(section.getZ(), SECTIONS) * 16, 16, 16, 16, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, upload);
                    sections.put(section, data);
                }
            }
        } finally { GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment); }
        initialized |= pending.isEmpty();
        return initialized;
    }

    private byte[] sample(final BlockPos section) {
        final byte[] values = new byte[4096];
        final var chunk = level.getChunkSource().getChunk(section.getX(), section.getZ(), ChunkStatus.FULL, false);
        final int baseY = section.getY() * 16;
        if (chunk == null || level.isOutsideBuildHeight(baseY)) return values;
        final var data = chunk.getSection(level.getSectionIndex(baseY));
        if (data.hasOnlyAir() || !data.getStates().maybeHas(state -> state.getFluidState().is(FluidTags.WATER))) return values;
        final var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            final var fluid = data.getFluidState(x, y, z);
            if (!fluid.is(FluidTags.WATER)) continue;
            pos.set(section.getX() * 16 + x, baseY + y, section.getZ() * 16 + z);
            values[x + 16 * (y + 16 * z)] = (byte) Math.round(fluid.getHeight(level, pos) * 255.0F);
        }
        return values;
    }

    @Override public void close() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        texture = 0; minimum = null; level = null; initialized = false;
        sections.clear(); pending.clear(); chunks.clear();
    }
}
