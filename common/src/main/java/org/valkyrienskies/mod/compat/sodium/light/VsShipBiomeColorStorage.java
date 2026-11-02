package org.valkyrienskies.mod.compat.sodium.light;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.BitSet;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Per-section world-space biome color cache for ship rendering. For each tracked
 * section we sample the three vanilla biome color resolvers (grass, foliage, water)
 * across a 16x16 grid in XZ at the section's middle Y, and upload the packed RGBA
 * colors to a buffer texture. The ship fragment shader can then re-tint biome-
 * dependent quads (grass tops, leaves, water, vines, etc.) using the world biome
 * at the ship's current rendered position rather than whatever was baked at chunk
 * mesh time.
 *
 * <p>Layout per section: 3 resolvers * 16x16 colors = 768 R32UI ints (3072 B).
 * Within a section the resolver order is GRASS=0, FOLIAGE=1, WATER=2; within each
 * resolver block the offset is {@code z * 16 + x}.
 *
 * <p>Each color is packed as one R32UI int with bytes {@code [R, G, B, 0xFF]} in
 * native (little-endian) order, so the shader can {@code unpackUnorm4x8(c).rgb}.
 *
 * <p>A separate LUT (mirroring {@link VsShipLightStorage}'s) maps section
 * coordinates to arena indices — independent of the light storage's LUT so the
 * two storages can prune sections independently.
 */
public class VsShipBiomeColorStorage {
    public static final int CELLS_PER_SECTION = 16 * 16; // 256
    public static final int RESOLVER_COUNT = 3;
    public static final int CELLS_PER_RESOLVER_BYTES = CELLS_PER_SECTION * 4; // 1024
    public static final int SECTION_SIZE_BYTES = CELLS_PER_RESOLVER_BYTES * RESOLVER_COUNT; // 3072
    public static final int SECTION_SIZE_INTS = SECTION_SIZE_BYTES / 4; // 768
    public static final int CELLS_PER_RESOLVER_INTS = CELLS_PER_RESOLVER_BYTES / 4; // 256

    // Resolver indices — must match the encoding the mesher mixin packs into
    // the AO byte and the shader's lookup. 0 means "no biome tint".
    public static final int RESOLVER_GRASS = 0;
    public static final int RESOLVER_FOLIAGE = 1;
    public static final int RESOLVER_WATER = 2;

    private static final int DEFAULT_CAPACITY = 64;
    private static final int INVALID = -1;

    private final Long2IntMap section2Index = new Long2IntOpenHashMap();
    private final BitSet free = new BitSet();
    private final BitSet changed = new BitSet();
    private boolean lutDirty = true;

    private LongSet requestedThisFrame = new LongOpenHashSet();

    private long arenaPtr;
    private int capacity;

    private int sectionsBuffer = 0;
    private int sectionsTexture = 0;
    private int lutBuffer = 0;
    private int lutTexture = 0;
    private int currentSectionsByteSize = 0;

    private final LightLut lut = new LightLut();
    private final IntArrayList lutScratch = new IntArrayList();
    private ByteBuffer lutUploadBuf = null;

    public VsShipBiomeColorStorage() {
        capacity = DEFAULT_CAPACITY;
        section2Index.defaultReturnValue(INVALID);
        arenaPtr = MemoryUtil.nmemAlloc((long) capacity * SECTION_SIZE_BYTES);
        for (int i = 0; i < capacity; i++) {
            free.set(i);
        }
    }

    public void delete() {
        if (arenaPtr != 0L) {
            MemoryUtil.nmemFree(arenaPtr);
            arenaPtr = 0L;
        }
        if (sectionsBuffer != 0) {
            GL15.glDeleteBuffers(sectionsBuffer);
            sectionsBuffer = 0;
        }
        if (lutBuffer != 0) {
            GL15.glDeleteBuffers(lutBuffer);
            lutBuffer = 0;
        }
        if (sectionsTexture != 0) {
            GL11.glDeleteTextures(sectionsTexture);
            sectionsTexture = 0;
        }
        if (lutTexture != 0) {
            GL11.glDeleteTextures(lutTexture);
            lutTexture = 0;
        }
    }

    public void beginFrame() {
        requestedThisFrame.clear();
    }

    public void requestSection(LevelAccessor level, long sectionPos) {
        if (!requestedThisFrame.add(sectionPos)) {
            return;
        }
        int idx = section2Index.get(sectionPos);
        if (idx == INVALID) {
            idx = allocate();
            section2Index.put(sectionPos, idx);
            lut.add(sectionPos, idx);
            lutDirty = true;
            collectSection(level, sectionPos, idx);
        }
        // Biome colors are stable for a fixed (sectionPos, world-biome) pair so
        // we never re-sample. If the ship moves into a new biome the section is
        // pruned and re-allocated at the new world section coords.
    }

    public void requestSectionsInAabb(LevelAccessor level,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        // Match the light storage's 1-block padding so the requested set is the
        // same and we don't churn allocations at the border.
        minX -= 1.0; minY -= 1.0; minZ -= 1.0;
        maxX += 1.0; maxY += 1.0; maxZ += 1.0;

        int sxMin = SectionPos.blockToSectionCoord((int) Math.floor(minX));
        int syMin = SectionPos.blockToSectionCoord((int) Math.floor(minY));
        int szMin = SectionPos.blockToSectionCoord((int) Math.floor(minZ));
        int sxMax = SectionPos.blockToSectionCoord((int) Math.floor(maxX));
        int syMax = SectionPos.blockToSectionCoord((int) Math.floor(maxY));
        int szMax = SectionPos.blockToSectionCoord((int) Math.floor(maxZ));

        long count = (long)(sxMax - sxMin + 1) * (syMax - syMin + 1) * (szMax - szMin + 1);
        if (count > MAX_SECTIONS_PER_REQUEST) {
            return;
        }

        for (int sy = syMin; sy <= syMax; sy++) {
            for (int sz = szMin; sz <= szMax; sz++) {
                for (int sx = sxMin; sx <= sxMax; sx++) {
                    requestSection(level, SectionPos.asLong(sx, sy, sz));
                }
            }
        }
    }

    private static final int MAX_SECTIONS_PER_REQUEST = 4096;

    public void pruneUnused() {
        if (section2Index.isEmpty()) {
            return;
        }
        ObjectIterator<Long2IntMap.Entry> it = section2Index.long2IntEntrySet().iterator();
        boolean anyRemoved = false;
        while (it.hasNext()) {
            Long2IntMap.Entry entry = it.next();
            long sec = entry.getLongKey();
            if (!requestedThisFrame.contains(sec)) {
                int idx = entry.getIntValue();
                free.set(idx);
                changed.clear(idx);
                lut.remove(sec);
                it.remove();
                anyRemoved = true;
            }
        }
        if (anyRemoved) {
            lutDirty = true;
        }
    }

    public void upload() {
        ensureGlObjects();

        if (!changed.isEmpty()) {
            int needed = capacity * SECTION_SIZE_BYTES;
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, sectionsBuffer);
            boolean orphaned = currentSectionsByteSize != needed;
            if (orphaned) {
                GL15.nglBufferData(GL31.GL_TEXTURE_BUFFER, needed, arenaPtr, GL15.GL_DYNAMIC_DRAW);
                currentSectionsByteSize = needed;
            } else {
                for (int i = changed.nextSetBit(0); i >= 0; i = changed.nextSetBit(i + 1)) {
                    GL15.nglBufferSubData(GL31.GL_TEXTURE_BUFFER,
                            (long) i * SECTION_SIZE_BYTES,
                            SECTION_SIZE_BYTES,
                            arenaPtr + (long) i * SECTION_SIZE_BYTES);
                }
            }
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
            if (orphaned) {
                GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
                GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30RUI(), sectionsBuffer);
                GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
            }
            changed.clear();
        }

        if (lutDirty) {
            lut.flattenInto(lutScratch);
            int sizeInts = lutScratch.size();
            int neededBytes = Math.max(4, sizeInts * 4);
            if (lutUploadBuf == null || lutUploadBuf.capacity() < neededBytes) {
                int newCap = Math.max(neededBytes, lutUploadBuf == null ? 1024 : lutUploadBuf.capacity() * 2);
                lutUploadBuf = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder());
            }
            IntBuffer ib = lutUploadBuf.asIntBuffer();
            if (sizeInts > 0) {
                ib.put(lutScratch.elements(), 0, sizeInts);
            }
            ib.flip();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, lutBuffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, ib, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30RUI(), lutBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
            lutDirty = false;
        }
    }

    public void bind(int sectionsTextureUnit, int lutTextureUnit) {
        ensureGlObjects();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + sectionsTextureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + lutTextureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void ensureGlObjects() {
        if (sectionsBuffer == 0) {
            sectionsBuffer = GL15.glGenBuffers();
        }
        if (sectionsTexture == 0) {
            sectionsTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30RUI(), sectionsBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
        if (lutBuffer == 0) {
            lutBuffer = GL15.glGenBuffers();
        }
        if (lutTexture == 0) {
            lutTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30RUI(), lutBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }

    private static int GL30RUI() {
        return 0x8236;
    }

    private int allocate() {
        int idx = free.nextSetBit(0);
        if (idx < 0) {
            grow();
            idx = free.nextSetBit(0);
        }
        free.clear(idx);
        return idx;
    }

    private void grow() {
        int newCapacity = capacity * 2;
        long newPtr = MemoryUtil.nmemAlloc((long) newCapacity * SECTION_SIZE_BYTES);
        MemoryUtil.memCopy(arenaPtr, newPtr, (long) capacity * SECTION_SIZE_BYTES);
        MemoryUtil.nmemFree(arenaPtr);
        arenaPtr = newPtr;
        for (int i = capacity; i < newCapacity; i++) {
            free.set(i);
        }
        capacity = newCapacity;
        currentSectionsByteSize = -1;
        for (int i = 0; i < capacity; i++) {
            if (!free.get(i)) {
                changed.set(i);
            }
        }
    }

    private void collectSection(LevelAccessor level, long sectionPos, int idx) {
        long ptr = arenaPtr + (long) idx * SECTION_SIZE_BYTES;
        // No need to memSet — every byte is overwritten below.

        // Sample biome colors at the section's middle Y. 1.18+ biomes can be 3D
        // but the per-block tint cache keys by (chunk, y_at_quarter_resolution),
        // so this single Y is "good enough" for most ships and saves 16x the
        // resolver-cache lookups.
        int xMin = SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos));
        int yMid = SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos)) + 8;
        int zMin = SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Three resolvers in a fixed slot order so the shader & mesher agree.
        sampleResolver(level, BiomeColors.GRASS_COLOR_RESOLVER,
                ptr + (long) RESOLVER_GRASS * CELLS_PER_RESOLVER_BYTES,
                xMin, yMid, zMin, pos);
        sampleResolver(level, BiomeColors.FOLIAGE_COLOR_RESOLVER,
                ptr + (long) RESOLVER_FOLIAGE * CELLS_PER_RESOLVER_BYTES,
                xMin, yMid, zMin, pos);
        sampleResolver(level, BiomeColors.WATER_COLOR_RESOLVER,
                ptr + (long) RESOLVER_WATER * CELLS_PER_RESOLVER_BYTES,
                xMin, yMid, zMin, pos);

        changed.set(idx);
    }

    private static void sampleResolver(LevelAccessor level, ColorResolver resolver,
                                       long basePtr, int xMin, int y, int zMin,
                                       BlockPos.MutableBlockPos pos) {
        if (!(level instanceof Level worldLevel)) {
            // Can't query biome tints without a Level. Fill with white so
            // multiplicative tinting in the shader is a no-op.
            for (int i = 0; i < CELLS_PER_SECTION; i++) {
                MemoryUtil.memPutInt(basePtr + (long) i * 4, 0xFFFFFFFF);
            }
            return;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                pos.set(xMin + x, y, zMin + z);
                int argb = worldLevel.getBlockTint(pos, resolver);
                // getBlockTint returns 0xRRGGBB (alpha bits 0). Pack as
                // little-endian RGBA = bytes [R, G, B, 0xFF] so the shader
                // sees R in the low byte of the R32UI texel. That matches
                // GLSL unpackUnorm4x8 byte order.
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                int packed = r | (g << 8) | (b << 16) | (0xFF << 24);
                MemoryUtil.memPutInt(basePtr + (long) (z * 16 + x) * 4, packed);
            }
        }
    }

    public boolean hasAnySections() {
        return !section2Index.isEmpty();
    }

    public int trackedSectionCount() {
        return section2Index.size();
    }
}
