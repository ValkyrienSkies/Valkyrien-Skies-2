package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterable;
import java.nio.ByteBuffer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import org.lwjgl.system.MemoryUtil;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.mixin.accessors.client.render.BufferBuilderAccessor;

public final class ShipSectionCompiler {

    static final int LAYER_COUNT = ShipSectionMesh.CHUNK_LAYERS.length;

    private final ChunkBufferBuilderPack buffers = new ChunkBufferBuilderPack();
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final ShipShadeRemovingGetter shadeRemovingView = new ShipShadeRemovingGetter();
    private final OffsetVertexConsumer offsetConsumer = new OffsetVertexConsumer();
    private final PoseStack poseStack = new PoseStack();
    private final boolean[] startedMerged = new boolean[LAYER_COUNT];
    private final boolean[] startedSorted = new boolean[LAYER_COUNT];

    /** The output of one section. It owns its CPU vertex bytes and its translucent buffer. */
    static final class CompiledSection implements AutoCloseable {
        /** Vertex bytes of the merged layers, relative to the ship reference point. Null when empty. */
        final ByteBuffer[] merged = new ByteBuffer[LAYER_COUNT];
        /** Geometry of the sorted layers ({@link ShipSectionMesh#isSorted}), relative to the section origin. */
        ShipSectionMesh sorted;

        boolean isEmpty() {
            if (sorted != null) return false;
            for (final ByteBuffer data : merged) {
                if (data != null) return false;
            }
            return true;
        }

        @Override
        public void close() {
            for (int i = 0; i < merged.length; i++) {
                if (merged[i] != null) {
                    MemoryUtil.memFree(merged[i]);
                    merged[i] = null;
                }
            }
            if (sorted != null) {
                sorted.close();
                sorted = null;
            }
        }
    }

    /**
     * Re-tessellate the given sections and store the results in {@code sections}, replacing and
     * freeing any previous entry. Sections that are now empty are removed.
     *
     * @param changedLayers set to true for every merged layer whose buffer must be rebuilt
     */
    void compileSections(final ClientLevel level, final BlockRenderDispatcher dispatcher, final ClientShip ship,
        final ShipBiomeTintCache biomeTints, final LongIterable sectionPositions,
        final int refX, final int refY, final int refZ,
        final Long2ObjectMap<CompiledSection> sections, final boolean[] changedLayers) {
        compileSections(level, dispatcher, shadeRemovingView.forShip(level, ship, biomeTints),
            secPos -> loadedSection(level, secPos), sectionPositions, refX, refY, refZ, sections, changedLayers);
    }

    @FunctionalInterface
    interface SectionSource {
        LevelChunkSection get(long sectionPos);
    }

    /**
     * Like {@link #compileSections(ClientLevel, BlockRenderDispatcher, ClientShip, ShipBiomeTintCache, LongIterable,
     * int, int, int, Long2ObjectMap, boolean[])}, reading blocks from {@code source} and models' neighbors, light and
     * tint from {@code renderView}. Distant ships use this with their block snapshot.
     */
    void compileSections(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final BlockAndTintGetter renderView, final SectionSource source, final LongIterable sectionPositions,
        final int refX, final int refY, final int refZ,
        final Long2ObjectMap<CompiledSection> sections, final boolean[] changedLayers) {

        ModelBlockRenderer.enableCaching();
        try {
            final var it = sectionPositions.iterator();
            while (it.hasNext()) {
                final long secPos = it.nextLong();
                final CompiledSection compiled =
                    compileSection(level, dispatcher, renderView, source.get(secPos), secPos, refX, refY, refZ);
                final CompiledSection old = compiled == null ? sections.remove(secPos) : sections.put(secPos, compiled);
                markChanged(old, changedLayers);
                markChanged(compiled, changedLayers);
                if (old != null) {
                    old.close();
                }
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    private static void markChanged(final CompiledSection section, final boolean[] changedLayers) {
        if (section == null) return;
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (section.merged[i] != null) changedLayers[i] = true;
        }
    }

    /** Merge the cached bytes of every section into one GPU buffer for one merged layer. */
    VertexBuffer mergeLayer(final int layerIndex, final Iterable<CompiledSection> sections) {
        final RenderType layer = ShipSectionMesh.CHUNK_LAYERS[layerIndex];
        final BufferBuilder builder = buffers.builder(layer);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (final CompiledSection section : sections) {
            final ByteBuffer data = section.merged[layerIndex];
            if (data != null) {
                appendVertices(builder, data);
            }
        }
        final BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered == null) {
            return null;
        }
        final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        return vb;
    }

    /** Append whole vertices in the builder's format. Same as Forge's BufferBuilder.putBulkData(ByteBuffer). */
    private static void appendVertices(final BufferBuilder builder, final ByteBuffer data) {
        final BufferBuilderAccessor access = (BufferBuilderAccessor) builder;
        final int bytes = data.limit();
        access.vs$ensureCapacity(bytes + DefaultVertexFormat.BLOCK.getVertexSize());
        final ByteBuffer target = access.vs$getBuffer();
        final int start = access.vs$getNextElementByte();
        MemoryUtil.memCopy(MemoryUtil.memAddress(data, 0), MemoryUtil.memAddress(target, start), bytes);
        access.vs$setVertices(access.vs$getVertices() + bytes / DefaultVertexFormat.BLOCK.getVertexSize());
        access.vs$setNextElementByte(start + bytes);
    }

    private static LevelChunkSection loadedSection(final ClientLevel level, final long secPos) {
        final LevelChunk chunk = level.getChunk(SectionPos.x(secPos), SectionPos.z(secPos));
        final int sectionIndex = level.getSectionIndexFromSectionY(SectionPos.y(secPos));
        final LevelChunkSection[] allSections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= allSections.length) {
            return null;
        }
        return allSections[sectionIndex];
    }

