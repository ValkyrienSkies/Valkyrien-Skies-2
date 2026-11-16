package org.valkyrienskies.mod.compat.lod;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.compat.LoadedMods;

/**
 * Connects ship rendering to mods that draw distant terrain (LODs).
 *
 * <ul>
 *     <li>The batched ship shaders drop fragments that are behind LOD terrain, using the LOD mod's depth.</li>
 *     <li>Ships in LOD range use the LOD fog instead of the vanilla fog, which would hide them.</li>
 *     <li>{@link #renderDistanceBlocks()} is part of the player's view distance, which sets how far away the server
 *     sends distant ships.</li>
 * </ul>
 *
 * <p>Distant Horizons is the only backend so far. Another LOD mod can be added as a {@link LodRenderBackend}.
 */
public final class LodCompat {

    public static final int DEPTH_TEXTURE_UNIT = 9;

    private static final Logger LOGGER = LogManager.getLogger("Valkyrien Skies LOD compat");

    private static LodRenderBackend[] backends;
    private static long frame;

    private static long distanceFrame = Long.MIN_VALUE;
    private static int distance;
    private static LodRenderBackend distanceBackend;

    private static final LodDepth DEPTH = new LodDepth();
    private static final float[] FOG = new float[2];
    private static boolean depthBound;

    private LodCompat() {
    }

    public static void onFrameStart() {
        frame++;
        backends();
    }

    static long currentFrame() {
        return frame;
    }

    private static LodRenderBackend[] backends() {
        if (backends == null) {
            final List<LodRenderBackend> found = new ArrayList<>();
            if (LoadedMods.getDistantHorizons()) {
                try {
                    found.add(new DhLodBackend());
                    LOGGER.info("Ships will be drawn with Distant Horizons LOD depth and fog");
                } catch (final Throwable t) {
                    LOGGER.error("Could not connect to Distant Horizons. Ships will show through its LOD terrain.", t);
                }
            }
            backends = found.toArray(new LodRenderBackend[0]);
        }
        return backends;
    }

    /** Remove a backend whose mod threw, so one incompatible version does not break every frame. */
    private static void disable(final LodRenderBackend backend, final Throwable error) {
        LOGGER.error("Disabling {} compat after an error", backend.name(), error);
        final List<LodRenderBackend> kept = new ArrayList<>();
        for (final LodRenderBackend other : backends) {
            if (other != backend) kept.add(other);
        }
        backends = kept.toArray(new LodRenderBackend[0]);
        distanceFrame = Long.MIN_VALUE;
    }

    /** Furthest distance, in blocks, at which a LOD mod draws terrain. 0 without an active LOD mod. */
    public static int renderDistanceBlocks() {
        if (distanceFrame != frame) {
            distanceFrame = frame;
            distance = 0;
            distanceBackend = null;
            for (final LodRenderBackend backend : backends()) {
                try {
                    final int blocks = backend.renderDistanceBlocks();
                    if (blocks > distance) {
                        distance = blocks;
                        distanceBackend = backend;
                    }
                } catch (final Throwable t) {
                    disable(backend, t);
                    return renderDistanceBlocks();
                }
            }
        }
        return distance;
    }

    /**
     * Distance in blocks beyond which a LOD mod paints over Minecraft's image this frame (DH's vanilla fade). Ships
     * reaching past it are drawn in {@code ShipBatchRenderer.drawDeferredShips}, after that pass.
     * {@link Float#POSITIVE_INFINITY} when no LOD mod paints over anything.
     */
    public static float overpaintDistanceBlocks() {
        final Minecraft minecraft = Minecraft.getInstance();
        // With Fabulous graphics the late hook draws into the weather target, so do not defer. DH turns it off.
        if (Minecraft.useShaderTransparency()) {
            return Float.POSITIVE_INFINITY;
        }
        float distance = Float.POSITIVE_INFINITY;
        for (final LodRenderBackend backend : backends()) {
            try {
                distance = Math.min(distance, backend.overpaintDistanceBlocks(minecraft.getFrameTime()));
            } catch (final Throwable t) {
                disable(backend, t);
                return Float.POSITIVE_INFINITY;
            }
        }
        return distance;
    }

    private static boolean depthThisFrame(final LodDepth out) {
        for (final LodRenderBackend backend : backends()) {
            try {
                if (backend.depthForThisFrame(out)) return true;
            } catch (final Throwable t) {
                disable(backend, t);
                return false;
            }
        }
        return false;
    }

