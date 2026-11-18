package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.IdentityHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.VisibleChunkCollectorDuck;

@Mixin(value = VisibleChunkCollector.class, remap = false)
public abstract class MixinVisibleChunkCollector implements VisibleChunkCollectorDuck {
    @Shadow @Final private int frame;
    @Unique private IdentityHashMap<RenderRegion, ChunkRenderList> vs$viewLists;

    @Override
    public void vs$useViewLocalLists() {
        vs$viewLists = new IdentityHashMap<>();
    }

    @WrapOperation(method = "visit", at = @At(value = "INVOKE",
        target = "Lme/jellysquid/mods/sodium/client/render/chunk/region/RenderRegion;getRenderList()Lme/jellysquid/mods/sodium/client/render/chunk/lists/ChunkRenderList;"))
    private ChunkRenderList vs$getViewRenderList(RenderRegion region, Operation<ChunkRenderList> original) {
        if (vs$viewLists == null) return original.call(region);
        return vs$viewLists.computeIfAbsent(region, key -> {
            final ChunkRenderList list = new ChunkRenderList(key);
            list.reset(frame - 1);
            return list;
        });
    }
}
