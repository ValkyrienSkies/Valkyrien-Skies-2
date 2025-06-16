package org.valkyrienskies.mod.mixin.accessors.server.level;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Invoker("clearCache")
    void callClearCache();
}