    /**
     * Set the LOD uniforms of a batched ship shader. Call after {@code ShaderInstance.apply()}, with the program
     * bound. Ships are only tested against LOD depth when a LOD mod drew terrain this frame.
     */
    public static void bindShipShader(final int programId) {
        final boolean enabled = VSGameConfig.CLIENT.getDistantShips().getHideShipsBehindLodTerrain();
        final boolean occlude = enabled && depthThisFrame(DEPTH);
        logOcclusionState(enabled, occlude);
        setInt(programId, "u_VsLodOcclusion", occlude ? 1 : 0);
        if (!occlude) {
            return;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + DEPTH_TEXTURE_UNIT);
        GlStateManager._bindTexture(DEPTH.textureId);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        depthBound = true;
        setInt(programId, "u_VsLodDepth", DEPTH_TEXTURE_UNIT);

        final RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
        final int viewport = GL20.glGetUniformLocation(programId, "u_VsLodViewport");
        if (viewport >= 0) GL20.glUniform2f(viewport, target.width, target.height);
        final int params = GL20.glGetUniformLocation(programId, "u_VsLodDepthParams");
        if (params >= 0) {
            GL20.glUniform4f(params, DEPTH.projectionA, DEPTH.projectionB, DEPTH.ndcScale, DEPTH.ndcBias);
        }
        final int empty = GL20.glGetUniformLocation(programId, "u_VsLodEmptyDepth");
        if (empty >= 0) GL20.glUniform1f(empty, DEPTH.emptyDepth);
    }

    private static String loggedOcclusionState = "";
    private static int occlusionLogCount;

    /** Log when ships start or stop being tested against LOD depth, with the reason, once per change. */
    private static void logOcclusionState(final boolean enabled, final boolean occlude) {
        final String state;
        if (!enabled) {
            state = "off: hideShipsBehindLodTerrain is false in the VS client config";
        } else if (occlude) {
            state = String.format("on: depth texture %d, A=%.5f B=%.5f, ndc=depth*%.0f%+.0f, empty depth %.1f",
                DEPTH.textureId, DEPTH.projectionA, DEPTH.projectionB, DEPTH.ndcScale, DEPTH.ndcBias,
                DEPTH.emptyDepth);
        } else {
            final StringBuilder reasons = new StringBuilder();
            for (final LodRenderBackend backend : backends()) {
                if (backend instanceof final DhLodBackend dh) reasons.append(dh.lastFailure);
            }
            state = "off: " + (reasons.length() == 0 ? "no LOD mod" : reasons);
        }
        if (!state.equals(loggedOcclusionState)) {
            loggedOcclusionState = state;
            if (occlusionLogCount++ < 20) LOGGER.info("Ship LOD occlusion {}", state);
        }
    }

    /** Unbind the LOD depth after the ships are drawn, so it is never bound while the LOD mod writes to it. */
    public static void unbindShipShader() {
        if (!depthBound) return;
        depthBound = false;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + DEPTH_TEXTURE_UNIT);
        GlStateManager._bindTexture(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    /**
     * Fit the ship fog to how far ships are drawn. Vanilla fog ends at the vanilla render distance and would hide
     * every ship beyond it.
     * <ul>
     *     <li>While a LOD mod is active, ships use the LOD fog, like the LOD terrain around them.</li>
     *     <li>When the ship render distance reaches past the fog (a ship render distance set beyond the terrain),
     *     the fog is stretched to end there, so the furthest ships fade in at the edge instead of vanishing.</li>
     * </ul>
     *
     * @param fog {@code fog[0]} fog start and {@code fog[1]} fog end, in blocks; changed in place
     */
    public static void adjustShipFog(final float[] fog) {
        if (!isTerrainFog()) return;
        final int blocks = renderDistanceBlocks();
        final LodRenderBackend backend = distanceBackend;
        if (VSGameConfig.CLIENT.getDistantShips().getUseLodFogForShips() && blocks > 0 && backend != null) {
            boolean lodFog;
            try {
                lodFog = backend.fogFractions(FOG);
            } catch (final Throwable t) {
                disable(backend, t);
                lodFog = false;
            }
            if (lodFog) {
                fog[0] = blocks * FOG[0];
                fog[1] = blocks * FOG[1];
            } else {
                fog[0] = Math.max(fog[0], blocks * 4.0f);
                fog[1] = Math.max(fog[1], blocks * 4.0f + 1.0f);
            }
        }
        final float shipBlocks =
            org.valkyrienskies.mod.common.render.batched.DistantShips.viewDistanceBlocks(Minecraft.getInstance());
        if (fog[1] > 0.0f && shipBlocks > fog[1]) {
            final float scale = shipBlocks / fog[1];
            fog[0] *= scale;
            fog[1] = shipBlocks;
        }
    }

    /** False under water, in lava or powder snow, with blindness or darkness, and in foggy places like the Nether. */
    private static boolean isTerrainFog() {
        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) return false;
        final Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        if (camera.getEntity() instanceof final LivingEntity living
            && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        final Vec3 position = camera.getPosition();
        return !level.effects().isFoggyAt(Mth.floor(position.x), Mth.floor(position.z))
            && !minecraft.gui.getBossOverlay().shouldCreateWorldFog();
    }

    private static void setInt(final int programId, final String name, final int value) {
        final int location = GL20.glGetUniformLocation(programId, name);
        if (location >= 0) GL20.glUniform1i(location, value);
    }
}
