package org.valkyrienskies.mod.common.render;

import net.minecraft.client.Minecraft;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import net.minecraft.client.multiplayer.ClientLevel;
import org.valkyrienskies.mod.common.config.ShipRenderer;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.compat.flywheel.FlywheelCompat;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.compat.sodium.SodiumCompat;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;

/** Apply config changes before a render path can use old meshes or light data. */
public final class ShipRenderConfig {
    private record State(ShipRenderer renderer, boolean shade, boolean biome, boolean light,
        boolean emitters, boolean shadows, boolean tint, boolean shaderPack, boolean terrainForFlywheel, double bakedShade) { }
    private static ClientLevel currentLevel;
    private static State previous;
    private static ClientLevel flywheelMaskLevel;

    public static void requestFlywheelMask(final ClientLevel level) {
        flywheelMaskLevel = level;
    }

    private ShipRenderConfig() { }

    public static boolean useTerrainForFlywheelHull() {
        return previous != null && previous.terrainForFlywheel();
    }

    public static void update(final ClientLevel level) {
        final var config = VSGameConfig.CLIENT;
        if (flywheelMaskLevel != level || !ShipRenderViews.hasOverlayListeners()) flywheelMaskLevel = null;
        final boolean shaderPack = LoadedMods.getIris() && IrisCompat.isIrisShaderActive();
        final State state = new State(ShipRendererKt.resolveShipRenderer(config.getDefaultRenderer()),
            config.getBetterVanillaShipShading(), config.getDynamicShipBiomeTinting(),
            config.getDynamicShipLighting(), config.isShipToWorldLightingEnabled(),
            config.getShipShadows() && config.getShipShadowStrength() > 0.0,
            config.getBlockTinting().getFixBlockTinting(),
            shaderPack, !shaderPack && LoadedMods.getFlywheel() == LoadedMods.FlywheelVersion.V1 &&
                (ShipRenderViews.hasMaterialSelectors() || (level != null && flywheelMaskLevel == level)),
            config.getBetterVanillaShipShading() ? 0.0 : config.getShipShadeStrength());
        final boolean sameLevel = currentLevel == level;
        final boolean configChanged = previous != null && !previous.equals(state);
        final boolean changed = previous != null && (!sameLevel || configChanged);
        currentLevel = level;
        previous = state;
        if (changed) {
            onRendererReload();
            if (configChanged || level == null) ShipBatchRenderer.INSTANCE.freeAll();
            if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
                SodiumCompat.deleteStorages();
            } else {
                VsDynamicLight.deleteStorages();
            }
            if (sameLevel && level != null) Minecraft.getInstance().levelRenderer.allChanged();
        }
        if ((changed || state.renderer() == ShipRenderer.FLYWHEEL)
            && LoadedMods.getFlywheel() == LoadedMods.FlywheelVersion.V1) {
            FlywheelCompat.INSTANCE.refreshShipRendering(level);
        }
    }

    public static void onRendererReload() {
        if (LoadedMods.getFlywheel() == LoadedMods.FlywheelVersion.V1) {
            FlywheelCompat.INSTANCE.clearShipRendering();
        }
    }
}
