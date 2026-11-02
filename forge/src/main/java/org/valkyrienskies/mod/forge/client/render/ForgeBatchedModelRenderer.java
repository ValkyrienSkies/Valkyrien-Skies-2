package org.valkyrienskies.mod.forge.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;
import org.valkyrienskies.mod.common.render.batched.BatchedModelRenderer;

public final class ForgeBatchedModelRenderer implements BatchedModelRenderer {

    @Override
    public Object getModelData(final BlockAndTintGetter view, final ClientLevel level,
        final BlockPos pos, final BlockState state) {
        final BakedModel model = dispatcher().getBlockModel(state);
        ModelData base = ModelData.EMPTY;
        final ModelDataManager manager = level.getModelDataManager();
        if (manager != null) {
            final ModelData cached = manager.getAt(pos);
            if (cached != null) {
                base = cached;
            }
        }
        return model.getModelData(view, pos, state, base);
    }

    @Override
    public Iterable<RenderType> getRenderTypes(final BlockRenderDispatcher dispatcher,
        final BlockState state, final RandomSource random, final Object modelData) {
        final BakedModel model = dispatcher.getBlockModel(state);
        return model.getRenderTypes(state, random, asModelData(modelData));
    }

    @Override
    public void renderModel(final BlockRenderDispatcher dispatcher, final BlockState state,
        final BlockPos pos, final BlockAndTintGetter view, final PoseStack poseStack,
        final VertexConsumer consumer, final boolean checkSides, final RandomSource random,
        final Object modelData, final RenderType renderType) {
        dispatcher.renderBatched(state, pos, view, poseStack, consumer, checkSides, random,
            asModelData(modelData), renderType);
    }

    private static ModelData asModelData(final Object modelData) {
        return modelData instanceof ModelData ? (ModelData) modelData : ModelData.EMPTY;
    }

    private static BlockRenderDispatcher dispatcher() {
        return net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
    }
}
