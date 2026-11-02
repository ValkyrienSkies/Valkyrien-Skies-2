package org.valkyrienskies.mod.air_pockets.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

/**
 * Render-thread state for the external-world water culling shader integration.
 *
 * <p>We avoid direct INVOKE-based injections into chunk-layer render methods because other renderers can overwrite those
 * methods. Instead, we track when a world *fluid* chunk layer is being rendered (water/lava/modded fluids can choose
 * different render layers) and update uniforms when the patched chunk shader is actually applied
 * (see {@code MixinShaderInstance}).
 */
public final class ShipWaterPocketExternalWaterCullRenderContext {

    private ShipWaterPocketExternalWaterCullRenderContext() {}

    private static int worldFluidDepth = 0;
    private static int shipRenderDepth = 0;
    private static @Nullable ClientLevel level = null;
    private static double camX;
    private static double camY;
    private static double camZ;

    private static boolean fluidChunkLayersInitialized = false;
    private static final HashSet<RenderType> FLUID_CHUNK_LAYERS = new HashSet<>();

    private static final int MAX_WORLD_STACK_DEPTH = 8;
    private static final @Nullable ClientLevel[] LEVEL_STACK = new ClientLevel[MAX_WORLD_STACK_DEPTH];
    private static final double[] CAM_X_STACK = new double[MAX_WORLD_STACK_DEPTH];
    private static final double[] CAM_Y_STACK = new double[MAX_WORLD_STACK_DEPTH];
    private static final double[] CAM_Z_STACK = new double[MAX_WORLD_STACK_DEPTH];

    private static void ensureFluidChunkLayersInitialized() {
        if (fluidChunkLayersInitialized) return;
        fluidChunkLayersInitialized = true;
        FLUID_CHUNK_LAYERS.clear();
        try {
            for (final Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid == null) continue;
                if (fluid instanceof final FlowingFluid flowing) {
                    addFluidChunkLayer(flowing.getSource().defaultFluidState());
                    addFluidChunkLayer(flowing.getFlowing().defaultFluidState());
                } else {
                    addFluidChunkLayer(fluid.defaultFluidState());
                }
            }
        } catch (final Throwable ignored) {
            // Fall back to vanilla translucent if something goes wrong.
            FLUID_CHUNK_LAYERS.add(RenderType.translucent());
        }
    }

    private static void addFluidChunkLayer(final FluidState state) {
        if (state == null || state.isEmpty()) return;
        final RenderType layer = ItemBlockRenderTypes.getRenderLayer(state);
        if (layer != null) FLUID_CHUNK_LAYERS.add(layer);
    }

    public static boolean isFluidChunkLayer(final RenderType renderType) {
        if (renderType == null) return false;
        ensureFluidChunkLayersInitialized();
        return FLUID_CHUNK_LAYERS.contains(renderType);
    }

    public static void beginWorldFluidChunkLayer(final ClientLevel level, final RenderType renderType, final double camX,
        final double camY, final double camZ) {
        if (level == null) return;
        if (!isFluidChunkLayer(renderType)) return;

        if (ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth < MAX_WORLD_STACK_DEPTH) {
            final int idx = ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth;
            LEVEL_STACK[idx] = ShipWaterPocketExternalWaterCullRenderContext.level;
            CAM_X_STACK[idx] = ShipWaterPocketExternalWaterCullRenderContext.camX;
            CAM_Y_STACK[idx] = ShipWaterPocketExternalWaterCullRenderContext.camY;
            CAM_Z_STACK[idx] = ShipWaterPocketExternalWaterCullRenderContext.camZ;
        }

        ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth++;
        ShipWaterPocketExternalWaterCullRenderContext.level = level;
        ShipWaterPocketExternalWaterCullRenderContext.camX = camX;
        ShipWaterPocketExternalWaterCullRenderContext.camY = camY;
        ShipWaterPocketExternalWaterCullRenderContext.camZ = camZ;
    }

    public static void endWorldFluidChunkLayer() {
        ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth--;
        if (ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth <= 0) {
            ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth = 0;
            ShipWaterPocketExternalWaterCullRenderContext.level = null;
            ShipWaterPocketExternalWaterCullRenderContext.camX = 0.0;
            ShipWaterPocketExternalWaterCullRenderContext.camY = 0.0;
            ShipWaterPocketExternalWaterCullRenderContext.camZ = 0.0;
            return;
        }

        final int idx = ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth;
        if (idx >= 0 && idx < MAX_WORLD_STACK_DEPTH) {
            ShipWaterPocketExternalWaterCullRenderContext.level = LEVEL_STACK[idx];
            ShipWaterPocketExternalWaterCullRenderContext.camX = CAM_X_STACK[idx];
            ShipWaterPocketExternalWaterCullRenderContext.camY = CAM_Y_STACK[idx];
            ShipWaterPocketExternalWaterCullRenderContext.camZ = CAM_Z_STACK[idx];
            LEVEL_STACK[idx] = null;
        }
    }

    public static void beginShipRender() {
        ShipWaterPocketExternalWaterCullRenderContext.shipRenderDepth++;
    }

    public static void endShipRender() {
        ShipWaterPocketExternalWaterCullRenderContext.shipRenderDepth--;
        if (ShipWaterPocketExternalWaterCullRenderContext.shipRenderDepth < 0) {
            ShipWaterPocketExternalWaterCullRenderContext.shipRenderDepth = 0;
        }
    }

    public static void clear() {
        ShipWaterPocketExternalWaterCullRenderContext.worldFluidDepth = 0;
        ShipWaterPocketExternalWaterCullRenderContext.level = null;
        ShipWaterPocketExternalWaterCullRenderContext.camX = 0.0;
        ShipWaterPocketExternalWaterCullRenderContext.camY = 0.0;
        ShipWaterPocketExternalWaterCullRenderContext.camZ = 0.0;
        java.util.Arrays.fill(LEVEL_STACK, null);
        ShipWaterPocketExternalWaterCullRenderContext.shipRenderDepth = 0;
        fluidChunkLayersInitialized = false;
        FLUID_CHUNK_LAYERS.clear();
    }

    public static boolean isInWorldFluidChunkLayer() {
        return worldFluidDepth > 0;
    }

    public static boolean isInShipRender() {
        return shipRenderDepth > 0;
    }

    public static @Nullable ClientLevel getLevel() {
        return level;
    }

    public static double getCamX() {
        return camX;
    }

    public static double getCamY() {
        return camY;
    }

    public static double getCamZ() {
        return camZ;
    }
}
