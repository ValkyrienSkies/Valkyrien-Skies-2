package org.valkyrienskies.mod.mixin.client.world;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Caps the per-call batch size of {@link ClientLevel#pollLightUpdates}.
 *
 * <p>Vanilla's pollLightUpdates:
 * <pre>
 *   int size = lightUpdateQueue.size();
 *   int limit = size >= 1000 ? size : Math.max(10, size / 10);
 *   // runs 'limit' Runnables sequentially
 * </pre>
 *
 * <p>On a 1000-ship spawn, the queue accumulates ~5000 Runnables (one per
 * ship chunk packet). ClientLevel.tick calls pollLightUpdates once per
 * tick; with size ≥ 1000, vanilla's branch drains ALL of them — ~5 s of
 * ship relight work packed into one frame ('max frame 7.6 s' in the
 * client benchmark, happening at +23 s after the initial drain).
 *
 * <p>This mixin caps the inner limit at 50 via @ModifyVariable on the
 * local, small enough that each call stays under ~100 ms even when each
 * Runnable is a ~2 ms ship relight. Queue drains over more ticks; max
 * frame drops from multi-second to ~100 ms.
 */
//note: leave this alone for now, idk if its safe
@Mixin(ClientLevel.class)
public abstract class MixinClientLevelPollCap {

    @ModifyVariable(
        method = "pollLightUpdates",
        at = @At("STORE"),
        ordinal = 1
    )
    private int vs$capPollLimit(final int limit) {
        // Cap at 3. The tick runs on the render thread in 1.21 and each
        // runnable is a full ship-chunk relight at 30-100 ms, so the old
        // cap of 10 meant a tick frame could eat 300-1000 ms of render
        // time — creating the visible "chop every 50 ms" that the user
        // reported during mass spawn. At cap=3, tick frames max out
        // around 90-300 ms (still a hitch, but far less frequent per
        // second).
        //
        // Earlier benchmark concern (cap=5 made things worse because
        // work moved to the render-frame drain) no longer applies: the
        // render-frame drain is capped at 1 runnable/frame in
        // MixinLevelRendererVanilla, so cutting tick throughput doesn't
        // pile work up on the render-frame path any more than before.
        // Net throughput: 3×20 TPS + 1×60 FPS = 120 runnables/sec,
        // still drains 1000 ships in ~8 s.
        return Math.min(limit, 3);
    }
}
