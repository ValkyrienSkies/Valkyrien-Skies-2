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
    private static final int TRANSLUCENT_LAYER_INDEX = ShipSectionMesh.layerIndex(RenderType.translucent());

    private final ChunkBufferBuilderPack buffers = new ChunkBufferBuilderPack();
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final ShipShadeRemovingGetter shadeRemovingView = new ShipShadeRemovingGetter();
    private final OffsetVertexConsumer offsetConsumer = new OffsetVertexConsumer();
    private final PoseStack poseStack = new PoseStack();
    private final boolean[] startedOpaque = new boolean[LAYER_COUNT];

    /** The output of one section. It owns its CPU vertex bytes and its translucent buffer. */
    static final class CompiledSection implements AutoCloseable {
        /** Opaque vertex bytes per layer index, relative to the ship reference point. Null when empty. */
        final ByteBuffer[] opaque = new ByteBuffer[LAYER_COUNT];
        /** Translucent geometry relative to the section origin, drawn per section for sorting. */
        ShipSectionMesh translucent;

        boolean isEmpty() {
            if (translucent != null) return false;
            for (final ByteBuffer data : opaque) {
                if (data != null) return false;
            }
            return true;
        }

        @Override
        public void close() {
            for (int i = 0; i < opaque.length; i++) {
                if (opaque[i] != null) {
                    MemoryUtil.memFree(opaque[i]);
                    opaque[i] = null;
                }
            }
            if (translucent != null) {
                translucent.close();
                translucent = null;
            }
        }
    }

    /**
     * Re-tessellate the given sections and store the results in {@code sections}, replacing and
     * freeing any previous entry. Sections that are now empty are removed.
     *
     * @param changedLayers set to true for every opaque layer whose merged buffer must be rebuilt
     */
    void compileSections(final ClientLevel level, final BlockRenderDispatcher dispatcher, final ClientShip ship,
        final ShipBiomeTintCache biomeTints, final LongIterable sectionPositions,
        final int refX, final int refY, final int refZ,
        final Long2ObjectMap<CompiledSection> sections, final boolean[] changedLayers) {

        final BlockAndTintGetter renderView = shadeRemovingView.forShip(level, ship, biomeTints);
        ModelBlockRenderer.enableCaching();
        try {
            final var it = sectionPositions.iterator();
            while (it.hasNext()) {
                final long secPos = it.nextLong();
                final CompiledSection compiled =
                    compileSection(level, dispatcher, renderView, secPos, refX, refY, refZ);
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
            if (section.opaque[i] != null) changedLayers[i] = true;
        }
    }

    /** Merge the cached opaque bytes of every section into one GPU buffer for one layer. */
    VertexBuffer mergeOpaqueLayer(final int layerIndex, final Iterable<CompiledSection> sections) {
        final RenderType layer = ShipSectionMesh.CHUNK_LAYERS[layerIndex];
        final BufferBuilder builder = buffers.builder(layer);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (final CompiledSection section : sections) {
            final ByteBuffer data = section.opaque[layerIndex];
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

    private CompiledSection compileSection(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final BlockAndTintGetter renderView, final long secPos, final int refX, final int refY, final int refZ) {

        final int sx = SectionPos.x(secPos);
        final int sy = SectionPos.y(secPos);
        final int sz = SectionPos.z(secPos);

        final LevelChunk chunk = level.getChunk(sx, sz);
        final int sectionIndex = level.getSectionIndexFromSectionY(sy);
        final LevelChunkSection[] allSections = chunk.getSections();
        if (sectionIndex < 0 || sectionIndex >= allSections.length) {
            return null;
        }
        final LevelChunkSection section = allSections[sectionIndex];
        if (section.hasOnlyAir()) {
            return null;
        }

        final RenderType translucentLayer = RenderType.translucent();
        final int originX = sx << 4;
        final int originY = sy << 4;
        final int originZ = sz << 4;
        final int offX = originX - refX;
        final int offY = originY - refY;
        final int offZ = originZ - refZ;

        boolean startedTranslucent = false;
        java.util.Arrays.fill(startedOpaque, false);

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
                        final RenderType fluidLayer = ItemBlockRenderTypes.getRenderLayer(fluid);
                        if (fluidLayer == translucentLayer) {
                            final BufferBuilder builder = beginTranslucent(translucentLayer, startedTranslucent);
                            startedTranslucent = true;
                            dispatcher.renderLiquid(scratchPos, renderView, builder, state, fluid);
                        } else {
                            final BufferBuilder builder = beginOpaque(fluidLayer);
                            if (builder != null) {
                                final VertexConsumer consumer = offsetConsumer.set(builder, offX, offY, offZ);
                                dispatcher.renderLiquid(scratchPos, renderView, consumer, state, fluid);
                            }
                        }
                    }

                    if (hasModel) {
                        final Object modelData = BatchedModelRenderer.INSTANCE
                            .getModelData(renderView, level, scratchPos, state);
                        random.setSeed(state.getSeed(scratchPos));
                        for (final RenderType blockLayer : BatchedModelRenderer.INSTANCE
                            .getRenderTypes(dispatcher, state, random, modelData)) {
                            final BufferBuilder builder;
                            if (blockLayer == translucentLayer) {
                                builder = beginTranslucent(translucentLayer, startedTranslucent);
                                startedTranslucent = true;
                                poseStack.pushPose();
                                poseStack.translate(lx, ly, lz);
                            } else {
                                builder = beginOpaque(blockLayer);
                                if (builder == null) {
                                    continue;
                                }
                                poseStack.pushPose();
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
            if (!startedOpaque[i]) continue;
            final BufferBuilder.RenderedBuffer rendered =
                buffers.builder(ShipSectionMesh.CHUNK_LAYERS[i]).endOrDiscardIfEmpty();
            if (rendered == null) continue;
            try {
                final ByteBuffer source = rendered.vertexBuffer();
                final ByteBuffer copy = MemoryUtil.memAlloc(source.remaining());
                MemoryUtil.memCopy(source, copy);
                compiled.opaque[i] = copy;
            } finally {
                rendered.release();
            }
        }

        if (startedTranslucent) {
            final BufferBuilder.RenderedBuffer rendered = buffers.builder(translucentLayer).endOrDiscardIfEmpty();
            if (rendered != null) {
                final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(rendered);
                VertexBuffer.unbind();
                final ShipSectionMesh mesh = new ShipSectionMesh(originX, originY, originZ);
                mesh.setBuffer(TRANSLUCENT_LAYER_INDEX, vb);
                compiled.translucent = mesh;
            }
        }

        if (compiled.isEmpty()) {
            return null;
        }
        return compiled;
    }

    /** Begin the layer's builder for this section, or return null for a layer the batched path does not draw. */
    private BufferBuilder beginOpaque(final RenderType layer) {
        final int layerIndex = ShipSectionMesh.layerIndex(layer);
        if (layerIndex < 0 || layerIndex == TRANSLUCENT_LAYER_INDEX) {
            return null;
        }
        final BufferBuilder builder = buffers.builder(layer);
        if (!startedOpaque[layerIndex]) {
            startedOpaque[layerIndex] = true;
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }

    private BufferBuilder beginTranslucent(final RenderType translucentLayer, final boolean alreadyStarted) {
        final BufferBuilder builder = buffers.builder(translucentLayer);
        if (!alreadyStarted) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }
}
