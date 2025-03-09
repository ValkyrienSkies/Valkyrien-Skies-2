package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ChunkTracker.class)
public class MixinChunkTracker {

    @Shadow
    @Final
    private Long2IntOpenHashMap chunkStatus;

    @Shadow
    @Final
    private LongOpenHashSet chunkReady;

    @Shadow
    @Final
    private LongSet unloadQueue;

    @Shadow
    @Final
    private LongSet loadQueue;

    @Inject(method = "updateMerged", at = @At("HEAD"), cancellable = true, remap = false)
    public void beforeUpdateMerged(final int x, final int z, final CallbackInfo ci) {
        final Level level = Minecraft.getInstance().level;
        if (level == null || !VSGameUtilsKt.isChunkInShipyard(level, x, z)) {
            return;
        }

        final long key = ChunkPos.asLong(x, z);

        final int flags = this.chunkStatus.get(key);

        if (flags == ChunkStatus.FLAG_ALL) {
            if (this.chunkReady.add(key) && !this.unloadQueue.remove(key)) {
                this.loadQueue.add(key);
            }
        } else {
            if (this.chunkReady.remove(key) && !this.loadQueue.remove(key)) {
                this.unloadQueue.add(key);
            }
        }

        ci.cancel();
    }

}
