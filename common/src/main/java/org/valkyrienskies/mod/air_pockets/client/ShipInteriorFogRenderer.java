package org.valkyrienskies.mod.air_pockets.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.util.FluidStateManager;

public final class ShipInteriorFogRenderer {
    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir ShipInteriorFog");
    private static final long DIAG_LOG_INTERVAL_MS = 3000L;

    private static final String INTERIOR_MASK_PASS_NAME = ValkyrienSkiesMod.MOD_ID + ":ship_interior_mask";
    private static final String EXTERIOR_FOG_PASS_NAME = ValkyrienSkiesMod.MOD_ID + ":ship_exterior_fog";
    private static final String FOG_COMPOSITE_PASS_NAME = ValkyrienSkiesMod.MOD_ID + ":ship_fog_composite";

    private static TextureTarget interiorMaskTarget;
    private static TextureTarget fogTarget;

    private static PostPass interiorMaskPass;
    private static PostPass fogPass;
    private static PostPass fogCompositePass;

    private static final BlockPos.MutableBlockPos TMP_BLOCK_POS = new BlockPos.MutableBlockPos();
    private static long lastDiagLogAtMs = 0L;
    private static long lastInteriorFogActiveAtMs = 0L;
    private static final long INTERIOR_FOG_GRACE_PERIOD_MS = 250L;
    private static long lastExteriorWaterGateUpdateAtNs = 0L;
    private static float smoothedExteriorWaterGate = 0.0f;
    private static final int PROBE_GRID_SIZE = 3;
    private static final float[] smoothedExteriorWaterGateGrid = new float[PROBE_GRID_SIZE * PROBE_GRID_SIZE];
    private static final String[] EXTERIOR_WATER_GATE_ROW_UNIFORMS = {
        "ExteriorWaterGateRow0",
        "ExteriorWaterGateRow1",
        "ExteriorWaterGateRow2"
    };

    private static final class ExteriorFluidSample {
        private final BlockPos pos;
        private final FluidState fluidState;
        private final Fluid fluid;

        private ExteriorFluidSample(final BlockPos pos, final FluidState fluidState, final Fluid fluid) {
            this.pos = pos;
            this.fluidState = fluidState;
            this.fluid = fluid;
        }
    }

    private static final class ProbeExteriorResult {
        private final float gate;
        private final ExteriorFluidSample fluidSample;

        private ProbeExteriorResult(final float gate, final ExteriorFluidSample fluidSample) {
            this.gate = gate;
            this.fluidSample = fluidSample;
        }
    }

    private ShipInteriorFogRenderer() {
    }

    public static void render(final Camera camera, final Matrix4f projectionMatrix, final Matrix4f modelViewMatrix) {
        if (!canRenderInteriorWaterFog(camera)) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final boolean inShipAirPocket = ShipWaterPocketManager.isWorldPosInShipAirPocket(
            mc.level, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z
        );
        final boolean inWorldFluidSuppressionZone = ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(
            mc.level, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z
        );
        if (!shouldRenderInteriorWaterFog(inShipAirPocket, inWorldFluidSuppressionZone)) {
//            logDiag(
//                "Skipped interior fog render: inShipAirPocket={} inWorldFluidSuppressionZone={}",
//                inShipAirPocket,
//                inWorldFluidSuppressionZone
//            );
            return;
        }

        final RenderTarget mainTarget = mc.getMainRenderTarget();
        ensureTargets(mainTarget.width, mainTarget.height);
        ensurePasses(mc, mainTarget);
        if (interiorMaskTarget == null || fogTarget == null || interiorMaskPass == null || fogPass == null
            || fogCompositePass == null) {
//            logDiag(
//                "Skipped interior fog render: targets/passes missing maskTarget={} fogTarget={} maskPass={} fogPass={} compositePass={}",
//                interiorMaskTarget != null,
//                fogTarget != null,
//                interiorMaskPass != null,
//                fogPass != null,
//                fogCompositePass != null
//            );
            return;
        }

        final int boundShips = renderInteriorMask(mc, camera, projectionMatrix, modelViewMatrix, mainTarget);
        if (boundShips <= 0) {
            //logDiag("Skipped interior fog render: no bound ships for mask");
            return;
        }

        renderFog(mainTarget, camera, projectionMatrix);
        compositeFogToMain(mainTarget);
        //logDiag("Rendered interior fog: boundShips={}", boundShips);
    }

