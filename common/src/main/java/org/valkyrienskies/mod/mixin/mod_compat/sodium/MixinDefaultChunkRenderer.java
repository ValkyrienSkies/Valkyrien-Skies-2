package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(DefaultChunkRenderer.class)
public abstract class MixinDefaultChunkRenderer {
    @Inject(
        method = "getVisibleFaces",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void cancelBlockFaceCulling(final int originX, final int originY, final int originZ, final int chunkX, final int chunkY, final int chunkZ, final CallbackInfoReturnable<Integer> cir) {
        if(VSGameUtilsKt.isChunkInShipyard(Minecraft.getInstance().level, chunkX, chunkZ))
            cir.setReturnValue(ModelQuadFacing.ALL);
    }
}
