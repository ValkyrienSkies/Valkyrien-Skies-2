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
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/**
 * Reimplementation of Flywheel's light storage approach (light_lut + light sections),
 * tailored for VS ship rendering. Uploads world-space block & sky light data for the
 * sections that ships occupy, so the ship shader can sample lighting based on the
 * current world position of each fragment instead of the baked chunk lightmap.
 *
 * <p>Layout per section: {@value #BLOCKS_PER_SECTION} bytes, one byte per block in an
 * 18x18x18 volume (a 16x16x16 section with a one-block border). Each byte packs
 * block light in the low nibble and sky light in the high nibble.
 *
 * <p>Two GL buffer textures are exposed:
 * <ul>
 *     <li><b>sectionsTexture</b>: R32UI buffer texture containing the packed light
 *     data for all tracked sections, laid out contiguously.</li>
 *     <li><b>lutTexture</b>: R32UI buffer texture containing a 3-level (Y/X/Z)
 *     coordinate-span LUT mapping section coordinates to indices into the sections
 *     buffer. The LUT layout matches Flywheel's so the same shader logic applies.</li>
 * </ul>
 */
public class VsShipLightStorage {
    public static final int BLOCKS_PER_SECTION = 18 * 18 * 18; // 5832
    public static final int LIGHT_SIZE_BYTES = BLOCKS_PER_SECTION; // 5832
    // Solid bitmap: one bit per block in the 18^3 volume, packed in 32-bit words.
    public static final int SOLID_SIZE_BYTES = ((BLOCKS_PER_SECTION + 31) / 32) * 4; // 732
    // Layout: [solid bits (732 B)] [light bytes (5832 B)] -- matches Flywheel's layout
    // so the light_lut.glsl-style smooth lookup logic can be ported verbatim.
    public static final int SOLID_START_BYTES = 0;
    public static final int LIGHT_START_BYTES = SOLID_SIZE_BYTES;
    public static final int SECTION_SIZE_BYTES = SOLID_SIZE_BYTES + LIGHT_SIZE_BYTES; // 6564
    public static final int SECTION_SIZE_INTS = SECTION_SIZE_BYTES / 4; // 1641
    public static final int LIGHT_START_INTS = SOLID_SIZE_BYTES / 4; // 183

    private static final int DEFAULT_CAPACITY = 64;
    private static final int INVALID = -1;

    private final Long2IntMap section2Index = new Long2IntOpenHashMap();
    private final BitSet free = new BitSet();
    private final BitSet changed = new BitSet();
    /**
     * Sections whose world-light data is stale (e.g. because of a server light update)
     * and needs to be re-collected the next time the section is requested for a render.
     */
    private final BitSet dirty = new BitSet();
    private boolean lutDirty = true;

    private LongSet requestedThisFrame = new LongOpenHashSet();

    private long arenaPtr;
    private int capacity;

    private int sectionsBuffer = 0;
    private int sectionsTexture = 0;
    private int lutBuffer = 0;
    private int lutTexture = 0;
    private int currentSectionsByteSize = 0;
    private int currentLutByteSize = 0;

    private final LightLut lut = new LightLut();
    private final IntArrayList lutScratch = new IntArrayList();
    private ByteBuffer lutUploadBuf = null;