    public static boolean shouldSuppressLiquidOverlay(final Camera camera) {
        if (!canRenderInteriorWaterFog(camera)) {
            return false;
        }
        final Minecraft mc = Minecraft.getInstance();
        return shouldRenderInteriorWaterFog(
            ShipWaterPocketManager.isWorldPosInShipAirPocket(
                mc.level, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z
            ),
            ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(
                mc.level, camera.getPosition().x, camera.getPosition().y, camera.getPosition().z
            )
        );
    }

    public static void clear() {
        closePasses();
        if (interiorMaskTarget != null) {
            interiorMaskTarget.destroyBuffers();
            interiorMaskTarget = null;
        }
        if (fogTarget != null) {
            fogTarget.destroyBuffers();
            fogTarget = null;
        }
    }

    private static void ensureTargets(final int width, final int height) {
        if (width <= 0 || height <= 0) return;

        if (interiorMaskTarget == null) {
            interiorMaskTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            interiorMaskTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (interiorMaskTarget.width != width || interiorMaskTarget.height != height) {
            interiorMaskTarget.resize(width, height, Minecraft.ON_OSX);
            interiorMaskTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        }

        if (fogTarget == null) {
            fogTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            fogTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (fogTarget.width != width || fogTarget.height != height) {
            fogTarget.resize(width, height, Minecraft.ON_OSX);
            fogTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        }
    }

    private static void ensurePasses(final Minecraft mc, final RenderTarget mainTarget) {
        if (interiorMaskPass != null && fogPass != null && fogCompositePass != null) {
            updatePassMatrices(mainTarget);
            return;
        }

        closePasses();
        interiorMaskPass = createPass(mc, INTERIOR_MASK_PASS_NAME, mainTarget, interiorMaskTarget);
        fogPass = createPass(mc, EXTERIOR_FOG_PASS_NAME, mainTarget, fogTarget);
        fogCompositePass = createPass(mc, FOG_COMPOSITE_PASS_NAME, fogTarget, mainTarget);
        if (interiorMaskPass != null && fogPass != null && fogCompositePass != null) {
            updatePassMatrices(mainTarget);
        }
    }

    private static PostPass createPass(final Minecraft mc, final String programName, final RenderTarget inTarget,
        final RenderTarget outTarget) {
        try {
            return new PostPass(mc.getResourceManager(), programName, inTarget, outTarget);
        } catch (final Exception ex) {
            LOGGER.warn("Failed to load interior fog pass {}", programName, ex);
            return null;
        }
    }

    private static void closePasses() {
        if (interiorMaskPass != null) {
            interiorMaskPass.close();
            interiorMaskPass = null;
        }
        if (fogPass != null) {
            fogPass.close();
            fogPass = null;
        }
        if (fogCompositePass != null) {
            fogCompositePass.close();
            fogCompositePass = null;
        }
    }

    private static void updatePassMatrices(final RenderTarget mainTarget) {
        if (interiorMaskPass != null && interiorMaskTarget != null) {
            interiorMaskPass.setOrthoMatrix(makeOrthoMatrix(interiorMaskTarget.width, interiorMaskTarget.height));
        }
        if (fogPass != null && fogTarget != null) {
            fogPass.setOrthoMatrix(makeOrthoMatrix(fogTarget.width, fogTarget.height));
        }
        if (fogCompositePass != null && mainTarget != null) {
            fogCompositePass.setOrthoMatrix(makeOrthoMatrix(mainTarget.width, mainTarget.height));
        }
    }

    private static Matrix4f makeOrthoMatrix(final int width, final int height) {
        return new Matrix4f().setOrtho(0.0f, (float) width, 0.0f, (float) height, 0.1f, 1000.0f);
    }

    private static void logDiag(final String message, final Object... args) {
        final long now = System.currentTimeMillis();
        if (now - lastDiagLogAtMs < DIAG_LOG_INTERVAL_MS) {
            return;
        }
        lastDiagLogAtMs = now;
        LOGGER.info(message, args);
    }

    private static int renderInteriorMask(final Minecraft mc, final Camera camera, final Matrix4f projectionMatrix,
        final Matrix4f modelViewMatrix, final RenderTarget mainTarget) {
        interiorMaskTarget.copyDepthFrom(mainTarget);
        interiorMaskTarget.bindWrite(true);
        interiorMaskTarget.clear(Minecraft.ON_OSX);

        final Matrix4f inverseProjection = new Matrix4f(projectionMatrix).invert();
        final Matrix4f inverseView = new Matrix4f(modelViewMatrix).invert();
        final EffectInstance effect = interiorMaskPass.getEffect();
        final int boundShips = ShipWaterPocketExternalWaterCull.bindInteriorVolumeSamplersAndUniforms(
            effect,
            mc.level,
            camera.getPosition().x,
            camera.getPosition().y,
            camera.getPosition().z
        );
        if (boundShips <= 0) {
            return 0;
        }

        setMat4(effect, "InverseProjMat", inverseProjection);
        setMat4(effect, "InverseViewMat", inverseView);
        setVec3(effect, "CameraWorldPos",
            (float) camera.getPosition().x,
            (float) camera.getPosition().y,
            (float) camera.getPosition().z
        );
        setVec3(effect, "CameraLookVector", camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z());
        setVec3(effect, "CameraUpVector", camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z());
        setVec3(effect, "CameraLeftVector", camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z());
        effect.setSampler("SceneDepthSampler", mainTarget::getDepthTextureId);
        interiorMaskPass.process(0.0f);
        return boundShips;
    }

    private static void renderFog(final RenderTarget mainTarget, final Camera camera, final Matrix4f projectionMatrix) {
        fogTarget.clear(Minecraft.ON_OSX);

        final EffectInstance effect = fogPass.getEffect();
        final ExteriorFluidSample fluidSample = findExteriorFluidSample(camera, projectionMatrix);
        final int fogColor = sampleExteriorFogColor(camera);
        setVec3(effect, "FogColor",
            ((fogColor >> 16) & 0xFF) / 255.0f,
            ((fogColor >> 8) & 0xFF) / 255.0f,
            (fogColor & 0xFF) / 255.0f
        );
        final float fogStrengthScale = getPotionAdjustedFogStrengthScale(fluidSample);
        final float fogDensity = (isLavaFluid(fluidSample)
            ? VSGameConfig.CLIENT.getUnderwater().getLavaFogDensity()
            : VSGameConfig.CLIENT.getUnderwater().getWaterFogDensity()) * fogStrengthScale;
        final float fogStart = 2.0f;
        setVec2(effect, "FogParams", fogDensity, fogStart);
        setFloat(effect, "SkyFogStrength", fogStrengthScale);
        final float[] gateGrid = getSmoothedExteriorWaterGateGrid(camera, projectionMatrix);
        setVec3(effect, EXTERIOR_WATER_GATE_ROW_UNIFORMS[0], gateGrid[0], gateGrid[1], gateGrid[2]);
        setVec3(effect, EXTERIOR_WATER_GATE_ROW_UNIFORMS[1], gateGrid[3], gateGrid[4], gateGrid[5]);
        setVec3(effect, EXTERIOR_WATER_GATE_ROW_UNIFORMS[2], gateGrid[6], gateGrid[7], gateGrid[8]);
        setFloat(effect, "ExteriorWaterGate", smoothedExteriorWaterGate);
        setMat4(effect, "InverseProjMat", new Matrix4f(projectionMatrix).invert());
        setVec3(effect, "CameraWorldPos",
            (float) camera.getPosition().x,
            (float) camera.getPosition().y,
            (float) camera.getPosition().z
        );
        setVec3(effect, "CameraLookVector", camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z());
        setVec3(effect, "CameraUpVector", camera.getUpVector().x(), camera.getUpVector().y(), camera.getUpVector().z());
        setVec3(effect, "CameraLeftVector", camera.getLeftVector().x(), camera.getLeftVector().y(), camera.getLeftVector().z());
        effect.setSampler("SceneDepthSampler", mainTarget::getDepthTextureId);
        effect.setSampler("InteriorMaskSampler", interiorMaskTarget::getColorTextureId);
        fogPass.process(0.0f);
    }

    private static void compositeFogToMain(final RenderTarget mainTarget) {
        fogCompositePass.process(0.0f);
        mainTarget.bindWrite(false);
    }

    private static int sampleExteriorFogColor(final Camera camera) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0x3F76E4;
        }

        final ExteriorFluidSample fluidSample = findExteriorFluidSample(camera, null);
        if (fluidSample == null) {
            TMP_BLOCK_POS.set(camera.getBlockPosition());
            return ShipWaterPocketFluidVisualHelper.getFluidFogColor(
                mc.level,
                TMP_BLOCK_POS,
                net.minecraft.world.level.material.Fluids.WATER,
                net.minecraft.world.level.material.Fluids.WATER.defaultFluidState(),
                camera
            );
        }

        return ShipWaterPocketFluidVisualHelper.getFluidFogColor(
            mc.level,
            fluidSample.pos,
            fluidSample.fluid,
            fluidSample.fluidState,
            camera
        );
    }

