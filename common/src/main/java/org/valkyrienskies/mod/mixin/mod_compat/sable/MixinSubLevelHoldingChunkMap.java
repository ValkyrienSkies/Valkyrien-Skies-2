package org.valkyrienskies.mod.mixin.mod_compat.sable;

import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SubLevelHoldingChunkMap.class)
public class MixinSubLevelHoldingChunkMap {
    @Shadow
    @Final
    private ServerLevel level;

    //don't let sable save and load sublevel pos in VS chunks, avoid creating unnecessary file
    @Inject(method = "updateChunkStatus", at = @At("HEAD"), cancellable = true, require = 0) //don't cause issue if it break so not required
    private void dontSavePosInsideVSShip(ChunkPos chunkPos, boolean loaded, CallbackInfo ci) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkPos.x, chunkPos.z))
            ci.cancel();
    }
}
