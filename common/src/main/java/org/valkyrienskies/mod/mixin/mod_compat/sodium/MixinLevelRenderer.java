package org.valkyrienskies.mod.mixin.mod_compat.sodium;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import me.jellysquid.mods.sodium.client.world.WorldRendererExtended;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.LoadedMods.FlywheelVersion;
import org.valkyrienskies.mod.compat.flywheel.FlywheelDynLightCompat;
import org.valkyrienskies.mod.compat.flywheel.ShipEmbeddingManager;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.mixinducks.client.render.NestedShipView;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.SodiumWorldRendererDuck;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer implements NestedShipView {
    @Shadow
    private @Nullable ClientLevel level;

    @Override
    public void vs$renderNestedShipView(final Runnable draw) {
        final var renderer = ((WorldRendererExtended) this).sodium$getWorldRenderer();
        if (renderer instanceof SodiumWorldRendererDuck duck) duck.vs$renderNestedShipView(draw);
        else draw.run();
    }

    /**
     * Updating world-to-ship, ship-to-world dynamic lighting and dynamic biome tint storages require
     * movement of ship or block update in either ship or world.
     * These changes only happen per game tick, not per frame. Updating per game tick disables dynamic
     * lighting/tinting interpolation over time, but the cost of update per frame is too much.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void updateDynamicLight(CallbackInfo ci) {
        Minecraft.getInstance().getProfiler().push("vs_dynamic_lighting");
        try {
            SodiumCompat.populateWorldFromShipsForFrame(level);
            SodiumCompat.populateLightSectionStorage(level);
            SodiumCompat.populateBiomeSectionStorage(level);
            if (LoadedMods.getFlywheel() != FlywheelVersion.NONE) {
                FlywheelDynLightCompat.updateDynamicLightingForFlywheel(level);
            }
        } finally {
            Minecraft.getInstance().getProfiler().pop();
        }
    }
}
