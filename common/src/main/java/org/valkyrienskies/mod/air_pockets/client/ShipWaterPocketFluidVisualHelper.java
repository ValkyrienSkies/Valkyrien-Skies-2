package org.valkyrienskies.mod.air_pockets.client;

import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Shared fluid visual lookup for air-pocket rendering.
 *
 * <p>Both the world-fluid culling mask and the ship overlay need to resolve the same client-side fluid sprites and
 * tint colors across vanilla, Forge, and Fabric.
 */
public final class ShipWaterPocketFluidVisualHelper {

    private ShipWaterPocketFluidVisualHelper() {}

    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW = new ResourceLocation("minecraft", "block/water_flow");
    private static final ResourceLocation WATER_OVERLAY = new ResourceLocation("minecraft", "block/water_overlay");
    private static final ResourceLocation LAVA_STILL = new ResourceLocation("minecraft", "block/lava_still");
    private static final ResourceLocation LAVA_FLOW = new ResourceLocation("minecraft", "block/lava_flow");

    private static boolean forgeFluidTexturesChecked = false;
    private static Method forgeFluidExtOf = null;
    private static Method forgeGetStill0 = null;
    private static Method forgeGetFlow0 = null;
    private static Method forgeGetOverlay0 = null;
    private static Method forgeGetStill3 = null;
    private static Method forgeGetFlow3 = null;
    private static Method forgeGetOverlay3 = null;
    private static Method forgeGetTint0 = null;
    private static Method forgeGetTint3 = null;
    private static Method forgeModifyFogColor6 = null;

    private static boolean fabricFluidTexturesChecked = false;
    private static Object fabricFluidRenderHandlerRegistry = null;
    private static Method fabricRegistryGetHandler = null;
    private static Method fabricHandlerGetSprites = null;
    private static Method fabricHandlerGetColor = null;

    private static final int VANILLA_LAVA_FOG_COLOR = rgbFromFloats(0.6f, 0.1f, 0.0f);

    public static final class FluidVisual {
        private final Fluid fluid;
        private final FluidState fluidState;
        private final TextureAtlasSprite sprite;
        private final int tintRgb;

        private FluidVisual(final Fluid fluid, final FluidState fluidState, final TextureAtlasSprite sprite, final int tintRgb) {
            this.fluid = fluid;
            this.fluidState = fluidState;
            this.sprite = sprite;
            this.tintRgb = tintRgb;
        }

        public Fluid getFluid() {
            return this.fluid;
        }

        public FluidState getFluidState() {
            return this.fluidState;
        }

        public TextureAtlasSprite getSprite() {
            return this.sprite;
        }

        public int getTintRgb() {
            return this.tintRgb;
        }
    }

    public static FluidVisual resolveFluidVisual(final ClientLevel level, final BlockPos pos, final @Nullable FluidState sampledState,
        final @Nullable Fluid fallbackFluid) {
        Fluid fluid = Fluids.WATER;
        FluidState fluidState = Fluids.WATER.defaultFluidState();

        if (sampledState != null && !sampledState.isEmpty()) {
            fluid = canonicalSource(sampledState.getType());
            fluidState = sampledState;
        } else if (fallbackFluid != null && fallbackFluid != Fluids.EMPTY) {
            fluid = canonicalSource(fallbackFluid);
            fluidState = fluid.defaultFluidState();
        }

        final TextureAtlasSprite[] sprites = getFluidSprites(level, pos, fluid, fluidState);
        TextureAtlasSprite sprite = pickPreferredSprite(fluid, sprites);
        if (sprite == null) {
            final Function<ResourceLocation, TextureAtlasSprite> atlas =
                Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
            sprite = atlas.apply(WATER_OVERLAY);
        }

        return new FluidVisual(fluid, fluidState, sprite, getFluidTintColor(level, pos, fluid, fluidState));
    }

    public static TextureAtlasSprite[] getFluidSprites(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        final Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final ResourceLocation[] textureIds = getFluidTextureIds(level, pos, fluid, fluidState);
        if (textureIds != null) {
            return new TextureAtlasSprite[] {
                textureIds[0] != null ? atlas.apply(textureIds[0]) : null,
                textureIds[1] != null ? atlas.apply(textureIds[1]) : null,
                textureIds[2] != null ? atlas.apply(textureIds[2]) : null
            };
        }

        final Fluid canonical = canonicalSource(fluid);
        if (canonical == Fluids.WATER) {
            return new TextureAtlasSprite[] {atlas.apply(WATER_STILL), atlas.apply(WATER_FLOW), atlas.apply(WATER_OVERLAY)};
        }
        if (canonical == Fluids.LAVA) {
            return new TextureAtlasSprite[] {atlas.apply(LAVA_STILL), atlas.apply(LAVA_FLOW), null};
        }
        return new TextureAtlasSprite[] {atlas.apply(WATER_STILL), atlas.apply(WATER_FLOW), atlas.apply(WATER_OVERLAY)};
    }

