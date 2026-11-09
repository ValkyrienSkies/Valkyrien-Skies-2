package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class VanillaBatchedModelRenderer implements BatchedModelRenderer {

    @Override
    public Object getModelData(final BlockAndTintGetter view, final ClientLevel level,
        final BlockPos pos, final BlockState state) {
        return null;
    }

    @Override
    public Iterable<RenderType> getRenderTypes(final BlockRenderDispatcher dispatcher,
        final BlockState state, final RandomSource random, final Object modelData) {
        return List.of(ItemBlockRenderTypes.getChunkRenderType(state));
    }

    @Override
    public void renderModel(final BlockRenderDispatcher dispatcher, final BlockState state,
        final BlockPos pos, final BlockAndTintGetter view, final PoseStack poseStack,
        final VertexConsumer consumer, final boolean checkSides, final RandomSource random,
        final Object modelData, final RenderType renderType) {
        dispatcher.renderBatched(state, pos, view, poseStack, consumer, checkSides, random);
    }
}
