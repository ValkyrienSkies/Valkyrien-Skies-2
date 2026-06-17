package org.valkyrienskies.mod.mixin.accessors.client.multiplayer;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {

    @Accessor
    LevelRenderer getLevelRenderer();

    /**
     * Raw access to the light-update Runnable queue. Needed because
     * vanilla's {@code pollLightUpdates()} has an internal
     * "if size ≥ 1000 drain ALL" branch that defeats per-frame time
     * budgets — a single call can run 1000+ Runnables (multi-second
     * freeze). We need to poll one at a time with our own deadline.
     */
    @Accessor("lightUpdateQueue")
    java.util.Deque<Runnable> vs$getLightUpdateQueue();
}