    private CompiledSection compileSection(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final BlockAndTintGetter renderView, final LevelChunkSection section, final long secPos,
        final int refX, final int refY, final int refZ) {

        if (section == null || section.hasOnlyAir()) {
            return null;
        }
        final int sx = SectionPos.x(secPos);
        final int sy = SectionPos.y(secPos);
        final int sz = SectionPos.z(secPos);

        final int originX = sx << 4;
        final int originY = sy << 4;
        final int originZ = sz << 4;
        final int offX = originX - refX;
        final int offY = originY - refY;
        final int offZ = originZ - refZ;

        java.util.Arrays.fill(startedMerged, false);
        java.util.Arrays.fill(startedSorted, false);

        for (int ly = 0; ly < 16; ly++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    final BlockState state = section.getBlockState(lx, ly, lz);
                    final FluidState fluid = state.getFluidState();
                    final boolean hasFluid = !fluid.isEmpty();
                    final boolean hasModel = state.getRenderShape() == RenderShape.MODEL;
                    if (!hasFluid && !hasModel) {
                        continue;
                    }
                    scratchPos.set(originX + lx, originY + ly, originZ + lz);

                    if (hasFluid) {
                        final int fluidLayer = ShipSectionMesh.layerIndex(ItemBlockRenderTypes.getRenderLayer(fluid));
                        if (ShipSectionMesh.isSorted(fluidLayer)) {
                            dispatcher.renderLiquid(scratchPos, renderView, beginSorted(fluidLayer), state, fluid);
                        } else if (fluidLayer >= 0) {
                            final VertexConsumer consumer =
                                offsetConsumer.set(beginMerged(fluidLayer), offX, offY, offZ);
                            dispatcher.renderLiquid(scratchPos, renderView, consumer, state, fluid);
                        }
                    }

                    if (hasModel) {
                        final Object modelData = BatchedModelRenderer.INSTANCE
                            .getModelData(renderView, level, scratchPos, state);
                        random.setSeed(state.getSeed(scratchPos));
                        for (final RenderType blockLayer : BatchedModelRenderer.INSTANCE
                            .getRenderTypes(dispatcher, state, random, modelData)) {
                            final int layerIndex = ShipSectionMesh.layerIndex(blockLayer);
                            if (layerIndex < 0) {
                                continue; // a layer the batched path does not draw
                            }
                            final BufferBuilder builder;
                            poseStack.pushPose();
                            if (ShipSectionMesh.isSorted(layerIndex)) {
                                builder = beginSorted(layerIndex);
                                poseStack.translate(lx, ly, lz);
                            } else {
                                builder = beginMerged(layerIndex);
                                poseStack.translate(offX + lx, offY + ly, offZ + lz);
                            }
                            BatchedModelRenderer.INSTANCE.renderModel(dispatcher, state, scratchPos,
                                renderView, poseStack, builder, true, random, modelData, blockLayer);
                            poseStack.popPose();
                        }
                    }
                }
            }
        }

        final CompiledSection compiled = new CompiledSection();
        for (int i = 0; i < LAYER_COUNT; i++) {
            if (!startedMerged[i] && !startedSorted[i]) continue;
            final BufferBuilder.RenderedBuffer rendered =
                buffers.builder(ShipSectionMesh.CHUNK_LAYERS[i]).endOrDiscardIfEmpty();
            if (rendered == null) continue;
            if (startedSorted[i]) {
                final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(rendered);
                VertexBuffer.unbind();
                if (compiled.sorted == null) {
                    compiled.sorted = new ShipSectionMesh(originX, originY, originZ);
                }
                compiled.sorted.setBuffer(i, vb);
                continue;
            }
            try {
                final ByteBuffer source = rendered.vertexBuffer();
                final ByteBuffer copy = MemoryUtil.memAlloc(source.remaining());
                MemoryUtil.memCopy(source, copy);
                compiled.merged[i] = copy;
            } finally {
                rendered.release();
            }
        }

        if (compiled.isEmpty()) {
            return null;
        }
        return compiled;
    }

    private BufferBuilder beginMerged(final int layerIndex) {
        return begin(layerIndex, startedMerged);
    }

    private BufferBuilder beginSorted(final int layerIndex) {
        return begin(layerIndex, startedSorted);
    }

    /** The section's builder for a layer, begun on first use. */
    private BufferBuilder begin(final int layerIndex, final boolean[] started) {
        final BufferBuilder builder = buffers.builder(ShipSectionMesh.CHUNK_LAYERS[layerIndex]);
        if (!started[layerIndex]) {
            started[layerIndex] = true;
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }
}
