//package org.valkyrienskies.mod.mixin.mod_compat.sodium;
//
//import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Shadow;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//import org.valkyrienskies.core.game.ChunkAllocator;
//
//@Mixin(value = ChunkRenderContainer.class, remap = false)
//public abstract class MixinChunkRenderContainer {
//
//    @Shadow
//    public abstract int getChunkX();
//
//    @Shadow
//    public abstract int getChunkZ();
//
//    /**
//     * warn: this is not good This mixin forces sodium to render ship chunks even though they don't have adjacent chunks
//     * sodium developer cortex says this is suboptimal. We should instead give sodium empty adjacent chunks, so it can
//     * calculate the right colors and stuff
//     */
//    @Inject(at = @At("HEAD"), method = "canRebuild", cancellable = true)
//    private void beforeCanRebuild(final CallbackInfoReturnable<Boolean> cir) {
//        if (ChunkAllocator.isChunkInShipyard(getChunkX(), getChunkZ())) {
//            cir.setReturnValue(true);
//        }
//    }
//}