    public static @Nullable ResourceLocation[] getFluidTextureIds(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        final Fluid canonical = canonicalSource(fluid);

        final ResourceLocation[] forge = queryForgeFluidTextures(level, pos, canonical, fluidState);
        if (forge != null) return forge;

        final TextureAtlasSprite[] fabric = queryFabricFluidSprites(level, pos, canonical, fluidState);
        if (fabric != null) {
            final ResourceLocation still = fabric.length > 0 && fabric[0] != null ? fabric[0].contents().name() : null;
            final ResourceLocation flow = fabric.length > 1 && fabric[1] != null ? fabric[1].contents().name() : still;
            return new ResourceLocation[] {still, flow, null};
        }

        if (canonical == Fluids.WATER) {
            return new ResourceLocation[] {WATER_STILL, WATER_FLOW, WATER_OVERLAY};
        }
        if (canonical == Fluids.LAVA) {
            return new ResourceLocation[] {LAVA_STILL, LAVA_FLOW, null};
        }
        return null;
    }

    public static int getFluidTintColor(final ClientLevel level, final BlockPos pos, final Fluid fluid, final FluidState fluidState) {
        final Fluid canonical = canonicalSource(fluid);
        if (canonical == Fluids.WATER) {
            return BiomeColors.getAverageWaterColor(level, pos);
        }
        if (canonical == Fluids.LAVA) {
            return 0xFF6A00;
        }

        final Integer forgeTint = queryForgeFluidTint(level, pos, canonical, fluidState);
        if (forgeTint != null) return forgeTint.intValue() & 0xFFFFFF;

        final Integer fabricTint = queryFabricFluidTint(level, pos, canonical, fluidState);
        if (fabricTint != null) return fabricTint.intValue() & 0xFFFFFF;

        return 0xFFFFFF;
    }

    public static int getFluidFogColor(final ClientLevel level, final BlockPos pos, final Fluid fluid, final FluidState fluidState,
        final @Nullable Camera camera) {
        final Fluid canonical = canonicalSource(fluid);
        if (canonical == Fluids.WATER) {
            return level.getBiome(pos).value().getWaterFogColor() & 0xFFFFFF;
        }
        if (canonical == Fluids.LAVA) {
            return VANILLA_LAVA_FOG_COLOR;
        }

        final Integer forgeFog = queryForgeFluidFogColor(level, pos, canonical, fluidState, camera);
        if (forgeFog != null) {
            return forgeFog.intValue() & 0xFFFFFF;
        }

        return getFluidTintColor(level, pos, canonical, fluidState);
    }

    public static @Nullable TextureAtlasSprite pickPreferredSprite(final Fluid fluid, final @Nullable TextureAtlasSprite[] sprites) {
        if (sprites == null) return null;
        final int preferredIndex = preferredSpriteIndex(
            canonicalSource(fluid),
            sprites.length > 0 && sprites[0] != null,
            sprites.length > 1 && sprites[1] != null,
            sprites.length > 2 && sprites[2] != null
        );
        return preferredIndex >= 0 ? sprites[preferredIndex] : null;
    }

    static int preferredSpriteIndex(final Fluid fluid, final boolean hasStill, final boolean hasFlow, final boolean hasOverlay) {
        if (fluid == Fluids.WATER && hasOverlay) return 2;
        if (hasStill) return 0;
        if (hasOverlay) return 2;
        if (hasFlow) return 1;
        return -1;
    }