    public VsShipLightStorage() {
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

    /** Call at the start of the frame's ship render pass to clear the request set. */
    public void beginFrame() {
        requestedThisFrame.clear();
    }

    /**
     * Request the section at the given world section pos for this frame. Newly
     * required sections are populated immediately by reading the level's light engine.
     */
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
        } else if (dirty.get(idx)) {
            // Section was invalidated by a world light update — re-collect in place.
            collectSection(level, sectionPos, idx);
            dirty.clear(idx);
        }
    }

    /**
     * Request all world sections that intersect the given world-space AABB.
     * Bails out if the AABB spans more than {@value #MAX_SECTIONS_PER_REQUEST}
     * sections (degenerate or absurdly large ships) to avoid pathological work.
     *
     * <p>The AABB is padded by 1 block in each direction before sectioning so
     * sub-block jitter in the ship's interpolated render transform doesn't
     * cause the tracked-section set to flip frame-to-frame at boundaries — that
     * flip would force a LUT rebuild and flicker the lighting on vertices that
     * happen to land in a section that was just pruned.
     */
    public void requestSectionsInAabb(LevelAccessor level,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
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

    /** Free sections that were not requested this frame. */
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
                dirty.clear(idx);
                lut.remove(sec);
                it.remove();
                anyRemoved = true;
            }
        }
        if (anyRemoved) {
            lutDirty = true;
        }
    }

    /**
     * Mark a section dirty so its light data is re-collected the next time it is
     * requested. Must also flag any neighbor we track, since each section's 18^3
     * payload contains a one-block border copied from its neighbors.
     */
    public void invalidateSection(long sectionPos) {
        int sx = SectionPos.x(sectionPos);
        int sy = SectionPos.y(sectionPos);
        int sz = SectionPos.z(sectionPos);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int idx = section2Index.get(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
                    if (idx != INVALID) {
                        dirty.set(idx);
                    }
                }
            }
        }
    }

    /**
     * Upload changed sections and (if needed) the LUT to the GPU.
     * Must be called on the render thread.
     */
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
            // Defensively re-associate the texture with the buffer after every
            // glBufferData orphan — some drivers cache the data-store reference at
            // glTexBuffer time and won't see the new store otherwise.
            if (orphaned) {
                GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
                GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL31.GL_R32UI, sectionsBuffer);
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
            // glBufferData orphans the buffer's data store; re-associate so the
            // texture sees the new storage on drivers that cache the data-store
            // reference at glTexBuffer time.
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL31.GL_R32UI, lutBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
            currentLutByteSize = sizeInts * 4;
            lutDirty = false;
        }
    }

    /**
     * Bind the sections and LUT textures to the given texture units, leaving them
     * active so the corresponding sampler uniforms see them. Goes through
     * {@link GlStateManager} for the active-texture-unit changes so Mojang's
     * cached state stays in sync (sodium and vanilla rely on that cache and
     * will silently miss state changes if we bypass it).
     */
    public void bind(int sectionsTextureUnit, int lutTextureUnit) {
        ensureGlObjects();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + sectionsTextureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + lutTextureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    public boolean hasAnySections() {
        return !section2Index.isEmpty();
    }

    public int trackedSectionCount() {
        return section2Index.size();
    }

    /** Print details of all tracked sections so we can verify the data path. */
    public void dumpFirstSection() {
        if (section2Index.isEmpty()) {
            System.out.println("[VS-LIGHT]   (no tracked sections)");
            return;
        }
        StringBuilder sections = new StringBuilder("[VS-LIGHT]   tracked: ");
        ObjectIterator<Long2IntMap.Entry> it = section2Index.long2IntEntrySet().iterator();
        while (it.hasNext()) {
            Long2IntMap.Entry e = it.next();
            long sp = e.getLongKey();
            sections.append("(").append(SectionPos.x(sp)).append(",")
                    .append(SectionPos.y(sp)).append(",")
                    .append(SectionPos.z(sp)).append(")=").append(e.getIntValue()).append(" ");
        }
        System.out.println(sections);
        // Also flatten and print FULL LUT.
        int[] flat = lut.flatten();
        StringBuilder sb = new StringBuilder("[VS-LIGHT]   LUT (" + flat.length + " ints):");
        for (int i = 0; i < flat.length; i++) sb.append(" ").append(flat[i]);
        System.out.println(sb);
    }

    /** Lookup test: returns the arena index for a given section pos, or -1 if not tracked. */
    public int lookupSection(int sx, int sy, int sz) {
        return section2Index.get(SectionPos.asLong(sx, sy, sz));
    }

    private void ensureGlObjects() {
        if (sectionsBuffer == 0) {
            sectionsBuffer = GL15.glGenBuffers();
        }
        if (sectionsTexture == 0) {
            sectionsTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL31.GL_R32UI, sectionsBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
        if (lutBuffer == 0) {
            lutBuffer = GL15.glGenBuffers();
        }
        if (lutTexture == 0) {
            lutTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL31.GL_R32UI, lutBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
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
        // Force a full upload next flush.
        currentSectionsByteSize = -1;
        // Re-mark all currently-used sections as changed so they get re-uploaded into the new buffer.
        for (int i = 0; i < capacity; i++) {
            if (!free.get(i)) {
                changed.set(i);
            }
        }
    }

    private void collectSection(LevelAccessor level, long sectionPos, int idx) {
        long ptr = arenaPtr + (long) idx * SECTION_SIZE_BYTES;
        // Zero out so any unset solid bits / unwritten light bytes start clean.
        MemoryUtil.memSet(ptr, 0, SECTION_SIZE_BYTES);

        LayerLightEventListener block = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        LayerLightEventListener sky = level.getLightEngine().getLayerListener(LightLayer.SKY);

        int xMin = SectionPos.sectionToBlockCoord(SectionPos.x(sectionPos));
        int yMin = SectionPos.sectionToBlockCoord(SectionPos.y(sectionPos));
        int zMin = SectionPos.sectionToBlockCoord(SectionPos.z(sectionPos));

        // Single pass over the 18^3 volume: emit one byte of packed light
        // (low nibble = block, high nibble = sky) at offset bitIdx in the
        // light region, and accumulate solid bits 32-at-a-time into the
        // solid bitmap. The loop order matches the offset formula
        // (x+1) + (z+1)*18 + (y+1)*324 so the byte index equals bitIdx.
        // The shader reads the solid region as R32UI words (bitOffset >> 5),
        // so writing 32-bit ints in native endian matches the shader's view.
        long solidPtr = ptr + SOLID_START_BYTES;
        long lightPtr = ptr + LIGHT_START_BYTES;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int acc = 0;
        int bitIdx = 0;
        for (int y = -1; y < 17; y++) {
            for (int z = -1; z < 17; z++) {
                for (int x = -1; x < 17; x++) {
                    pos.set(xMin + x, yMin + y, zMin + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.canOcclude() && Block.isShapeFullBlock(state.getOcclusionShape(level, pos))) {
                        acc |= 1 << (bitIdx & 31);
                    }
                    int b = block.getLightValue(pos);
                    int s = sky.getLightValue(pos);
                    MemoryUtil.memPutByte(lightPtr + bitIdx, (byte) ((b & 0xF) | ((s & 0xF) << 4)));
                    bitIdx++;
                    if ((bitIdx & 31) == 0) {
                        MemoryUtil.memPutInt(solidPtr, acc);
                        solidPtr += 4;
                        acc = 0;
                    }
                }
            }
        }
        // Residual partial word (5832 bits = 182 full words + 8 remaining).
        if ((bitIdx & 31) != 0) {
            MemoryUtil.memPutInt(solidPtr, acc);
        }
        changed.set(idx);
    }
}
