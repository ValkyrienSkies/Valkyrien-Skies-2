package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkTracker;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkTracker.class, remap = false)
public class MixinChunkTracker {
    @Shadow
    @Final
    private Long2IntOpenHashMap single;

    @Inject(method = "onLightDataAdded", at = @At("HEAD"), cancellable = true)
    private void cancelDataLight(final int x, final int z, final CallbackInfo ci) {
        final long key = ChunkPos.asLong(x, z);
        final int existingFlags = this.single.get(key);
        if ((existingFlags & 1) == 0) {
            ci.cancel(); // Cancel instead of throwing an error for now
        }
    }
}