    private static Fluid canonicalSource(final Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    private static @Nullable ResourceLocation[] queryForgeFluidTextures(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        if (!ensureForgeFluidTextureAccess()) return null;
        try {
            final Object ext = forgeFluidExtOf.invoke(null, fluid);
            if (ext == null) return null;

            final ResourceLocation still = invokeTexture(ext, forgeGetStill0, forgeGetStill3, fluidState, level, pos);
            final ResourceLocation flow = invokeTexture(ext, forgeGetFlow0, forgeGetFlow3, fluidState, level, pos);
            final ResourceLocation overlay = invokeTexture(ext, forgeGetOverlay0, forgeGetOverlay3, fluidState, level, pos);

            if (still == null && flow == null && overlay == null) return null;
            return new ResourceLocation[] {still, flow, overlay};
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Integer queryForgeFluidTint(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        if (!ensureForgeFluidTextureAccess()) return null;
        try {
            final Object ext = forgeFluidExtOf.invoke(null, fluid);
            if (ext == null) return null;
            if (forgeGetTint3 != null) {
                return (Integer) forgeGetTint3.invoke(ext, fluidState, level, pos);
            }
            if (forgeGetTint0 != null) {
                return (Integer) forgeGetTint0.invoke(ext);
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    private static @Nullable Integer queryForgeFluidFogColor(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState, final @Nullable Camera camera) {
        if (!ensureForgeFluidTextureAccess() || forgeModifyFogColor6 == null || camera == null) return null;
        try {
            final Object ext = forgeFluidExtOf.invoke(null, fluid);
            if (ext == null) return null;

            final int fallbackRgb = getFluidTintColor(level, pos, fluid, fluidState);
            final Vector3f baseColor = new Vector3f(
                ((fallbackRgb >> 16) & 0xFF) / 255.0f,
                ((fallbackRgb >> 8) & 0xFF) / 255.0f,
                (fallbackRgb & 0xFF) / 255.0f
            );
            final Minecraft mc = Minecraft.getInstance();
            final int renderDistance = mc.options != null ? mc.options.renderDistance().get() * 16 : 0;
            final Object result = forgeModifyFogColor6.invoke(ext, camera, 0.0f, level, renderDistance, 0.0f, baseColor);
            if (result instanceof final Vector3f fogColor) {
                return rgbFromFloats(fogColor.x(), fogColor.y(), fogColor.z());
            }
        } catch (final Throwable ignored) {
        }
        return null;
    }

    private static boolean ensureForgeFluidTextureAccess() {
        if (forgeFluidTexturesChecked) return forgeFluidExtOf != null;
        forgeFluidTexturesChecked = true;
        try {
            final Class<?> extClass = Class.forName("net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions");
            forgeFluidExtOf = extClass.getMethod("of", Fluid.class);

            forgeGetStill0 = findMethod(extClass, "getStillTexture");
            forgeGetFlow0 = findMethod(extClass, "getFlowingTexture");
            forgeGetOverlay0 = findMethod(extClass, "getOverlayTexture");
            forgeGetTint0 = findMethod(extClass, "getTintColor");

            forgeGetStill3 = findMethod(extClass, "getStillTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeGetFlow3 = findMethod(extClass, "getFlowingTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeGetOverlay3 = findMethod(extClass, "getOverlayTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeGetTint3 = findMethod(extClass, "getTintColor", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeModifyFogColor6 = findMethod(
                extClass,
                "modifyFogColor",
                Camera.class,
                float.class,
                ClientLevel.class,
                int.class,
                float.class,
                Vector3f.class
            );
            return true;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static @Nullable TextureAtlasSprite[] queryFabricFluidSprites(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        if (!ensureFabricFluidTextureAccess()) return null;
        try {
            final Object handler = fabricRegistryGetHandler.invoke(fabricFluidRenderHandlerRegistry, fluid);
            if (handler == null) return null;
            return (TextureAtlasSprite[]) fabricHandlerGetSprites.invoke(handler, level, pos, fluidState);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Integer queryFabricFluidTint(final ClientLevel level, final BlockPos pos, final Fluid fluid,
        final FluidState fluidState) {
        if (!ensureFabricFluidTextureAccess()) return null;
        try {
            final Object handler = fabricRegistryGetHandler.invoke(fabricFluidRenderHandlerRegistry, fluid);
            if (handler == null || fabricHandlerGetColor == null) return null;
            return (Integer) fabricHandlerGetColor.invoke(handler, level, pos, fluidState);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static boolean ensureFabricFluidTextureAccess() {
        if (fabricFluidTexturesChecked) return fabricFluidRenderHandlerRegistry != null;
        fabricFluidTexturesChecked = true;
        try {
            final Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry");
            fabricFluidRenderHandlerRegistry = registryClass.getField("INSTANCE").get(null);
            fabricRegistryGetHandler = registryClass.getMethod("get", Fluid.class);

            final Class<?> handlerClass = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler");
            fabricHandlerGetSprites = handlerClass.getMethod(
                "getFluidSprites",
                BlockAndTintGetter.class,
                BlockPos.class,
                FluidState.class
            );
            fabricHandlerGetColor = findMethod(handlerClass, "getFluidColor", BlockAndTintGetter.class, BlockPos.class, FluidState.class);
            return true;
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static @Nullable Method findMethod(final Class<?> owner, final String name, final Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static @Nullable ResourceLocation invokeTexture(final Object ext, final @Nullable Method zeroArg,
        final @Nullable Method contextual, final FluidState fluidState, final BlockAndTintGetter getter, final BlockPos pos) throws Exception {
        if (contextual != null) {
            final Object value = contextual.invoke(ext, fluidState, getter, pos);
            if (value instanceof final ResourceLocation location) return location;
        }
        if (zeroArg != null) {
            final Object value = zeroArg.invoke(ext);
            if (value instanceof final ResourceLocation location) return location;
        }
        return null;
    }

    private static int rgbFromFloats(final float red, final float green, final float blue) {
        final int redInt = Math.max(0, Math.min(255, Math.round(red * 255.0f)));
        final int greenInt = Math.max(0, Math.min(255, Math.round(green * 255.0f)));
        final int blueInt = Math.max(0, Math.min(255, Math.round(blue * 255.0f)));
        return (redInt << 16) | (greenInt << 8) | blueInt;
    }
}
