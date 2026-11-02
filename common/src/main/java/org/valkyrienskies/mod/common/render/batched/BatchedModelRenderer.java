package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ServiceLoader;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public interface BatchedModelRenderer {

    BatchedModelRenderer INSTANCE = load();

    static BatchedModelRenderer load() {
        return ServiceLoader.load(BatchedModelRenderer.class, BatchedModelRenderer.class.getClassLoader())
            .findFirst()
            .orElseGet(VanillaBatchedModelRenderer::new);
    }

    Object getModelData(BlockAndTintGetter view, ClientLevel level, BlockPos pos, BlockState state);

    Iterable<RenderType> getRenderTypes(BlockRenderDispatcher dispatcher, BlockState state,
        RandomSource random, Object modelData);

    void renderModel(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos,
        BlockAndTintGetter view, PoseStack poseStack, VertexConsumer consumer, boolean checkSides,
        RandomSource random, Object modelData, RenderType renderType);
}
