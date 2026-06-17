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

import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;

import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * Stores ship voxels projected into the world's light grid for the inverse
 * lighting direction: ships occluding world sky-light below them and ship-
 * internal block-light emitters illuminating nearby world blocks.
 *
 * <p>Layout is identical to {@link VsShipLightStorage} — solid bitmap +
 * light bytes in 18^3 voxels per section — so the same shader sampling helpers
 * (vs_chunkCoordToSectionIndex, vs_isSolid, vs_lightAt) work with this
 * storage's textures swapped in for the buffer/LUT samplers.
 *
 * <p>Unlike {@link VsShipLightStorage} which collects from the world's light
 * engine on first request and re-uses across frames, this storage is rebuilt
 * every frame because ship voxels' world coordinates change as ships move and
 * rotate. Sections are allocated on demand and pruned each frame.
 *
 * <p>Sky-light bits in the byte are unused (set to 0); only the block-light
 * nibble carries data (ship-internal emitters projected into world coords).
 * The solid bitmap is what gives sky occlusion — the world FSH reduces sky
 * light when a solid bit sits above the world fragment.
 */
public class VsWorldFromShipLightStorage {
    public static final int BLOCKS_PER_SECTION = 18 * 18 * 18; // 5832
    public static final int LIGHT_SIZE_BYTES = BLOCKS_PER_SECTION;
    public static final int SOLID_SIZE_BYTES = ((BLOCKS_PER_SECTION + 31) / 32) * 4; // 732
    public static final int SOLID_START_BYTES = 0;
    public static final int LIGHT_START_BYTES = SOLID_SIZE_BYTES;
    public static final int SECTION_SIZE_BYTES = SOLID_SIZE_BYTES + LIGHT_SIZE_BYTES; // 6564
    public static final int SECTION_SIZE_INTS = SECTION_SIZE_BYTES / 4;

    private static final int DEFAULT_CAPACITY = 64;
    private static final int INVALID = -1;
    /** Per-ship safety cap so a 1km cube ship can't lock up the meshing thread. */
    private static final int MAX_BLOCKS_PER_SHIP = 1 << 18; // 262144
    /** How far each ship emitter spreads into the world's block-light grid —
     *  bounded radius for the BFS. Vanilla MC's max is 15 (light decrements 1
     *  per block to 0). Bigger radius = more cells written per emitter
     *  (up to ~O(R^3)); 15 is the upper bound for visual parity with vanilla. */
    private static final int MAX_LIGHT_DILATION = 15;

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

    private final Vector3d scratchPos = new Vector3d();
    private final Quaterniond scratchQuat = new Quaterniond();
    private final BlockPos.MutableBlockPos scratchBlockPos = new BlockPos.MutableBlockPos();

    // Reusable BFS queue (parallel int arrays, cleared per emitter). Used to
    // flood block-light through the world's voxel grid while skipping cells
    // whose solid bit is set (ship walls block their own light).
    private final IntArrayList bfsX = new IntArrayList();
    private final IntArrayList bfsY = new IntArrayList();
    private final IntArrayList bfsZ = new IntArrayList();
    private final IntArrayList bfsL = new IntArrayList();
    // Per-ship emitter list — populated by pass 1, drained by pass 2 BFS.
    private final IntArrayList pendingEmitterX = new IntArrayList();
    private final IntArrayList pendingEmitterY = new IntArrayList();
    private final IntArrayList pendingEmitterZ = new IntArrayList();
    private final IntArrayList pendingEmitterL = new IntArrayList();

    public VsWorldFromShipLightStorage() {
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
        if (sectionsBuffer != 0) { GL15.glDeleteBuffers(sectionsBuffer); sectionsBuffer = 0; }
        if (lutBuffer != 0) { GL15.glDeleteBuffers(lutBuffer); lutBuffer = 0; }
        if (sectionsTexture != 0) { GL11.glDeleteTextures(sectionsTexture); sectionsTexture = 0; }
        if (lutTexture != 0) { GL11.glDeleteTextures(lutTexture); lutTexture = 0; }
    }

    /** Begin a fresh frame: forget which sections were touched last frame. */
    public void beginFrame() {
        requestedThisFrame.clear();
    }

