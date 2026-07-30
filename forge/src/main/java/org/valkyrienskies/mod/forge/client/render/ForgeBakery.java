package org.valkyrienskies.mod.forge.client.render;

import static org.valkyrienskies.mod.common.render.Bakery.bakeQuad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.valkyrienskies.mod.common.render.Bakery.BakedGeometry;
import org.valkyrienskies.mod.common.render.PlatformBakery;

public class ForgeBakery implements PlatformBakery {
    @Override
    public List<BakedGeometry> bakeBlockQuads(BlockState bs, BlockPos pos) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(bs);
        ModelData modelData = model.getModelData(Minecraft.getInstance().level, pos, bs, Minecraft.getInstance().level.getModelDataManager()
            .getAt(pos));
        if (modelData == null) {
            modelData = ModelData.EMPTY;
        }
        RandomSource random = RandomSource.create(42L); // vanilla's fixed seed for consistent quad caching

        List<BakedGeometry> baked = new ArrayList<>();

        // Cull-face-specific quads: each of the 6 directions is baked separately so the
        // resulting BakedGeometry can record which face it belongs to (cullFace), matching
        // how chunk rendering culls per-neighbor.
        for (RenderType type : model.getRenderTypes(bs, random, modelData)) {
            for (Direction dir : Direction.values()) {
                for (BakedQuad quad : model.getQuads(bs, dir, random, modelData, type)) {
                    baked.add(bakeQuad(quad, dir, bs, pos));
                }
            }
            List<BakedQuad> unculled = model.getQuads(bs, null, random, modelData, type);
            if (!unculled.isEmpty()) {
                for (BakedQuad quad : unculled) {
                    baked.add(bakeQuad(quad, null, bs, pos));
                }
            }
        }

        return baked;
    }
}