    private static boolean canRenderInteriorWaterFog(final Camera camera) {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) {
            //logDiag("Skipped interior fog render: air pockets disabled");
            return false;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || camera == null) {
            //logDiag("Skipped interior fog render: missing level/camera");
            return false;
        }
        final FogType cameraFogType = camera.getFluidInCamera();
        if (cameraFogType != FogType.NONE &&
            !isCameraInShipPocketRenderZone(mc.level, camera)) {
            //logDiag("Skipped interior fog render: camera fluid={}", cameraFogType);
            return false;
        }
        return true;
    }

    private static boolean isLavaFluid(final ExteriorFluidSample fluidSample) {
        if (fluidSample == null) {
            return false;
        }
        final Fluid fluid = fluidSample.fluid;
        return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA;
    }

    //todo: this should also be data driven to support things like TFC water
    private static boolean isWaterFluid(final ExteriorFluidSample fluidSample) {
        if (fluidSample == null) {
            return false;
        }
        final Fluid fluid = fluidSample.fluid;
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private static float getPotionAdjustedFogStrengthScale(final ExteriorFluidSample fluidSample) {
        if (!VSGameConfig.CLIENT.getUnderwater().getFogEffects()) {
            return 1.0f;
        }
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || fluidSample == null) {
            return 1.0f;
        }

        if (isWaterFluid(fluidSample)) {
            final float waterVision = Mth.clamp(player.getWaterVision(), 0.0f, 1.0f);
            final float vanillaWaterVisibility = Math.max(0.25f, waterVision);
            return Mth.clamp(0.25f / vanillaWaterVisibility, 0.25f, 1.0f);
        }

        if (isLavaFluid(fluidSample) && player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return 1.0f / 3.0f;
        }

        return 1.0f;
    }

    private static float[] getSmoothedExteriorWaterGateGrid(final Camera camera, final Matrix4f projectionMatrix) {
        final long nowNs = System.nanoTime();
        final float deltaSeconds;
        if (lastExteriorWaterGateUpdateAtNs == 0L) {
            deltaSeconds = 0.0f;
        } else {
            deltaSeconds = (nowNs - lastExteriorWaterGateUpdateAtNs) / 1_000_000_000.0f;
        }
        lastExteriorWaterGateUpdateAtNs = nowNs;

        final float[] targetGrid = computeExteriorWaterGateGrid(camera, projectionMatrix);
        final float riseRatePerSecond = 8.0f;
        final float fallRatePerSecond = 1.25f;
        float sum = 0.0f;
        float max = 0.0f;
        for (int i = 0; i < smoothedExteriorWaterGateGrid.length; i++) {
            final float targetGate = targetGrid[i];
            final float maxDelta =
                (targetGate > smoothedExteriorWaterGateGrid[i] ? riseRatePerSecond : fallRatePerSecond) * deltaSeconds;
            smoothedExteriorWaterGateGrid[i] = Mth.approach(smoothedExteriorWaterGateGrid[i], targetGate, maxDelta);
            sum += smoothedExteriorWaterGateGrid[i];
            max = Math.max(max, smoothedExteriorWaterGateGrid[i]);
        }
        smoothedExteriorWaterGate = Math.max(sum / smoothedExteriorWaterGateGrid.length, max * 0.9f);
        return smoothedExteriorWaterGateGrid.clone();
    }

    private static float[] computeExteriorWaterGateGrid(final Camera camera, final Matrix4f projectionMatrix) {
        final Matrix4f inverseProjection =
            projectionMatrix != null ? new Matrix4f(projectionMatrix).invert() : null;
        final float[] grid = new float[PROBE_GRID_SIZE * PROBE_GRID_SIZE];
        for (int row = 0; row < PROBE_GRID_SIZE; row++) {
            for (int col = 0; col < PROBE_GRID_SIZE; col++) {
                final float u = col / (float) (PROBE_GRID_SIZE - 1);
                final float v = 1.0f - row / (float) (PROBE_GRID_SIZE - 1);
                final ProbeExteriorResult result = findExteriorFluidSampleForScreenUv(camera, inverseProjection, u, v);
                grid[row * PROBE_GRID_SIZE + col] = result != null ? result.gate : 0.0f;
            }
        }
        return grid;
    }

    private static ExteriorFluidSample findExteriorFluidSample(final Camera camera, final Matrix4f projectionMatrix) {
        final Matrix4f inverseProjection =
            projectionMatrix != null ? new Matrix4f(projectionMatrix).invert() : null;
        for (int row = 0; row < PROBE_GRID_SIZE; row++) {
            for (int col = 0; col < PROBE_GRID_SIZE; col++) {
                final float u = col / (float) (PROBE_GRID_SIZE - 1);
                final float v = 1.0f - row / (float) (PROBE_GRID_SIZE - 1);
                final ProbeExteriorResult result = findExteriorFluidSampleForScreenUv(camera, inverseProjection, u, v);
                if (result != null && result.fluidSample != null) {
                    return result.fluidSample;
                }
            }
        }
        return null;
    }

    private static ProbeExteriorResult findExteriorFluidSampleForScreenUv(final Camera camera, final Matrix4f inverseProjection,
        final float u, final float v) {
        final Vector3f look = computeProbeDirection(camera, inverseProjection, u, v);
        if (look == null || look.lengthSquared() <= 1.0e-6f) {
            return null;
        }
        return findExteriorFluidSampleForDirection(camera, look);
    }

    private static Vector3f computeProbeDirection(final Camera camera, final Matrix4f inverseProjection, final float u,
        final float v) {
        if (inverseProjection == null) {
            final Vector3f look = new Vector3f(camera.getLookVector());
            final Vector3f up = new Vector3f(camera.getUpVector());
            final Vector3f left = new Vector3f(camera.getLeftVector());
            return new Vector3f(look)
                .fma((v - 0.5f) * 0.7f, up)
                .fma((u - 0.5f) * 0.5f, left)
                .normalize();
        }

        final Vector4f clipPos = new Vector4f(u * 2.0f - 1.0f, v * 2.0f - 1.0f, 1.0f, 1.0f);
        inverseProjection.transform(clipPos);
        if (Math.abs(clipPos.w) <= 1.0e-6f) {
            return null;
        }
        clipPos.div(clipPos.w);

        return new Vector3f(camera.getLookVector()).mul(-clipPos.z)
            .fma(clipPos.y, new Vector3f(camera.getUpVector()))
            .fma(-clipPos.x, new Vector3f(camera.getLeftVector()))
            .normalize();
    }

    private static ProbeExteriorResult findExteriorFluidSampleForDirection(final Camera camera, final Vector3f look) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        ShipWaterPocketLiquidOverlay.ensureExteriorFluidCacheLevel(mc.level);

        final double startX = camera.getPosition().x;
        final double startY = camera.getPosition().y;
        final double startZ = camera.getPosition().z;
        final double maxTraceDistance = 64.0;
        final double dryStep = 0.25;
        final double outsideSampleOffset = 0.2;
        final double surfaceSlack = 0.1;
        final BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        final FluidStateManager.QueryCache fluidQueryCache = new FluidStateManager.QueryCache();

        double exitDistance = -1.0;
        for (double travel = 0.0; travel <= maxTraceDistance; travel += dryStep) {
            final double sampleX = startX + look.x() * travel;
            final double sampleY = startY + look.y() * travel;
            final double sampleZ = startZ + look.z() * travel;
            if (!isWorldPosInShipPocketRenderZone(mc.level, sampleX, sampleY, sampleZ)) {
                exitDistance = travel;
                break;
            }
        }
        if (exitDistance < 0.0) return null;

        final double sampleDistance = Math.min(maxTraceDistance, exitDistance + outsideSampleOffset);
        final double sampleX = startX + look.x() * sampleDistance;
        final double sampleY = startY + look.y() * sampleDistance;
        final double sampleZ = startZ + look.z() * sampleDistance;
        final ShipWaterPocketLiquidOverlay.FluidSurfaceSample surfaceSample =
            ShipWaterPocketLiquidOverlay.findExteriorFluidSurface(
                mc.level,
                fluidPos,
                scanPos,
                fluidQueryCache,
                sampleX,
                sampleY,
                sampleZ
            );
        if (surfaceSample == null || sampleY > surfaceSample.surfaceY + surfaceSlack) {
            return null;
        }

        return new ProbeExteriorResult(
            1.0f,
            new ExteriorFluidSample(surfaceSample.pos, surfaceSample.fluidState, surfaceSample.fluid)
        );
    }

    static boolean shouldRenderInteriorWaterFog(final boolean inShipAirPocket, final boolean inWorldFluidSuppressionZone) {
        final long now = System.currentTimeMillis();
        if (inShipAirPocket || inWorldFluidSuppressionZone) {
            lastInteriorFogActiveAtMs = now;
            return true;
        }
        return now - lastInteriorFogActiveAtMs <= INTERIOR_FOG_GRACE_PERIOD_MS;
    }

    private static boolean isCameraInShipPocketRenderZone(final net.minecraft.client.multiplayer.ClientLevel level,
        final Camera camera) {
        return isWorldPosInShipPocketRenderZone(
            level,
            camera.getPosition().x,
            camera.getPosition().y,
            camera.getPosition().z
        );
    }

    private static boolean isWorldPosInShipPocketRenderZone(final net.minecraft.client.multiplayer.ClientLevel level,
        final double x, final double y, final double z) {
        return ShipWaterPocketManager.isWorldPosInShipAirPocket(level, x, y, z) ||
            ShipWaterPocketManager.isWorldPosInShipWorldFluidSuppressionZone(level, x, y, z);
    }

    private static void setMat4(final EffectInstance effect, final String uniformName, final Matrix4f value) {
        final Uniform uniform = effect.getUniform(uniformName);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setVec2(final EffectInstance effect, final String uniformName, final float x, final float y) {
        final Uniform uniform = effect.getUniform(uniformName);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    private static void setVec3(final EffectInstance effect, final String uniformName, final float x, final float y,
        final float z) {
        final Uniform uniform = effect.getUniform(uniformName);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static void setFloat(final EffectInstance effect, final String uniformName, final float value) {
        final Uniform uniform = effect.getUniform(uniformName);
        if (uniform != null) {
            uniform.set(value);
        }
    }
}
