package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.HashSet;
import java.util.Set;
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

public final class ShipSectionCompiler {

    private static final int TRANSLUCENT_LAYER_INDEX = ShipSectionMesh.layerIndex(RenderType.translucent());

    private final ChunkBufferBuilderPack buffers = new ChunkBufferBuilderPack();
    private final RandomSource random = RandomSource.create();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();
    private final ShipShadeRemovingGetter shadeRemovingView = new ShipShadeRemovingGetter();
    private final OffsetVertexConsumer offsetConsumer = new OffsetVertexConsumer();

    public ShipMesh compileShip(final ClientLevel level, final BlockRenderDispatcher dispatcher,
        final LongList sectionPositions, final int refX, final int refY, final int refZ) {

        final BlockAndTintGetter renderView = shadeRemovingView.forLevel(level);
        final PoseStack poseStack = new PoseStack();
        final RenderType translucentLayer = RenderType.translucent();
        final Set<RenderType> startedOpaque = new HashSet<>();
        final Long2ObjectMap<ShipSectionMesh> translucentSections = new Long2ObjectOpenHashMap<>();

        ModelBlockRenderer.enableCaching();
        try {
            for (int s = 0; s < sectionPositions.size(); s++) {
                final long secPos = sectionPositions.getLong(s);
                final int sx = SectionPos.x(secPos);
                final int sy = SectionPos.y(secPos);
                final int sz = SectionPos.z(secPos);

                final LevelChunk chunk = level.getChunk(sx, sz);
                final int sectionIndex = level.getSectionIndexFromSectionY(sy);
                final LevelChunkSection[] allSections = chunk.getSections();
                if (sectionIndex < 0 || sectionIndex >= allSections.length) {
                    continue;
                }
                final LevelChunkSection section = allSections[sectionIndex];
                if (section.hasOnlyAir()) {
                    continue;
                }

                final int originX = sx << 4;
                final int originY = sy << 4;
                final int originZ = sz << 4;
                final int offX = originX - refX;
                final int offY = originY - refY;
                final int offZ = originZ - refZ;

                boolean startedTranslucent = false;

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
                                    final BufferBuilder builder =
                                        beginSectionTranslucent(translucentLayer, startedTranslucent);
                                    startedTranslucent = true;
                                    dispatcher.renderLiquid(scratchPos, renderView, builder, state, fluid);
                                } else {
                                    final BufferBuilder builder = beginOpaque(fluidLayer, startedOpaque);
                                    final VertexConsumer consumer = offsetConsumer.set(builder, offX, offY, offZ);
                                    dispatcher.renderLiquid(scratchPos, renderView, consumer, state, fluid);
                                }
                            }

                            if (hasModel) {
                                final RenderType blockLayer = ItemBlockRenderTypes.getChunkRenderType(state);
                                if (blockLayer == translucentLayer) {
                                    final BufferBuilder builder =
                                        beginSectionTranslucent(translucentLayer, startedTranslucent);
                                    startedTranslucent = true;
                                    poseStack.pushPose();
                                    poseStack.translate(lx, ly, lz);
                                    dispatcher.renderBatched(state, scratchPos, renderView, poseStack, builder, true, random);
                                    poseStack.popPose();
                                } else {
                                    final BufferBuilder builder = beginOpaque(blockLayer, startedOpaque);
                                    poseStack.pushPose();
                                    poseStack.translate(offX + lx, offY + ly, offZ + lz);
                                    dispatcher.renderBatched(state, scratchPos, renderView, poseStack, builder, true, random);
                                    poseStack.popPose();
                                }
                            }
                        }
                    }
                }

                if (startedTranslucent) {
                    final BufferBuilder builder = buffers.builder(translucentLayer);
                    final BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
                    if (rendered != null) {
                        final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                        vb.bind();
                        vb.upload(rendered);
                        final ShipSectionMesh mesh = new ShipSectionMesh(originX, originY, originZ);
                        mesh.setBuffer(TRANSLUCENT_LAYER_INDEX, vb);
                        translucentSections.put(secPos, mesh);
                    }
                }
            }

            final VertexBuffer[] opaque = new VertexBuffer[ShipSectionMesh.CHUNK_LAYERS.length];
            for (final RenderType layer : startedOpaque) {
                final int layerIndex = ShipSectionMesh.layerIndex(layer);
                final BufferBuilder builder = buffers.builder(layer);
                final BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
                if (rendered == null) {
                    continue;
                }
                if (layerIndex < 0) {
                    rendered.release();
                    continue;
                }
                final VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(rendered);
                opaque[layerIndex] = vb;
            }
            VertexBuffer.unbind();

            return new ShipMesh(opaque, translucentSections, refX, refY, refZ);
        } finally {
            ModelBlockRenderer.clearCache();
        }
    }

    private BufferBuilder beginOpaque(final RenderType layer, final Set<RenderType> startedOpaque) {
        final BufferBuilder builder = buffers.builder(layer);
        if (startedOpaque.add(layer)) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }

    private BufferBuilder beginSectionTranslucent(final RenderType translucentLayer, final boolean alreadyStarted) {
        final BufferBuilder builder = buffers.builder(translucentLayer);
        if (!alreadyStarted) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        return builder;
    }
}
