package org.valkyrienskies.mod.mixin.accessors.client.world;

import net.minecraft.client.multiplayer.ClientChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientChunkCache.Storage.class)
public interface ClientChunkCacheStorageAccessor {
    @Invoker(value = "inRange")
    boolean callInRange(int chunkX, int chunkZ);
}