    /**
     * Walk every block in {@code ship}'s shipyard-space AABB, transform each one
     * to world coordinates via the ship's render transform, and write its solid
     * bit / block-light value into the world section that contains it. Sections
     * are allocated as needed and zeroed before first write each frame.
     */
    public void populateFromShip(LevelAccessor level, ClientShip ship) {
        populateFromShip(level, ship, null, null);
    }

    public void markShipVoxels(LevelAccessor level, ClientShip ship, VsShipEmitterList emitters,
        VsShipOccluderList occluders) {
        AABBic shipyardAabb = ship.getShipAABB();
        if (shipyardAabb == null) return;

        int xMin = shipyardAabb.minX();
        int yMin = shipyardAabb.minY();
        int zMin = shipyardAabb.minZ();
        int xMax = shipyardAabb.maxX();
        int yMax = shipyardAabb.maxY();
        int zMax = shipyardAabb.maxZ();

        long count = (long)(xMax - xMin + 1) * (yMax - yMin + 1) * (zMax - zMin + 1);
        if (count > MAX_BLOCKS_PER_SHIP) return;

        ShipTransform xform = ship.getRenderTransform();
        Matrix4dc shipToWorld = xform.getShipToWorld();
        shipToWorld.getNormalizedRotation(scratchQuat);
        final float qx = (float) scratchQuat.x;
        final float qy = (float) scratchQuat.y;
        final float qz = (float) scratchQuat.z;
        final float qw = (float) scratchQuat.w;

        int minChunkX = xMin >> 4;
        int maxChunkX = xMax >> 4;
        int minChunkZ = zMin >> 4;
        int maxChunkZ = zMax >> 4;
        int minSectionY = yMin >> 4;
        int maxSectionY = yMax >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int sectionXMin = Math.max(xMin, chunkX << 4);
            int sectionXMax = Math.min(xMax, (chunkX << 4) + 15);

            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ);

