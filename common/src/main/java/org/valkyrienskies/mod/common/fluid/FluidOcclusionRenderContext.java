package org.valkyrienskies.mod.common.fluid;

import java.util.HashSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Render-thread state identifying a world chunk layer that can contain fluids.
 */
public final class FluidOcclusionRenderContext {
    private static final HashSet<RenderType> FLUID_LAYERS = new HashSet<>();
    private static boolean fluidLayersInitialized;
    private static int depth;
    private static @Nullable ClientLevel level;
    private static double cameraX;
    private static double cameraY;
    private static double cameraZ;

    private FluidOcclusionRenderContext() {
    }

    private static void ensureFluidLayersInitialized() {
        if (fluidLayersInitialized) return;
        fluidLayersInitialized = true;
        FLUID_LAYERS.clear();
        try {
            for (final Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid instanceof final FlowingFluid flowing) {
                    addFluidLayer(flowing.getSource().defaultFluidState());
                    addFluidLayer(flowing.getFlowing().defaultFluidState());
                } else if (fluid != null) {
                    addFluidLayer(fluid.defaultFluidState());
                }
            }
        } catch (final Throwable ignored) {
            FLUID_LAYERS.add(RenderType.translucent());
        }
    }

    private static void addFluidLayer(final FluidState state) {
        if (state == null || state.isEmpty()) return;
        final RenderType layer = ItemBlockRenderTypes.getRenderLayer(state);
        if (layer != null) FLUID_LAYERS.add(layer);
    }

    public static boolean isFluidLayer(final RenderType renderType) {
        ensureFluidLayersInitialized();
        return renderType != null && FLUID_LAYERS.contains(renderType);
    }

    public static void begin(
        final ClientLevel clientLevel,
        final RenderType renderType,
        final double camX,
        final double camY,
        final double camZ
    ) {
        if (clientLevel == null || !isFluidLayer(renderType)) return;
        if (depth++ == 0) {
            level = clientLevel;
            cameraX = camX;
            cameraY = camY;
            cameraZ = camZ;
        }
    }

    public static void end(final RenderType renderType) {
        if (!isFluidLayer(renderType) || depth == 0) return;
        if (--depth == 0) {
            level = null;
        }
    }

    public static boolean isActive() {
        return depth > 0 && level != null;
    }

    public static @Nullable ClientLevel getLevel() {
        return level;
    }

    public static double getCameraX() {
        return cameraX;
    }

    public static double getCameraY() {
        return cameraY;
    }

    public static double getCameraZ() {
        return cameraZ;
    }
}
