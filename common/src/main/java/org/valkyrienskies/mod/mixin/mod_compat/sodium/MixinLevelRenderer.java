package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion;
import org.valkyrienskies.mod.compat.flywheel.FlywheelDynLightCompat;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.common.render.light.ShipLightUpdateScheduler;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Shadow
    private @Nullable ClientLevel level;

    @Unique
    private final ShipLightUpdateScheduler vs$shipLightUpdates = new ShipLightUpdateScheduler();

    /** Request a ship light update. Keep the other light and biome updates on the client tick. */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void updateDynamicLight(CallbackInfo ci) {
        vs$shipLightUpdates.request();
        Minecraft.getInstance().getProfiler().push("vs_dynamic_lighting");
        try {
            SodiumCompat.populateLightSectionStorage(level);
            SodiumCompat.populateBiomeSectionStorage(level);
            if (LoadedMods.getFlywheel() != FlywheelVersion.NONE) {
                FlywheelDynLightCompat.updateDynamicLightingForFlywheel(level);
            }
        } finally {
            Minecraft.getInstance().getProfiler().pop();
        }
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vs$updateShipLightBeforeRender(final CallbackInfo ci) {
        // Catch-up ticks must not rebuild the same light grid before a frame can use it.
        if (vs$shipLightUpdates.beginFrame(level)) SodiumCompat.populateWorldFromShipsForFrame(level);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void vs$clearPendingShipLight(final CallbackInfo ci) {
        vs$shipLightUpdates.clear();
    }
}
