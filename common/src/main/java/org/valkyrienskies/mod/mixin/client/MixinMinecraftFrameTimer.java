package org.valkyrienskies.mod.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.render.FrameTimeTracker;

/**
 * Per-render-frame boundary hook for {@link FrameTimeTracker}. Provides
 * the render-thread timestamp stream the tracker needs to report
 * max-frame-ms / hitch counts during a benchmark window.
 *
 * <p>Production cost: one volatile read per frame ({@code isArmed} is
 * a null-check on an AtomicLong). When unarmed the hook returns
 * immediately.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFrameTimer {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void vs$markFrameBoundary(final boolean renderLevel, final CallbackInfo ci) {
        if (FrameTimeTracker.isArmed()) {
            FrameTimeTracker.onFrameBoundary();
        }
    }
}
