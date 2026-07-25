package org.valkyrienskies.mod.common.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
                    bakedSections.add(bake(new BlockPos(x << 4, y << 4, z << 4)));
                }
            }
        });
        return bakedSections;
    }

    public BakedSection bake(BlockPos sectionOrigin) {
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        List<PaletteEntry> palette = new ArrayList<>();
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

                    Integer paletteIdx = paletteIndex.get(state);
                    if (paletteIdx == null) {
                        List<Bakery.BakedGeometry> geometry = Bakery.bakeQuads(state, sectionOrigin.offset(x, y, z));

                        paletteIdx = palette.size();
                        paletteIndex.put(state, paletteIdx);
                        palette.add(new PaletteEntry(state, geometry));
                    }

                    blockIndices[arrIdx] = paletteIdx;
                }
            }
        }

        return new BakedSection(palette, blockIndices);
    }

    public record PaletteEntry(BlockState state, List<Bakery.BakedGeometry> geometry) {
    }

    public record BakedSection(List<PaletteEntry> palette, int[] blockIndices) {
        public static final int NO_BLOCK = -1;

        public static int toArrayIndex(int x, int y, int z) {
            return (y << 8) | (z << 4) | x;
        }
    }
}
