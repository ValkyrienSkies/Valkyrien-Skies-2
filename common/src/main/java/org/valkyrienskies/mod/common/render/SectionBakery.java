package org.valkyrienskies.mod.common.render;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Strategy;
import org.valkyrienskies.core.api.ships.ClientShip;

public class SectionBakery {

    // No longer a shared field — see ingestSection/bake below, each section now gets its
    // own fresh PalettedContainer so the internal palette doesn't accumulate every
    // BlockState ever seen across the whole ship's lifetime.
    private PalettedContainer<BlockState> states;

    private PalettedContainer<BlockState> newContainer() {
        return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(),
            Strategy.SECTION_STATES);
    }

    public void ingestSection(LevelChunkSection section) {
        this.states = newContainer();
        for (int x = 0; x < LevelChunkSection.SECTION_WIDTH; x++) {
            for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
                for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
                    BlockState blockState = section.getBlockState(x, y, z);
                    states.set(x, y, z, blockState);
                }
            }
        }
    }

    public List<BakedSection> ingestShip(ClientShip clientShip) {
        if (clientShip == null) {
            return new ArrayList<>();
        }
        List<BakedSection> bakedSections = new ArrayList<>();
        clientShip.getActiveChunksSet().forEach((x, z) -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                throw new IllegalStateException(
                    "ClientLevel is null. This method should only be called on the client side.");
            }
            LevelChunk chunk = level.getChunk(x, z);
            for (int y = level.getMinSection(); y < level.getMaxSection(); y++) {
                final LevelChunkSection levelChunkSection = chunk.getSection(y - level.getMinSection());
                if (!levelChunkSection.hasOnlyAir()) {
                    ingestSection(levelChunkSection);
                    bakedSections.add(bake(level, new BlockPos(x << 4, y << 4, z << 4)));
                }
            }
        });
        return bakedSections;
    }

    public BakedSection bake(Level level, BlockPos sectionOrigin) {
        // Geometry dedup key per block:
        //  - non-BE blocks: the BlockState itself. Their mesh is a pure function of state, so all
        //    same-state instances safely share one palette entry.
        //  - BE blocks: (state, NBT fingerprint). A BE's rendered mesh is BlockEntityRenderer-
        //    driven, so two same-state blocks with different NBT (fluid tanks at different levels
        //    or with different fluids, chests with vs without loot, etc.) need different meshes.
        //    Without this, the first tank baked would be reused for every tank on the ship and
        //    all the others' fluid/level would be silently dropped. Identical-NBT instances still
        //    dedupe. The position field is stripped from the fingerprint since it never changes a
        //    BE's appearance, so two identical tanks at different positions still share geometry.
        Map<Object, Integer> paletteIndex = new LinkedHashMap<>();
        List<PaletteEntry> palette = new ArrayList<>();
        // Per-instance BlockEntity NBT captured alongside the geometry. Per-instance (not deduped
        // per state): two chests share geometry but differ in contents. Keyed by the same wire
        // key the geometry palette uses for that state, so a consumer can join
        // beInstances[i].wireKey -> palette[j] to attach NBT to the right rendered model.
        List<BakedGeometrySerializer.BlockEntityEntry> beInstances = new ArrayList<>();
        int[] blockIndices = new int[LevelChunkSection.SECTION_WIDTH * LevelChunkSection.SECTION_HEIGHT *
            LevelChunkSection.SECTION_WIDTH];

        for (int x = 0; x < LevelChunkSection.SECTION_WIDTH; x++) {
            for (int y = 0; y < LevelChunkSection.SECTION_HEIGHT; y++) {
                for (int z = 0; z < LevelChunkSection.SECTION_WIDTH; z++) {
                    BlockState state = states.get(x, y, z);
                    int arrIdx = BakedSection.toArrayIndex(x, y, z);

                    if (state.isAir()) {
                        blockIndices[arrIdx] = BakedSection.NO_BLOCK;
                        continue;
                    }

                    BlockPos worldPos = sectionOrigin.offset(x, y, z);

                    BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(worldPos) : null;
                    CompoundTag nbt = be != null ? be.saveWithFullMetadata() : null;
                    // Dedup key: state for non-BE blocks; (state, fingerprint) for BE blocks. The
                    // fingerprint is over the position-stripped NBT so position doesn't defeat dedup.
                    Object dedupKey;
                    if (nbt != null) {
                        dedupKey = new StateAndFingerprint(state, nbtFingerprint(nbt));
                    } else {
                        dedupKey = state;
                    }

                    Integer paletteIdx = paletteIndex.get(dedupKey);
                    if (paletteIdx == null) {
                        List<Bakery.BakedGeometry> geometry = Bakery.bakeQuads(state, worldPos);

                        paletteIdx = palette.size();
                        paletteIndex.put(dedupKey, paletteIdx);
                        // The wire key is what the serializer keys palette entries by. For BE blocks
                        // it carries a NBT-content suffix so different-NBT instances land in distinct
                        // palette entries; non-BE blocks use the plain canonical BlockState string.
                        String wireKey = nbt != null
                            ? beWireKey(state, nbt)
                            : BakedGeometrySerializer.BlockStateKeys.canonicalKey(state);
                        palette.add(new PaletteEntry(state, geometry, wireKey));
                    }

                    blockIndices[arrIdx] = paletteIdx;

                    // Capture the full per-instance NBT (with position) for every BE block. The
                    // wireKey matches this palette entry's geometry so the consumer can join.
                    if (nbt != null) {
                        beInstances.add(new BakedGeometrySerializer.BlockEntityEntry(
                            palette.get(paletteIdx).wireKey(), nbt
                        ));
                    }
                }
            }
        }

        return new BakedSection(palette, blockIndices, beInstances);
    }

    /**
     * Canonical wire key for a BE block's palette entry: {@code canonicalKey(state) + "#" +
     * nbtFingerprint(nbt)}. Distinct NBT (different fluid level/type, different chest contents,
     * ...) produces distinct keys and therefore distinct palette entries. Multiple instances with
     * the same NBT share the same key and the same geometry — position-independent because
     * nbtFingerprint strips the position fields.
     */
    private static String beWireKey(BlockState state, CompoundTag nbt) {
        return BakedGeometrySerializer.BlockStateKeys.canonicalKey(state) + "#" + nbtFingerprint(nbt);
    }

    /**
     * Short, stable fingerprint of a BE's NBT for geometry dedup. Uses SHA-256 over the vanilla
     * NbtIo encoding with the position fields (x, y, z) removed, so two identical tanks at
     * different positions produce the same fingerprint (they render identically). The fingerprint
     * itself is opaque; the full NBT is carried separately in the BE-instance section.
     */
    private static String nbtFingerprint(CompoundTag nbt) {
        CompoundTag stripped = nbt.copy();
        stripped.remove("x");
        stripped.remove("y");
        stripped.remove("z");
        // create bs
        if (stripped.contains("Network")) {
            stripped.remove("Network");
        }
        if (stripped.contains("Source")) {
            stripped.remove("Source");
        }
        if (stripped.contains("Controller")) {
            stripped.remove("Controller");
        }
        if (stripped.contains("LastKnownPos")) {
            stripped.remove("LastKnownPos");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.write(stripped, new DataOutputStream(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fingerprint BlockEntity NBT", e);
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Dedup key pairing a BlockState with a NBT fingerprint, with value equality so a Map can
     * collapse identical-(state, fingerprint) BE blocks across a section.
     */
    private record StateAndFingerprint(BlockState state, String fingerprint) {
    }

    public record PaletteEntry(BlockState state, List<Bakery.BakedGeometry> geometry, String wireKey) {
    }

    public record BakedSection(
        List<PaletteEntry> palette,
        int[] blockIndices,
        List<BakedGeometrySerializer.BlockEntityEntry> beInstances
    ) {
        public static final int NO_BLOCK = -1;

        public static int toArrayIndex(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
    }
}