                int sectionZMin = Math.max(zMin, chunkZ << 4);
                int sectionZMax = Math.min(zMax, (chunkZ << 4) + 15);

                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = sectionY - level.getMinSection();
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                        continue;
                    }

                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir()) {
                        continue;
                    }

                    int sectionYMin = Math.max(yMin, sectionY << 4);
                    int sectionYMax = Math.min(yMax, (sectionY << 4) + 15);

                    for (int sy = sectionYMin; sy <= sectionYMax; sy++) {
                        int localY = sy & 15;
                        for (int sz = sectionZMin; sz <= sectionZMax; sz++) {
                            int localZ = sz & 15;
                            for (int sx = sectionXMin; sx <= sectionXMax; sx++) {
                                BlockState state = section.getBlockState(sx & 15, localY, localZ);
                    if (state.isAir()) continue;

                    scratchBlockPos.set(sx, sy, sz);
                    boolean isSolid = state.canOcclude()
                            && Block.isShapeFullBlock(state.getOcclusionShape(level, scratchBlockPos));
                    int blockLight = state.getLightEmission();
                    if (!isSolid && blockLight == 0) continue;

                    // Project block center to world coords (floats — sub-block
                    // accuracy preserved). The CENTER cell (floor of these) is
                    // where we set the binary solid bit + queue the emitter
                    // BFS source. The 8 cells overlapping the voxel get
                    // trilinear-weighted occluder STRENGTH so the shader's
                    // sky/wall attenuation tracks ship motion smoothly even
                    // when the ship moves a fraction of a block.
                    scratchPos.set(sx + 0.5, sy + 0.5, sz + 0.5);
                    shipToWorld.transformPosition(scratchPos);
                    double cwx = scratchPos.x;
                    double cwy = scratchPos.y;
                    double cwz = scratchPos.z;
                    // +1e-4 bias absorbs FP rounding error from the ship-to-
                    // world matrix multiply. A clean integer ship transform
                    // can produce e.g. 9.99999998 instead of exactly 10.0,
                    // and a naked floor() then puts the splat in cell 9
                    // instead of 10 — visible as an AO shape that's offset
                    // by one cell from where vanilla AO would render an
                    // equivalent solid block. The bias is small enough to
                    // not affect genuinely-at-half positions (10.5 ± epsilon
                    // floors the same with or without it).
                    int wx = (int) Math.floor(cwx + 1.0e-4);
                    int wy = (int) Math.floor(cwy + 1.0e-4);
                    int wz = (int) Math.floor(cwz + 1.0e-4);

                    int idx = ensureSection(SectionPos.asLong(wx >> 4, wy >> 4, wz >> 4));

                    // Voxel offset within the section's 18^3 grid (matches the
                    // VsShipLightStorage layout: +1 to skip the leading border).
                    int ix = (wx & 15) + 1;
                    int iy = (wy & 15) + 1;
                    int iz = (wz & 15) + 1;
                    int voxelIdx = ix + iz * 18 + iy * 18 * 18;

                    if (isSolid) {
                        long secPtr = arenaPtr + (long) idx * SECTION_SIZE_BYTES;
                        // Binary solid bit at center cell — used by the BFS
                        // dilation as an opaque-block check (light propagation
                        // stops on solid). Smoothness is handled separately
                        // via the trilinear-splatted occluder strength below.
                        long solidWordPtr = secPtr + SOLID_START_BYTES + (long)(voxelIdx >>> 5) * 4L;
                        int existing = MemoryUtil.memGetInt(solidWordPtr);
                        MemoryUtil.memPutInt(solidWordPtr, existing | (1 << (voxelIdx & 31)));
                        // Trilinear splat the voxel's "1.0 occluder" across the
                        // 8 world cells it overlaps. As the ship moves
                        // sub-block, the weights redistribute smoothly —
                        // shadow on the ground tracks the motion instead of
                        // jumping at integer crossings.
                        splatOccluderStrength(cwx, cwy, cwz);
                        if (occluders != null) {
                            occluders.appendOccluder(cwx, cwy, cwz, qx, qy, qz, qw);
                        }
                    }
                    if (blockLight > 0) {
                        // Defer BFS dilation to pass 2 — solid bits for this
                        // and every later voxel of the current ship aren't all
                        // written yet, and we want the BFS to treat them as
                        // occluders. Trilinear-splat the emitter source: for
                        // each of the (up to) 8 cells the voxel overlaps,
                        // queue an independent BFS source at full L. The BFS
                        // dedupes via existing-light max-merge, so additional
                        // seeds don't make things brighter — they just keep
                        // the lit region tracking ship motion smoothly across
                        // block boundaries. Without this the torch BFS source
                        // sits at a single floor()'d cell and the lit region
                        // jumps a full block whenever the ship's sub-block
                        // position crosses an integer.
                        splatEmitterSeeds(cwx, cwy, cwz, blockLight & 0xF);
                        if (emitters != null) {
                            emitters.appendEmitter(cwx, cwy, cwz, blockLight, qx, qy, qz, qw);
                        }
                    }
                }
            }
        }
                }
            }
        }

    }

    public void populateFromShip(LevelAccessor level, ClientShip ship, VsShipEmitterList emitters,
        VsShipOccluderList occluders) {
        markShipVoxels(level, ship, emitters, occluders);
        runQueuedBfs(level);
    }

    public void runQueuedBfs(LevelAccessor level) {
        for (int i = 0; i < pendingEmitterX.size(); i++) {
            bfsLight(
                    level,
                    pendingEmitterX.getInt(i),
                    pendingEmitterY.getInt(i),
                    pendingEmitterZ.getInt(i),
                    pendingEmitterL.getInt(i));
        }
        pendingEmitterX.clear();
        pendingEmitterY.clear();
        pendingEmitterZ.clear();
        pendingEmitterL.clear();
    }

    public int getBlockLightAt(BlockPos pos) {
        long sectionPos = SectionPos.asLong(pos);
        int idx = section2Index.get(sectionPos);
        if(idx == INVALID) return 0;
        int ix = (pos.getX() & 0xF) + 1;
        int iy = (pos.getY() & 0xF) + 1;
        int iz = (pos.getZ() & 0xF) + 1;
        int voxelIdx = ix + iz * 18 + iy * 18 * 18;
        long secPtr = arenaPtr + (long) idx * SECTION_SIZE_BYTES;
        long lightBytePtr = secPtr + LIGHT_START_BYTES + voxelIdx;
        return MemoryUtil.memGetByte(lightBytePtr) & 0xF;
    }

    /**
     * Trilinear-splat one ship voxel's occluder strength across the (up to)
     * 8 world cells it overlaps. The voxel center is at world
     * {@code (cwx, cwy, cwz)} (float) and occupies {@code [c-0.5, c+0.5]^3};
     * weight per cell is the volume of overlap. As the ship moves or
     * rotates sub-block, the weights redistribute smoothly across cells
     * instead of jumping at integer crossings, so the AO shadow tracks
     * ship motion continuously.
     *
     * <p>For an integer-positioned voxel (center at .5, .5, .5) the trilinear
     * weights collapse to 1.0 in the single containing cell and 0 elsewhere
     * — i.e., the integer case is identical to binary splat, so the FSH's
     * dynamic-triangle-split AO logic still recognizes "lone occluder"
     * patterns and produces clean triangular shapes. Sub-block positions
     * naturally produce multi-cell strengths, which fall through to the
     * default smooth-gradient interpolation — exactly what we want for
     * smooth rotation.
     */
    private void splatOccluderStrength(double cwx, double cwy, double cwz) {
        // Voxel range [c-0.5, c+0.5] overlaps cells floor(c-0.5) and the next
        // one. wxLo is the cell with floor; wxHi is the cell above it.
        double bxF = cwx - 0.5;
        double byF = cwy - 0.5;
        double bzF = cwz - 0.5;
        int bx = (int) Math.floor(bxF);
        int by = (int) Math.floor(byF);
        int bz = (int) Math.floor(bzF);
        double fxHi = bxF - bx;
        double fyHi = byF - by;
        double fzHi = bzF - bz;
        double fxLo = 1.0 - fxHi;
        double fyLo = 1.0 - fyHi;
        double fzLo = 1.0 - fzHi;

        writeOccluderAt(bx,     by,     bz,     fxLo * fyLo * fzLo);
        writeOccluderAt(bx + 1, by,     bz,     fxHi * fyLo * fzLo);
        writeOccluderAt(bx,     by + 1, bz,     fxLo * fyHi * fzLo);
        writeOccluderAt(bx + 1, by + 1, bz,     fxHi * fyHi * fzLo);
        writeOccluderAt(bx,     by,     bz + 1, fxLo * fyLo * fzHi);
        writeOccluderAt(bx + 1, by,     bz + 1, fxHi * fyLo * fzHi);
        writeOccluderAt(bx,     by + 1, bz + 1, fxLo * fyHi * fzHi);
        writeOccluderAt(bx + 1, by + 1, bz + 1, fxHi * fyHi * fzHi);
    }

    /**
     * Queue BFS seeds for an emitter voxel at world {@code (cwx, cwy, cwz)}
     * with light value {@code L}. Each of the (up to) 8 cells the voxel
     * overlaps gets a full-L seed when its trilinear weight is above a small
     * threshold — the BFS later max-merges between sources, so extra seeds
     * don't add brightness, they just keep the lit area tracking ship motion
     * smoothly across cell boundaries.
     */
    private void splatEmitterSeeds(double cwx, double cwy, double cwz, int lightValue) {
        if (lightValue <= 0) return;
        double bxF = cwx - 0.5;
        double byF = cwy - 0.5;
        double bzF = cwz - 0.5;
        int bx = (int) Math.floor(bxF);
        int by = (int) Math.floor(byF);
        int bz = (int) Math.floor(bzF);
        double fxHi = bxF - bx;
        double fyHi = byF - by;
        double fzHi = bzF - bz;
        double fxLo = 1.0 - fxHi;
        double fyLo = 1.0 - fyHi;
        double fzLo = 1.0 - fzHi;

        // 0.01 threshold so cells barely overlapping the voxel don't waste a
        // whole BFS expansion. The discrete on/off at the threshold is small
        // enough to be invisible — the dominant cells (weight > 0.5) carry
        // the bulk of the light pattern.
        final double T = 0.01;
        if (fxLo * fyLo * fzLo > T) seedEmitter(bx,     by,     bz,     lightValue);
        if (fxHi * fyLo * fzLo > T) seedEmitter(bx + 1, by,     bz,     lightValue);
        if (fxLo * fyHi * fzLo > T) seedEmitter(bx,     by + 1, bz,     lightValue);
        if (fxHi * fyHi * fzLo > T) seedEmitter(bx + 1, by + 1, bz,     lightValue);
        if (fxLo * fyLo * fzHi > T) seedEmitter(bx,     by,     bz + 1, lightValue);
        if (fxHi * fyLo * fzHi > T) seedEmitter(bx + 1, by,     bz + 1, lightValue);
        if (fxLo * fyHi * fzHi > T) seedEmitter(bx,     by + 1, bz + 1, lightValue);
        if (fxHi * fyHi * fzHi > T) seedEmitter(bx + 1, by + 1, bz + 1, lightValue);
    }

    private void seedEmitter(int wx, int wy, int wz, int lightValue) {
        pendingEmitterX.add(wx);
        pendingEmitterY.add(wy);
        pendingEmitterZ.add(wz);
        pendingEmitterL.add(lightValue);
    }

    private void writeOccluderAt(int wx, int wy, int wz, double weight) {
        if (weight <= 0.0) return;
        int strength = (int) Math.round(Math.min(1.0, weight) * 15.0);
        if (strength <= 0) return;
        int idx = ensureSection(SectionPos.asLong(wx >> 4, wy >> 4, wz >> 4));
        int ix = (wx & 15) + 1;
        int iy = (wy & 15) + 1;
        int iz = (wz & 15) + 1;
        int voxelIdx = ix + iz * 18 + iy * 18 * 18;
        long lightBytePtr = arenaPtr + (long) idx * SECTION_SIZE_BYTES + LIGHT_START_BYTES + voxelIdx;
        byte existing = MemoryUtil.memGetByte(lightBytePtr);
        int curOccl = (existing >> 4) & 0xF;
        int curBlock = existing & 0xF;
        int newOccl = Math.max(curOccl, strength);
        MemoryUtil.memPutByte(lightBytePtr, (byte) ((newOccl << 4) | curBlock));
    }

    /**
     * Vanilla-style block-light flood from {@code (wx, wy, wz)} carrying
     * {@code lightValue}, decrementing 1 per step. Skips cells whose solid bit
     * is set in the storage (ship walls block their own light) and cells that
     * already hold a brighter value (saves redundant work and gives correct
     * max-merge behavior on overlapping emitters). Capped at
     * {@link #MAX_LIGHT_DILATION} so a glowstone-rich ship can't blow the
     * per-frame budget.
     */
    private void bfsLight(LevelAccessor level, int sx, int sy, int sz, int startLight) {
        if (startLight <= 0) return;
        // Start the BFS at the emitter's full value (so a torch shines as
        // brightly as a torch does). MAX_LIGHT_DILATION (= vanilla's 15) is
        // already the natural ceiling because light decrements to 0 in <=15
        // steps — keeping `startLight` here without the min() removes the
        // accidental brightness clamp that made every emitter look dim.
        int start = Math.min(startLight, MAX_LIGHT_DILATION);
        bfsX.clear(); bfsY.clear(); bfsZ.clear(); bfsL.clear();
        bfsX.add(sx); bfsY.add(sy); bfsZ.add(sz); bfsL.add(start);
        int head = 0;
        while (head < bfsX.size()) {
            int x = bfsX.getInt(head);
            int y = bfsY.getInt(head);
            int z = bfsZ.getInt(head);
            int l = bfsL.getInt(head);
            head++;

            if (isWorldOccluder(level, x, y, z)) {
                continue;
            }

            int idx = ensureSection(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
            int ix = (x & 15) + 1;
            int iy = (y & 15) + 1;
            int iz = (z & 15) + 1;
            int voxelIdx = ix + iz * 18 + iy * 18 * 18;
            long secPtr = arenaPtr + (long) idx * SECTION_SIZE_BYTES;

            // Solid voxels (ship walls) block light propagation. We let the
            // emitter cell itself ignore the solid check: the emitter's host
            // block can be a torch (non-solid) or a glowstone (solid) — for
            // the host cell we still write, but we don't propagate further if
            // it's solid.
            long solidWordPtr = secPtr + SOLID_START_BYTES + (long)(voxelIdx >>> 5) * 4L;
            int solidWord = MemoryUtil.memGetInt(solidWordPtr);
            boolean isSolid = (solidWord & (1 << (voxelIdx & 31))) != 0;

            long lightBytePtr = secPtr + LIGHT_START_BYTES + voxelIdx;
            byte existing = MemoryUtil.memGetByte(lightBytePtr);
            int curLight = existing & 0xF;
            if (curLight >= l) continue;
            MemoryUtil.memPutByte(lightBytePtr, (byte) ((existing & 0xF0) | (l & 0xF)));

            if (isSolid) continue; // host cell got the value, but no propagation through solid

            int nl = l - 1;
            if (nl <= 0) continue;
            bfsX.add(x + 1); bfsY.add(y); bfsZ.add(z); bfsL.add(nl);
            bfsX.add(x - 1); bfsY.add(y); bfsZ.add(z); bfsL.add(nl);
            bfsX.add(x); bfsY.add(y + 1); bfsZ.add(z); bfsL.add(nl);
            bfsX.add(x); bfsY.add(y - 1); bfsZ.add(z); bfsL.add(nl);
            bfsX.add(x); bfsY.add(y); bfsZ.add(z + 1); bfsL.add(nl);
            bfsX.add(x); bfsY.add(y); bfsZ.add(z - 1); bfsL.add(nl);
        }
    }

    private boolean isWorldOccluder(final LevelAccessor level, final int x, final int y, final int z) {
        scratchBlockPos.set(x, y, z);
        return level.getBlockState(scratchBlockPos).canOcclude();
    }

    /** Allocate or reuse a section for {@code sectionPos}, zeroing it on first
     *  request this frame. */
    private int ensureSection(long sectionPos) {
        if (requestedThisFrame.add(sectionPos)) {
            int existing = section2Index.get(sectionPos);
            if (existing == INVALID) {
                int idx = allocate();
                section2Index.put(sectionPos, idx);
                lut.add(sectionPos, idx);
                lutDirty = true;
                MemoryUtil.memSet(arenaPtr + (long) idx * SECTION_SIZE_BYTES, 0, SECTION_SIZE_BYTES);
                changed.set(idx);
                return idx;
            } else {
                // Re-using a section from last frame: zero it before writing.
                MemoryUtil.memSet(arenaPtr + (long) existing * SECTION_SIZE_BYTES, 0, SECTION_SIZE_BYTES);
                changed.set(existing);
                return existing;
            }
        }
        return section2Index.get(sectionPos);
    }

    /** Release any sections that no ship populated this frame. */
    public void pruneUnused() {
        if (section2Index.isEmpty()) return;
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
        if (anyRemoved) lutDirty = true;
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
                GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL_R32UI(), sectionsBuffer);
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
            if (sizeInts > 0) ib.put(lutScratch.elements(), 0, sizeInts);
            ib.flip();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, lutBuffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, ib, GL15.GL_DYNAMIC_DRAW);
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL_R32UI(), lutBuffer);
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

    public boolean hasAnySections() {
        return !section2Index.isEmpty();
    }

    public int trackedSectionCount() {
        return section2Index.size();
    }

    public Set<Long> trackedSections() { return Set.copyOf(section2Index.keySet()); }

    private void ensureGlObjects() {
        if (sectionsBuffer == 0) sectionsBuffer = GL15.glGenBuffers();
        if (sectionsTexture == 0) {
            sectionsTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, sectionsTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL_R32UI(), sectionsBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
        if (lutBuffer == 0) lutBuffer = GL15.glGenBuffers();
        if (lutTexture == 0) {
            lutTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, lutTexture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL_R32UI(), lutBuffer);
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        }
    }

    private static int GL_R32UI() {
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
        for (int i = capacity; i < newCapacity; i++) free.set(i);
        capacity = newCapacity;
        currentSectionsByteSize = -1;
        for (int i = 0; i < capacity; i++) {
            if (!free.get(i)) changed.set(i);
        }
    }
}
