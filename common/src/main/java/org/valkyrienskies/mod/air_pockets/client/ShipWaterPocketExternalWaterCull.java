package org.valkyrienskies.mod.air_pockets.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.Uniform;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.primitives.AABBdc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncRuntime;
import org.valkyrienskies.mod.common.air_pockets.ShipPocketAsyncSubsystem;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketAsyncCull;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketClientCullBridge;
import org.valkyrienskies.mod.common.air_pockets.ShipWaterPocketManager;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * Updates uniforms/samplers for the patched {@code rendertype_translucent} shader to cull *world* fluid surfaces inside
 * ship interiors (air pockets) without affecting ship-rendered fluids.
 */
public final class ShipWaterPocketExternalWaterCull {

    private ShipWaterPocketExternalWaterCull() {}

    private static final Logger LOGGER = LogManager.getLogger("ValkyrienAir ShipWaterCull");

    // Upper bound for ship-mask slots supported by the patched shaders.
    // it cannot be truly "uncapped" because we still need one texture unit per ship slot + 1 for the fluid mask, and
    // we must stay within GlStateManager's tracked texture-unit range to avoid crashes.
    //
    // With BASE_MASK_TEX_UNIT=2 and GLSTATEMANAGER_SAFE_TEXTURE_UNITS=12, the maximum safe mask slots is 9.
    private static final int MAX_SHIPS = 9;
    private static final int SUB = 8;
    private static final int OCC_WORDS_PER_VOXEL = (SUB * SUB * SUB) / 32; // 512 bits / 32 = 16

    // Must match shader constants (power-of-two).
    private static final int MASK_TEX_WIDTH = 4096;
    private static final int MASK_TEX_WIDTH_MASK = MASK_TEX_WIDTH - 1;
    private static final int MASK_TEX_WIDTH_SHIFT = 12;

    // Texture units 0/1 are used by Embeddium chunk shaders (block + light).
    //
    // Use 2 (right after block/light). We still clamp to the GlStateManager-tracked texture unit range to avoid
    // crashes (see GLSTATEMANAGER_SAFE_TEXTURE_UNITS).
    private static final int BASE_MASK_TEX_UNIT = 2;
    // Minecraft/GlStateManager in 1.20.1 tracks a fixed small texture-unit array; exceeding it can crash (see AIOOBE in
    // GlStateManager._bindTexture). Keep our internal usage within this cap.
    private static final int GLSTATEMANAGER_SAFE_TEXTURE_UNITS = 12;

    private static final ResourceLocation WATER_STILL = new ResourceLocation("minecraft", "block/water_still");
    private static final ResourceLocation WATER_FLOW = new ResourceLocation("minecraft", "block/water_flow");
    private static final ResourceLocation WATER_OVERLAY = new ResourceLocation("minecraft", "block/water_overlay");

    private static final ResourceLocation LAVA_STILL = new ResourceLocation("minecraft", "block/lava_still");
    private static final ResourceLocation LAVA_FLOW = new ResourceLocation("minecraft", "block/lava_flow");

    private static int fluidMaskTexId = 0;
    private static int fluidMaskWidth = 0;
    private static int fluidMaskHeight = 0;
    private static int fluidMaskLastAtlasTexId = 0;
    private static byte[] fluidMaskData = null;
    private static ByteBuffer fluidMaskBuffer = null;
    private static boolean loggedFluidMaskBuildFailed = false;
    private static CompletableFuture<byte[]> pendingFluidMaskFuture = null;
    private static int pendingFluidMaskAtlasTexId = 0;
    private static int pendingFluidMaskWidth = 0;
    private static int pendingFluidMaskHeight = 0;

    private static boolean forgeFluidTexturesChecked = false;
    private static Method forgeFluidExtOf = null;
    private static Method forgeGetStill0 = null;
    private static Method forgeGetFlow0 = null;
    private static Method forgeGetOverlay0 = null;
    private static Method forgeGetStill3 = null;
    private static Method forgeGetFlow3 = null;
    private static Method forgeGetOverlay3 = null;

    private static boolean fabricFluidTexturesChecked = false;
    private static Object fabricFluidRenderHandlerRegistry = null;
    private static Method fabricRegistryGetHandler = null;
    private static Method fabricHandlerGetSprites = null;

    private static final Matrix4f IDENTITY_MAT4 = new Matrix4f();
    private static final long INTERIOR_VOLUME_DIAG_LOG_INTERVAL_MS = 3000L;

    private static ClientLevel lastLevel = null;
    private static long lastInteriorVolumeDiagLogAtMs = 0L;

    private static final class ShipMasks {
        private final long shipId;
        private long geometryRevision;

        private int minX;
        private int minY;
        private int minZ;
        private int sizeX;
        private int sizeY;
        private int sizeZ;

        private int maskTexId;
        private int maskTexHeight;
        private long lastMaskUploadRevision = Long.MIN_VALUE;

        private final Matrix4f worldToShip = new Matrix4f();

        private int[] maskData;
        private byte[] maskBytes;
        private ByteBuffer maskByteBuffer;
        private CompletableFuture<int[]> pendingMaskWordsFuture;
        private long pendingMaskBuildRevision = Long.MIN_VALUE;

        private ShipMasks(final long shipId) {
            this.shipId = shipId;
        }

        private void close() {
            if (pendingMaskWordsFuture != null) {
                pendingMaskWordsFuture.cancel(true);
                pendingMaskWordsFuture = null;
            }
            if (isLiveTextureId(maskTexId)) {
                TextureUtil.releaseTextureId(maskTexId);
            }
            maskTexId = 0;
        }
    }

    private static final Map<Long, ShipMasks> SHIP_MASKS = new HashMap<>();

    private static final class ProgramHandles {
        private final int programId;
        private boolean supported = false;
        private boolean embeddiumChunkProgram = false;

        private int regionOffsetLoc = -1;
        private int blockTexLoc = -1;

        private int cullEnabledLoc = -1;
        private int isShipPassLoc = -1;
        private int cameraWorldPosLoc = -1;
        private int waterStillUvLoc = -1;
        private int waterFlowUvLoc = -1;
        private int waterOverlayUvLoc = -1;
        private int fluidMaskLoc = -1;
        private int shipWaterTintEnabledLoc = -1;
        private int shipWaterTintLoc = -1;
        private int chunkWorldOriginLoc = -1;

        private int maxMaskSlots = MAX_SHIPS;
        private int maxSafeTextureUnits = GLSTATEMANAGER_SAFE_TEXTURE_UNITS;

        private final int[] shipAabbMinLoc = new int[MAX_SHIPS];
        private final int[] shipAabbMaxLoc = new int[MAX_SHIPS];
        private final int[] cameraShipPosLoc = new int[MAX_SHIPS];
        private final int[] gridMinLoc = new int[MAX_SHIPS];
        private final int[] gridSizeLoc = new int[MAX_SHIPS];
        private final int[] worldToShipLoc = new int[MAX_SHIPS];
        private final int[] maskLoc = new int[MAX_SHIPS];
        private final boolean[] shipSlotSupported = new boolean[MAX_SHIPS];

        private ProgramHandles(final int programId) {
            this.programId = programId;
        }
    }

    private static final Int2ObjectOpenHashMap<ProgramHandles> PROGRAM_HANDLES = new Int2ObjectOpenHashMap<>();
    private static boolean programEverSupported = false;
    private static final ThreadLocal<FloatBuffer> MATRIX_BUFFER = ThreadLocal.withInitial(() -> BufferUtils.createFloatBuffer(16));

    private static boolean isLiveTextureId(final int texId) {
        return texId != 0 && GL11.glIsTexture(texId);
    }

    private static final class ShaderHandles {
        private ShaderInstance shader;
        private boolean supported;

        private Uniform cullEnabled;
        private Uniform isShipPass;
        private Uniform cameraWorldPos;
        private Uniform waterStillUv;
        private Uniform waterFlowUv;
        private Uniform waterOverlayUv;
        private Uniform shipWaterTintEnabled;
        private Uniform shipWaterTint;

        private final Uniform[] shipAabbMin = new Uniform[MAX_SHIPS];
        private final Uniform[] shipAabbMax = new Uniform[MAX_SHIPS];
        private final Uniform[] cameraShipPos = new Uniform[MAX_SHIPS];
        private final Uniform[] gridMin = new Uniform[MAX_SHIPS];
        private final Uniform[] gridSize = new Uniform[MAX_SHIPS];
        private final Uniform[] worldToShip = new Uniform[MAX_SHIPS];
    }

    private static final ShaderHandles SHADER = new ShaderHandles();

    private static boolean everEnabled = false;
    private static boolean shaderEverSupported = false;
    private static boolean loggedEmbeddiumProgramMissingUniforms = false;

    public static void clear() {
        SHADER.shader = null;
        SHADER.supported = false;

        everEnabled = false;
        shaderEverSupported = false;
        programEverSupported = false;
        PROGRAM_HANDLES.clear();

        for (final ShipMasks masks : SHIP_MASKS.values()) {
            masks.close();
        }
        SHIP_MASKS.clear();

        if (isLiveTextureId(fluidMaskTexId)) {
            TextureUtil.releaseTextureId(fluidMaskTexId);
        }
        fluidMaskTexId = 0;
        if (pendingFluidMaskFuture != null) {
            pendingFluidMaskFuture.cancel(true);
            pendingFluidMaskFuture = null;
        }
        fluidMaskWidth = 0;
        fluidMaskHeight = 0;
        fluidMaskLastAtlasTexId = 0;
        fluidMaskData = null;
        fluidMaskBuffer = null;
        loggedFluidMaskBuildFailed = false;
        pendingFluidMaskAtlasTexId = 0;
        pendingFluidMaskWidth = 0;
        pendingFluidMaskHeight = 0;

        lastLevel = null;
    }

    public static boolean isShaderCullingActive() {
        if (!VSGameConfig.COMMON.getEnableAirPockets()) return false;
        return (shaderEverSupported || programEverSupported) && everEnabled;
    }

    public static void setupForWorldTranslucentPass(final ShaderInstance shader, final ClientLevel level, final Camera camera) {
        if (level == null || camera == null) return;
        final Vec3 cameraPos = camera.getPosition();
        setupForWorldTranslucentPass(shader, level, cameraPos.x, cameraPos.y, cameraPos.z);
    }

    public static void setupForWorldTranslucentPass(final ShaderInstance shader, final ClientLevel level,
        final double cameraX, final double cameraY, final double cameraZ) {
        if (level == null) return;

        RenderSystem.assertOnRenderThread();

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        bindShaderHandles(shader);
        if (!SHADER.supported) return;

        if (!VSGameConfig.COMMON.getEnableAirPockets()) {
            disable(shader);
            return;
        }

        SHADER.cullEnabled.set(1.0f);
        SHADER.cullEnabled.upload();
        everEnabled = true;

        setShipPass(shader, false);

        final Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        SHADER.shader.setSampler("ValkyrienAir_FluidMask", ensureFluidMaskTexture(level));
        updateCameraAndWaterUv(cameraPos);

        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, MAX_SHIPS);
        updateShipUniformsAndMasks(level, ships, cameraX, cameraY, cameraZ);
    }

    public static void setupForWorldTranslucentPassProgram(final int programId, final ClientLevel level,
        final double cameraX, final double cameraY, final double cameraZ) {
        if (programId == 0 || level == null) return;

        RenderSystem.assertOnRenderThread();

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        final ProgramHandles handles = bindProgramHandles(programId);
        if (handles == null || !handles.supported) {
            if (!loggedEmbeddiumProgramMissingUniforms && handles != null && handles.embeddiumChunkProgram &&
                VSGameConfig.COMMON.getEnableAirPockets()) {
                loggedEmbeddiumProgramMissingUniforms = true;
                if (handles.cullEnabledLoc < 0) {
                    LOGGER.warn("Embeddium chunk shader program {} is missing ValkyrienAir uniforms; water culling is inactive (shader injection not applied?)",
                        programId);
                } else {
                    LOGGER.warn("Embeddium chunk shader program {} has incomplete ValkyrienAir uniforms; water culling is inactive",
                        programId);
                }
            }
            return;
        }

        if (!VSGameConfig.COMMON.getEnableAirPockets()) {
            disableProgram(programId);
            return;
        }

        if (handles.cullEnabledLoc >= 0) {
            GL20.glUniform1f(handles.cullEnabledLoc, 1.0f);
        }
        everEnabled = true;

        // Default to world rendering. The caller is expected to update this via setShipPassProgram().
        if (handles.isShipPassLoc >= 0) {
            GL20.glUniform1f(handles.isShipPassLoc, 0.0f);
        }

        final Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        if (handles.chunkWorldOriginLoc >= 0) {
            GL20.glUniform3f(handles.chunkWorldOriginLoc, (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        }
        bindProgramFluidMaskTexture(handles, ensureFluidMaskTexture(level));
        updateCameraAndWaterUvProgram(handles, cameraPos);

        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, handles.maxMaskSlots);
        updateShipUniformsAndMasksProgram(handles, level, ships, cameraX, cameraY, cameraZ);
    }

    public static void disableProgram(final int programId) {
        if (programId == 0) return;
        RenderSystem.assertOnRenderThread();

        final ProgramHandles handles = bindProgramHandles(programId);
        if (handles == null) return;

        if (handles.cullEnabledLoc >= 0) {
            GL20.glUniform1f(handles.cullEnabledLoc, 0.0f);
        }
        if (handles.isShipPassLoc >= 0) {
            GL20.glUniform1f(handles.isShipPassLoc, 0.0f);
        }
        if (handles.shipWaterTintEnabledLoc >= 0) {
            GL20.glUniform1f(handles.shipWaterTintEnabledLoc, 0.0f);
        }
        if (handles.shipWaterTintLoc >= 0) {
            GL20.glUniform3f(handles.shipWaterTintLoc, 1.0f, 1.0f, 1.0f);
        }
    }

    public static void setShipPassProgram(final int programId, final boolean shipPass) {
        if (programId == 0) return;
        RenderSystem.assertOnRenderThread();

        final ProgramHandles handles = bindProgramHandles(programId);
        if (handles == null || handles.isShipPassLoc < 0) return;

        if (handles.isShipPassLoc >= 0) {
            GL20.glUniform1f(handles.isShipPassLoc, shipPass ? 1.0f : 0.0f);
        }
    }

    public static void disable(final ShaderInstance shader) {
        if (shader == null) return;
        RenderSystem.assertOnRenderThread();

        bindShaderHandles(shader);
        if (!SHADER.supported || SHADER.cullEnabled == null) return;

        SHADER.cullEnabled.set(0.0f);
        SHADER.cullEnabled.upload();

        if (SHADER.isShipPass != null) {
            SHADER.isShipPass.set(0.0f);
            SHADER.isShipPass.upload();
        }

        if (SHADER.shipWaterTintEnabled != null) {
            SHADER.shipWaterTintEnabled.set(0.0f);
            SHADER.shipWaterTintEnabled.upload();
        }
        if (SHADER.shipWaterTint != null) {
            SHADER.shipWaterTint.set(1.0f, 1.0f, 1.0f);
            SHADER.shipWaterTint.upload();
        }
    }

    public static void setShipPass(final ShaderInstance shader, final boolean shipPass) {
        if (shader == null) return;
        if (!SHIP_MASKS.isEmpty()) {
            // Ensure handles are bound if someone calls setShipPass() before setup.
            bindShaderHandles(shader);
        }
        if (!SHADER.supported || SHADER.isShipPass == null) return;

        SHADER.isShipPass.set(shipPass ? 1.0f : 0.0f);
        SHADER.isShipPass.upload();
    }

    public static void setShipWaterTintEnabled(final ShaderInstance shader, final boolean enabled) {
        if (shader == null) return;
        if (!SHIP_MASKS.isEmpty()) {
            bindShaderHandles(shader);
        }
        if (!SHADER.supported || SHADER.shipWaterTintEnabled == null) return;

        SHADER.shipWaterTintEnabled.set(enabled ? 1.0f : 0.0f);
        SHADER.shipWaterTintEnabled.upload();
    }

    public static void setShipWaterTint(final ShaderInstance shader, final int rgb) {
        if (shader == null) return;
        if (!SHIP_MASKS.isEmpty()) {
            bindShaderHandles(shader);
        }
        if (!SHADER.supported || SHADER.shipWaterTint == null) return;

        final float r = ((rgb >> 16) & 0xFF) / 255.0f;
        final float g = ((rgb >> 8) & 0xFF) / 255.0f;
        final float b = (rgb & 0xFF) / 255.0f;
        SHADER.shipWaterTint.set(r, g, b);
        SHADER.shipWaterTint.upload();
    }

    public static void setShipWaterTintEnabledProgram(final int programId, final boolean enabled) {
        if (programId == 0) return;
        RenderSystem.assertOnRenderThread();

        final ProgramHandles handles = bindProgramHandles(programId);
        if (handles == null || handles.shipWaterTintEnabledLoc < 0) return;
        GL20.glUniform1f(handles.shipWaterTintEnabledLoc, enabled ? 1.0f : 0.0f);
    }

    public static void setShipWaterTintProgram(final int programId, final int rgb) {
        if (programId == 0) return;
        RenderSystem.assertOnRenderThread();

        final ProgramHandles handles = bindProgramHandles(programId);
        if (handles == null || handles.shipWaterTintLoc < 0) return;

        final float r = ((rgb >> 16) & 0xFF) / 255.0f;
        final float g = ((rgb >> 8) & 0xFF) / 255.0f;
        final float b = (rgb & 0xFF) / 255.0f;
        GL20.glUniform3f(handles.shipWaterTintLoc, r, g, b);
    }

    public static int bindInteriorVolumeSamplersAndUniforms(final ShaderInstance shader, final ClientLevel level,
        final double cameraX, final double cameraY, final double cameraZ) {
        if (shader == null || level == null) return 0;

        RenderSystem.assertOnRenderThread();

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        final Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, MAX_SHIPS);

        int boundShipCount = 0;
        for (int slot = 0; slot < MAX_SHIPS; slot++) {
            if (slot >= ships.size()) {
                clearInteriorVolumeSlot(shader, slot);
                continue;
            }

            final LoadedShip ship = ships.get(slot);
            final long shipId = ship.getId();
            final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot =
                ShipWaterPocketManager.getClientWaterReachableSnapshot(level, shipId);
            if (snapshot == null) {
                clearInteriorVolumeSlot(shader, slot);
                continue;
            }

            final ShipMasks masks = SHIP_MASKS.computeIfAbsent(shipId, ShipMasks::new);

            final int minX = snapshot.getMinX();
            final int minY = snapshot.getMinY();
            final int minZ = snapshot.getMinZ();
            final int sizeX = snapshot.getSizeX();
            final int sizeY = snapshot.getSizeY();
            final int sizeZ = snapshot.getSizeZ();
            final long geometryRevision = snapshot.getGeometryRevision();

            final boolean boundsChanged =
                masks.minX != minX || masks.minY != minY || masks.minZ != minZ ||
                    masks.sizeX != sizeX || masks.sizeY != sizeY || masks.sizeZ != sizeZ;

            if (boundsChanged || masks.geometryRevision != geometryRevision) {
                rebuildMask(level, masks, snapshot, minX, minY, minZ, sizeX, sizeY, sizeZ, geometryRevision);
            } else {
                ensureMaskTextureStorage(masks, sizeX * sizeY * sizeZ);
            }
            applyPendingMaskBuild(masks, geometryRevision);

            if (!isLiveTextureId(masks.maskTexId) || masks.lastMaskUploadRevision != geometryRevision) {
                clearInteriorVolumeSlot(shader, slot);
                continue;
            }

            final AABBdc worldAabb = getShipWorldAabb(ship).orElse(null);
            if (worldAabb == null) {
                clearInteriorVolumeSlot(shader, slot);
                continue;
            }

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc worldToShipMatrix = shipTransform.getWorldToShip();
            final double biasedM30 = worldToShipMatrix.m30() - (double) minX;
            final double biasedM31 = worldToShipMatrix.m31() - (double) minY;
            final double biasedM32 = worldToShipMatrix.m32() - (double) minZ;
            masks.worldToShip.set(
                (float) worldToShipMatrix.m00(), (float) worldToShipMatrix.m01(), (float) worldToShipMatrix.m02(), (float) worldToShipMatrix.m03(),
                (float) worldToShipMatrix.m10(), (float) worldToShipMatrix.m11(), (float) worldToShipMatrix.m12(), (float) worldToShipMatrix.m13(),
                (float) worldToShipMatrix.m20(), (float) worldToShipMatrix.m21(), (float) worldToShipMatrix.m22(), (float) worldToShipMatrix.m23(),
                (float) biasedM30, (float) biasedM31, (float) biasedM32, (float) worldToShipMatrix.m33()
            );

            final Uniform aabbMin = shader.getUniform("ValkyrienAir_ShipAabbMin" + slot);
            if (aabbMin != null) {
                aabbMin.set((float) worldAabb.minX(), (float) worldAabb.minY(), (float) worldAabb.minZ(), 0.0f);
                aabbMin.upload();
            }

            final Uniform aabbMax = shader.getUniform("ValkyrienAir_ShipAabbMax" + slot);
            if (aabbMax != null) {
                aabbMax.set((float) worldAabb.maxX(), (float) worldAabb.maxY(), (float) worldAabb.maxZ(), 0.0f);
                aabbMax.upload();
            }

            final Uniform gridSize = shader.getUniform("ValkyrienAir_GridSize" + slot);
            if (gridSize != null) {
                gridSize.set((float) sizeX, (float) sizeY, (float) sizeZ, 0.0f);
                gridSize.upload();
            }

            final Uniform worldToShip = shader.getUniform("ValkyrienAir_WorldToShip" + slot);
            if (worldToShip != null) {
                worldToShip.set(masks.worldToShip);
                worldToShip.upload();
            }

            shader.setSampler("ValkyrienAir_Mask" + slot, masks.maskTexId);
            boundShipCount++;
        }

        final Uniform shipCount = shader.getUniform("ValkyrienAir_ShipCount");
        if (shipCount != null) {
            shipCount.set(boundShipCount);
            shipCount.upload();
        }

        return boundShipCount;
    }

    public static int bindInteriorVolumeSamplersAndUniforms(final EffectInstance effect, final ClientLevel level,
        final double cameraX, final double cameraY, final double cameraZ) {
        if (effect == null || level == null) return 0;

        RenderSystem.assertOnRenderThread();

        if (lastLevel != level) {
            clear();
            lastLevel = level;
        }

        final Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        final List<LoadedShip> ships = selectClosestShips(level, cameraPos, MAX_SHIPS);

        int boundShipCount = 0;
        final boolean shouldLogDiag = shouldLogInteriorVolumeDiag();
        final StringBuilder diag = shouldLogDiag
            ? new StringBuilder("Interior volume effect bind camera=(")
                .append(formatDiagDouble(cameraX)).append(",")
                .append(formatDiagDouble(cameraY)).append(",")
                .append(formatDiagDouble(cameraZ)).append(")")
            : null;
        for (int slot = 0; slot < MAX_SHIPS; slot++) {
            if (slot >= ships.size()) {
                clearInteriorVolumeSlot(effect, slot);
                continue;
            }

            final LoadedShip ship = ships.get(slot);
            final long shipId = ship.getId();
            final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot =
                ShipWaterPocketManager.getClientWaterReachableSnapshot(level, shipId);
            if (snapshot == null) {
                clearInteriorVolumeSlot(effect, slot);
                continue;
            }

            final ShipMasks masks = SHIP_MASKS.computeIfAbsent(shipId, ShipMasks::new);

            final int minX = snapshot.getMinX();
            final int minY = snapshot.getMinY();
            final int minZ = snapshot.getMinZ();
            final int sizeX = snapshot.getSizeX();
            final int sizeY = snapshot.getSizeY();
            final int sizeZ = snapshot.getSizeZ();
            final long geometryRevision = snapshot.getGeometryRevision();

            final boolean boundsChanged =
                masks.minX != minX || masks.minY != minY || masks.minZ != minZ ||
                    masks.sizeX != sizeX || masks.sizeY != sizeY || masks.sizeZ != sizeZ;

            if (boundsChanged || masks.geometryRevision != geometryRevision) {
                rebuildMask(level, masks, snapshot, minX, minY, minZ, sizeX, sizeY, sizeZ, geometryRevision);
            } else {
                ensureMaskTextureStorage(masks, sizeX * sizeY * sizeZ);
            }
            applyPendingMaskBuild(masks, geometryRevision);

            if (!isLiveTextureId(masks.maskTexId) || masks.lastMaskUploadRevision != geometryRevision) {
                clearInteriorVolumeSlot(effect, slot);
                continue;
            }

            final AABBdc worldAabb = getShipWorldAabb(ship).orElse(null);
            if (worldAabb == null) {
                clearInteriorVolumeSlot(effect, slot);
                continue;
            }

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc worldToShipMatrix = shipTransform.getWorldToShip();
            final double biasedM30 = worldToShipMatrix.m30() - (double) minX;
            final double biasedM31 = worldToShipMatrix.m31() - (double) minY;
            final double biasedM32 = worldToShipMatrix.m32() - (double) minZ;
            final double camShipX =
                worldToShipMatrix.m00() * cameraX + worldToShipMatrix.m10() * cameraY + worldToShipMatrix.m20() * cameraZ + biasedM30;
            final double camShipY =
                worldToShipMatrix.m01() * cameraX + worldToShipMatrix.m11() * cameraY + worldToShipMatrix.m21() * cameraZ + biasedM31;
            final double camShipZ =
                worldToShipMatrix.m02() * cameraX + worldToShipMatrix.m12() * cameraY + worldToShipMatrix.m22() * cameraZ + biasedM32;
            masks.worldToShip.set(
                (float) worldToShipMatrix.m00(), (float) worldToShipMatrix.m01(), (float) worldToShipMatrix.m02(), (float) worldToShipMatrix.m03(),
                (float) worldToShipMatrix.m10(), (float) worldToShipMatrix.m11(), (float) worldToShipMatrix.m12(), (float) worldToShipMatrix.m13(),
                (float) worldToShipMatrix.m20(), (float) worldToShipMatrix.m21(), (float) worldToShipMatrix.m22(), (float) worldToShipMatrix.m23(),
                (float) biasedM30, (float) biasedM31, (float) biasedM32, (float) worldToShipMatrix.m33()
            );

            final Uniform aabbMin = effect.getUniform("ValkyrienAir_ShipAabbMin" + slot);
            if (aabbMin != null) {
                aabbMin.set((float) worldAabb.minX(), (float) worldAabb.minY(), (float) worldAabb.minZ(), 0.0f);
            }

            final Uniform aabbMax = effect.getUniform("ValkyrienAir_ShipAabbMax" + slot);
            if (aabbMax != null) {
                aabbMax.set((float) worldAabb.maxX(), (float) worldAabb.maxY(), (float) worldAabb.maxZ(), 0.0f);
            }

            final Uniform gridSize = effect.getUniform("ValkyrienAir_GridSize" + slot);
            if (gridSize != null) {
                gridSize.set((float) sizeX, (float) sizeY, (float) sizeZ, 0.0f);
            }

            final Uniform worldToShip = effect.getUniform("ValkyrienAir_WorldToShip" + slot);
            if (worldToShip != null) {
                worldToShip.set(masks.worldToShip);
            }

            effect.setSampler("ValkyrienAir_Mask" + slot, () -> masks.maskTexId);
            boundShipCount++;

            if (shouldLogDiag) {
                appendInteriorVolumeDiag(
                    diag,
                    slot,
                    shipId,
                    worldAabb,
                    sizeX,
                    sizeY,
                    sizeZ,
                    geometryRevision,
                    masks,
                    cameraX,
                    cameraY,
                    cameraZ,
                    camShipX,
                    camShipY,
                    camShipZ
                );
            }
        }

        final Uniform shipCount = effect.getUniform("ValkyrienAir_ShipCount");
        if (shipCount != null) {
            shipCount.set(boundShipCount);
        }

        if (shouldLogDiag) {
            diag.append(" boundShips=").append(boundShipCount);
            LOGGER.info(diag.toString());
        }

        return boundShipCount;
    }

    private static boolean shouldLogInteriorVolumeDiag() {
        final long now = System.currentTimeMillis();
        if (now - lastInteriorVolumeDiagLogAtMs < INTERIOR_VOLUME_DIAG_LOG_INTERVAL_MS) {
            return false;
        }
        lastInteriorVolumeDiagLogAtMs = now;
        return true;
    }

    private static void appendInteriorVolumeDiag(
        final StringBuilder diag,
        final int slot,
        final long shipId,
        final AABBdc worldAabb,
        final int sizeX,
        final int sizeY,
        final int sizeZ,
        final long geometryRevision,
        final ShipMasks masks,
        final double cameraX,
        final double cameraY,
        final double cameraZ,
        final double camShipX,
        final double camShipY,
        final double camShipZ
    ) {
        final boolean inAabb =
            cameraX >= worldAabb.minX() && cameraX <= worldAabb.maxX() &&
                cameraY >= worldAabb.minY() && cameraY <= worldAabb.maxY() &&
                cameraZ >= worldAabb.minZ() && cameraZ <= worldAabb.maxZ();
        final boolean inGrid =
            camShipX >= 0.0 && camShipY >= 0.0 && camShipZ >= 0.0 &&
                camShipX < sizeX && camShipY < sizeY && camShipZ < sizeZ;
        final int voxelX = Mth.floor(camShipX);
        final int voxelY = Mth.floor(camShipY);
        final int voxelZ = Mth.floor(camShipZ);
        final boolean cpuAir = inGrid && isAirVoxelSet(masks, voxelX, voxelY, voxelZ);
        final boolean cpuWaterReachable = inGrid && isWaterReachableVoxelSet(masks, voxelX, voxelY, voxelZ);
        final int nearbyWaterReachable = inGrid ? countNearbyWaterReachableVoxels(masks, voxelX, voxelY, voxelZ, 3) : 0;
        final RayProbeResult rayProbe = probeLookRayBoundary(masks, cameraX, cameraY, cameraZ);

        diag.append(" | slot=").append(slot)
            .append(" ship=").append(shipId)
            .append(" aabb=").append(inAabb)
            .append(" camShip=(")
            .append(formatDiagDouble(camShipX)).append(",")
            .append(formatDiagDouble(camShipY)).append(",")
            .append(formatDiagDouble(camShipZ)).append(")")
            .append(" voxel=(").append(voxelX).append(",").append(voxelY).append(",").append(voxelZ).append(")")
            .append(" inGrid=").append(inGrid)
            .append(" cpuAir=").append(cpuAir)
            .append(" cpuWater=").append(cpuWaterReachable)
            .append(" nearWater=").append(nearbyWaterReachable)
            .append(" rayExit=").append(rayProbe.exitFound)
            .append(" rayDist=").append(formatDiagDouble(rayProbe.exitDistance))
            .append(" rayVoxel=(").append(rayProbe.voxelX).append(",").append(rayProbe.voxelY).append(",").append(rayProbe.voxelZ).append(")")
            .append(" rayNearWater=").append(rayProbe.nearbyWaterCount)
            .append(" grid=(").append(sizeX).append(",").append(sizeY).append(",").append(sizeZ).append(")")
            .append(" tex=").append(masks.maskTexId)
            .append(" uploadRev=").append(masks.lastMaskUploadRevision)
            .append(" geomRev=").append(geometryRevision);
    }

    private static RayProbeResult probeLookRayBoundary(final ShipMasks masks, final double cameraX, final double cameraY,
        final double cameraZ) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) {
            return RayProbeResult.EMPTY;
        }
        final Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
        final double lookLenSq = (double) look.x * look.x + (double) look.y * look.y + (double) look.z * look.z;
        if (lookLenSq <= 1.0e-6) {
            return RayProbeResult.EMPTY;
        }

        final double maxDistance = 64.0;
        final double step = 0.25;
        double lastInsideDistance = -1.0;
        int lastVoxelX = Integer.MIN_VALUE;
        int lastVoxelY = Integer.MIN_VALUE;
        int lastVoxelZ = Integer.MIN_VALUE;

        for (double travel = 0.0; travel <= maxDistance; travel += step) {
            final double worldX = cameraX + look.x * travel;
            final double worldY = cameraY + look.y * travel;
            final double worldZ = cameraZ + look.z * travel;
            final double shipX =
                masks.worldToShip.m00() * worldX + masks.worldToShip.m10() * worldY + masks.worldToShip.m20() * worldZ + masks.worldToShip.m30();
            final double shipY =
                masks.worldToShip.m01() * worldX + masks.worldToShip.m11() * worldY + masks.worldToShip.m21() * worldZ + masks.worldToShip.m31();
            final double shipZ =
                masks.worldToShip.m02() * worldX + masks.worldToShip.m12() * worldY + masks.worldToShip.m22() * worldZ + masks.worldToShip.m32();

            if (shipX < 0.0 || shipY < 0.0 || shipZ < 0.0 || shipX >= masks.sizeX || shipY >= masks.sizeY || shipZ >= masks.sizeZ) {
                break;
            }

            final int voxelX = Mth.floor(shipX);
            final int voxelY = Mth.floor(shipY);
            final int voxelZ = Mth.floor(shipZ);
            if (!isAirVoxelSet(masks, voxelX, voxelY, voxelZ)) {
                break;
            }

            lastInsideDistance = travel;
            lastVoxelX = voxelX;
            lastVoxelY = voxelY;
            lastVoxelZ = voxelZ;
        }

        if (lastInsideDistance < 0.0) {
            return RayProbeResult.EMPTY;
        }

        return new RayProbeResult(
            true,
            lastInsideDistance,
            lastVoxelX,
            lastVoxelY,
            lastVoxelZ,
            countNearbyWaterReachableVoxels(masks, lastVoxelX, lastVoxelY, lastVoxelZ, 3)
        );
    }

    private static boolean isAirVoxelSet(final ShipMasks masks, final int voxelX, final int voxelY, final int voxelZ) {
        if (masks.maskData == null) return false;
        if (voxelX < 0 || voxelY < 0 || voxelZ < 0) return false;
        if (voxelX >= masks.sizeX || voxelY >= masks.sizeY || voxelZ >= masks.sizeZ) return false;

        final int volume = masks.sizeX * masks.sizeY * masks.sizeZ;
        final int voxelIdx = voxelX + masks.sizeX * (voxelY + masks.sizeY * voxelZ);
        final int wordIndex = volume * OCC_WORDS_PER_VOXEL + (voxelIdx >> 5);
        if (wordIndex < 0 || wordIndex >= masks.maskData.length) return false;

        final int bit = voxelIdx & 31;
        final int word = masks.maskData[wordIndex];
        return ((word >>> bit) & 1) != 0;
    }

    private static boolean isWaterReachableVoxelSet(final ShipMasks masks, final int voxelX, final int voxelY, final int voxelZ) {
        if (masks.maskData == null) return false;
        if (voxelX < 0 || voxelY < 0 || voxelZ < 0) return false;
        if (voxelX >= masks.sizeX || voxelY >= masks.sizeY || voxelZ >= masks.sizeZ) return false;

        final int volume = masks.sizeX * masks.sizeY * masks.sizeZ;
        final int airWordCount = (volume + 31) >> 5;
        final int voxelIdx = voxelX + masks.sizeX * (voxelY + masks.sizeY * voxelZ);
        final int wordIndex = volume * OCC_WORDS_PER_VOXEL + airWordCount + (voxelIdx >> 5);
        if (wordIndex < 0 || wordIndex >= masks.maskData.length) return false;

        final int bit = voxelIdx & 31;
        final int word = masks.maskData[wordIndex];
        return ((word >>> bit) & 1) != 0;
    }

    private static int countNearbyWaterReachableVoxels(final ShipMasks masks, final int centerX, final int centerY, final int centerZ,
        final int radius) {
        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (isWaterReachableVoxelSet(masks, centerX + dx, centerY + dy, centerZ + dz)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private record RayProbeResult(boolean exitFound, double exitDistance, int voxelX, int voxelY, int voxelZ, int nearbyWaterCount) {
        private static final RayProbeResult EMPTY = new RayProbeResult(false, 0.0, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
    }

    private static String formatDiagDouble(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void clearInteriorVolumeSlot(final ShaderInstance shader, final int slot) {
        final Uniform aabbMin = shader.getUniform("ValkyrienAir_ShipAabbMin" + slot);
        if (aabbMin != null) {
            aabbMin.set(0.0f, 0.0f, 0.0f, 0.0f);
            aabbMin.upload();
        }

        final Uniform aabbMax = shader.getUniform("ValkyrienAir_ShipAabbMax" + slot);
        if (aabbMax != null) {
            aabbMax.set(0.0f, 0.0f, 0.0f, 0.0f);
            aabbMax.upload();
        }

        final Uniform gridSize = shader.getUniform("ValkyrienAir_GridSize" + slot);
        if (gridSize != null) {
            gridSize.set(0.0f, 0.0f, 0.0f, 0.0f);
            gridSize.upload();
        }

        final Uniform worldToShip = shader.getUniform("ValkyrienAir_WorldToShip" + slot);
        if (worldToShip != null) {
            worldToShip.set(IDENTITY_MAT4);
            worldToShip.upload();
        }
    }

    private static void clearInteriorVolumeSlot(final EffectInstance effect, final int slot) {
        final Uniform aabbMin = effect.getUniform("ValkyrienAir_ShipAabbMin" + slot);
        if (aabbMin != null) {
            aabbMin.set(0.0f, 0.0f, 0.0f, 0.0f);
        }

        final Uniform aabbMax = effect.getUniform("ValkyrienAir_ShipAabbMax" + slot);
        if (aabbMax != null) {
            aabbMax.set(0.0f, 0.0f, 0.0f, 0.0f);
        }

        final Uniform gridSize = effect.getUniform("ValkyrienAir_GridSize" + slot);
        if (gridSize != null) {
            gridSize.set(0.0f, 0.0f, 0.0f, 0.0f);
        }

        final Uniform worldToShip = effect.getUniform("ValkyrienAir_WorldToShip" + slot);
        if (worldToShip != null) {
            worldToShip.set(IDENTITY_MAT4);
        }
    }

    private static void bindShaderHandles(final ShaderInstance shader) {
        if (shader == null) {
            SHADER.shader = null;
            SHADER.supported = false;
            return;
        }
        if (SHADER.shader == shader) return;

        SHADER.shader = shader;
        SHADER.supported = false;

        // Detect whether the rendertype_translucent shader has been patched by checking for our enable uniform.
        SHADER.cullEnabled = shader.getUniform("ValkyrienAir_CullEnabled");
        if (SHADER.cullEnabled == null) return;

        SHADER.isShipPass = shader.getUniform("ValkyrienAir_IsShipPass");
        SHADER.cameraWorldPos = shader.getUniform("ValkyrienAir_CameraWorldPos");
        SHADER.waterStillUv = shader.getUniform("ValkyrienAir_WaterStillUv");
        SHADER.waterFlowUv = shader.getUniform("ValkyrienAir_WaterFlowUv");
        SHADER.waterOverlayUv = shader.getUniform("ValkyrienAir_WaterOverlayUv");
        SHADER.shipWaterTintEnabled = shader.getUniform("ValkyrienAir_ShipWaterTintEnabled");
        SHADER.shipWaterTint = shader.getUniform("ValkyrienAir_ShipWaterTint");
        if (SHADER.isShipPass == null || SHADER.cameraWorldPos == null || SHADER.waterStillUv == null ||
            SHADER.waterFlowUv == null || SHADER.waterOverlayUv == null) {
            return;
        }

        for (int i = 0; i < MAX_SHIPS; i++) {
            SHADER.shipAabbMin[i] = shader.getUniform("ValkyrienAir_ShipAabbMin" + i);
            SHADER.shipAabbMax[i] = shader.getUniform("ValkyrienAir_ShipAabbMax" + i);
            SHADER.cameraShipPos[i] = shader.getUniform("ValkyrienAir_CameraShipPos" + i);
            SHADER.gridMin[i] = shader.getUniform("ValkyrienAir_GridMin" + i);
            SHADER.gridSize[i] = shader.getUniform("ValkyrienAir_GridSize" + i);
            SHADER.worldToShip[i] = shader.getUniform("ValkyrienAir_WorldToShip" + i);
            if (SHADER.shipAabbMin[i] == null || SHADER.shipAabbMax[i] == null || SHADER.gridMin[i] == null ||
                SHADER.gridSize[i] == null || SHADER.worldToShip[i] == null || SHADER.cameraShipPos[i] == null) {
                return;
            }
        }

        SHADER.supported = true;
        shaderEverSupported = true;
    }

    private static ProgramHandles bindProgramHandles(final int programId) {
        ProgramHandles handles = PROGRAM_HANDLES.get(programId);
        if (handles != null) return handles;

        handles = new ProgramHandles(programId);

        final int maxCombined = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        final int maxSafeUnits = Math.min(maxCombined, GLSTATEMANAGER_SAFE_TEXTURE_UNITS);
        handles.maxSafeTextureUnits = maxSafeUnits;
        final int availableUnits = maxSafeUnits - BASE_MASK_TEX_UNIT;
        final int availableUnitsForShipMasks = Math.max(0, availableUnits - 1); // Reserve 1 unit for the fluid mask.
        // 1 texture unit per ship slot (combined occ+air mask texture).
        handles.maxMaskSlots = Math.max(0, Math.min(MAX_SHIPS, availableUnitsForShipMasks));

        handles.regionOffsetLoc = GL20.glGetUniformLocation(programId, "u_RegionOffset");
        handles.blockTexLoc = GL20.glGetUniformLocation(programId, "u_BlockTex");
        final boolean looksLikeEmbeddiumChunkProgram = handles.regionOffsetLoc >= 0 || handles.blockTexLoc >= 0;
        handles.embeddiumChunkProgram = looksLikeEmbeddiumChunkProgram;

        handles.cullEnabledLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_CullEnabled");
        if (handles.cullEnabledLoc < 0) {
            PROGRAM_HANDLES.put(programId, handles);
            return handles;
        }

        handles.isShipPassLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_IsShipPass");
        handles.cameraWorldPosLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_CameraWorldPos");
        handles.waterStillUvLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_WaterStillUv");
        handles.waterFlowUvLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_WaterFlowUv");
        handles.waterOverlayUvLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_WaterOverlayUv");
        handles.fluidMaskLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_FluidMask");
        handles.shipWaterTintEnabledLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_ShipWaterTintEnabled");
        handles.shipWaterTintLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_ShipWaterTint");
        handles.chunkWorldOriginLoc = GL20.glGetUniformLocation(programId, "ValkyrienAir_ChunkWorldOrigin");

        for (int i = 0; i < MAX_SHIPS; i++) {
            handles.shipAabbMinLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_ShipAabbMin" + i);
            handles.shipAabbMaxLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_ShipAabbMax" + i);
            handles.cameraShipPosLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_CameraShipPos" + i);
            handles.gridMinLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_GridMin" + i);
            handles.gridSizeLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_GridSize" + i);
            handles.worldToShipLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_WorldToShip" + i);
            handles.maskLoc[i] = GL20.glGetUniformLocation(programId, "ValkyrienAir_Mask" + i);

            handles.shipSlotSupported[i] =
                i < handles.maxMaskSlots &&
                    handles.shipAabbMinLoc[i] >= 0 &&
                    handles.shipAabbMaxLoc[i] >= 0 &&
                    handles.gridSizeLoc[i] >= 0 &&
                    handles.worldToShipLoc[i] >= 0 &&
                    handles.maskLoc[i] >= 0;
        }

        final boolean requiredOk =
            looksLikeEmbeddiumChunkProgram &&
                handles.cullEnabledLoc >= 0 &&
                handles.isShipPassLoc >= 0 &&
                handles.cameraWorldPosLoc >= 0 &&
                handles.fluidMaskLoc >= 0 &&
                handles.maxMaskSlots > 0 &&
                handles.shipSlotSupported[0];

        // Candidate Embeddium chunk programs: allow partial operation even if some uniforms are optimized out.
        // Slot 0 must have the required uniforms; other slots and core uniforms are optional.
        handles.supported = requiredOk;

        programEverSupported |= handles.supported;
        PROGRAM_HANDLES.put(programId, handles);
        return handles;
    }

    private static void updateCameraAndWaterUv(final Vec3 cameraPos) {
        SHADER.cameraWorldPos.set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        SHADER.cameraWorldPos.upload();

        final Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final TextureAtlasSprite still = atlas.apply(WATER_STILL);
        final TextureAtlasSprite flow = atlas.apply(WATER_FLOW);
        final TextureAtlasSprite overlay = atlas.apply(WATER_OVERLAY);

        SHADER.waterStillUv.set(still.getU0(), still.getV0(), still.getU1(), still.getV1());
        SHADER.waterFlowUv.set(flow.getU0(), flow.getV0(), flow.getU1(), flow.getV1());
        SHADER.waterOverlayUv.set(overlay.getU0(), overlay.getV0(), overlay.getU1(), overlay.getV1());

        SHADER.waterStillUv.upload();
        SHADER.waterFlowUv.upload();
        SHADER.waterOverlayUv.upload();
    }

    private static void updateCameraAndWaterUvProgram(final ProgramHandles handles, final Vec3 cameraPos) {
        if (handles.cameraWorldPosLoc >= 0) {
            GL20.glUniform3f(handles.cameraWorldPosLoc, (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        }

        final Function<ResourceLocation, TextureAtlasSprite> atlas =
            Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final TextureAtlasSprite still = atlas.apply(WATER_STILL);
        final TextureAtlasSprite flow = atlas.apply(WATER_FLOW);
        final TextureAtlasSprite overlay = atlas.apply(WATER_OVERLAY);

        if (handles.waterStillUvLoc >= 0) {
            GL20.glUniform4f(handles.waterStillUvLoc, still.getU0(), still.getV0(), still.getU1(), still.getV1());
        }
        if (handles.waterFlowUvLoc >= 0) {
            GL20.glUniform4f(handles.waterFlowUvLoc, flow.getU0(), flow.getV0(), flow.getU1(), flow.getV1());
        }
        if (handles.waterOverlayUvLoc >= 0) {
            GL20.glUniform4f(handles.waterOverlayUvLoc, overlay.getU0(), overlay.getV0(), overlay.getU1(), overlay.getV1());
        }
    }

    private static ResourceLocation[] queryForgeFluidTextures(final ClientLevel level, final Fluid fluid, final FluidState fluidState) {
        if (!ensureForgeFluidTextureAccess()) return null;
        try {
            final Object ext = forgeFluidExtOf.invoke(null, fluid);
            if (ext == null) return null;

            final ResourceLocation still = invokeTexture(ext, forgeGetStill0, forgeGetStill3, fluidState, level);
            final ResourceLocation flow = invokeTexture(ext, forgeGetFlow0, forgeGetFlow3, fluidState, level);
            final ResourceLocation overlay = invokeTexture(ext, forgeGetOverlay0, forgeGetOverlay3, fluidState, level);

            if (still == null && flow == null && overlay == null) return null;
            return new ResourceLocation[] {still, flow, overlay};
        } catch (final Throwable ignored) {
            return null;
        }
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

            forgeGetStill3 = findMethod(extClass, "getStillTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeGetFlow3 = findMethod(extClass, "getFlowingTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            forgeGetOverlay3 = findMethod(extClass, "getOverlayTexture", FluidState.class, BlockAndTintGetter.class, BlockPos.class);
            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        } catch (final Throwable t) {
            if (!loggedFluidMaskBuildFailed) {
                loggedFluidMaskBuildFailed = true;
                LOGGER.warn("Failed to query Forge fluid render textures for fluid culling; some modded fluids may not be culled.", t);
            }
            return false;
        }
    }

    private static TextureAtlasSprite[] queryFabricFluidSprites(final ClientLevel level, final Fluid fluid, final FluidState fluidState) {
        if (!ensureFabricFluidTextureAccess()) return null;
        try {
            final Object handler = fabricRegistryGetHandler.invoke(fabricFluidRenderHandlerRegistry, fluid);
            if (handler == null) return null;
            return (TextureAtlasSprite[]) fabricHandlerGetSprites.invoke(handler, level, BlockPos.ZERO, fluidState);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static boolean ensureFabricFluidTextureAccess() {
        if (fabricFluidTexturesChecked) return fabricFluidRenderHandlerRegistry != null;
        fabricFluidTexturesChecked = true;
        try {
            final Class<?> registryClass = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry");
            final Field instanceField = registryClass.getField("INSTANCE");
            fabricFluidRenderHandlerRegistry = instanceField.get(null);
            fabricRegistryGetHandler = registryClass.getMethod("get", Fluid.class);

            final Class<?> handlerClass = Class.forName("net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler");
            fabricHandlerGetSprites = handlerClass.getMethod(
                "getFluidSprites",
                net.minecraft.world.level.BlockAndTintGetter.class,
                BlockPos.class,
                FluidState.class
            );

            return true;
        } catch (final ClassNotFoundException ignored) {
            return false;
        } catch (final Throwable t) {
            if (!loggedFluidMaskBuildFailed) {
                loggedFluidMaskBuildFailed = true;
                LOGGER.warn("Failed to query Fabric fluid sprites for fluid culling; some modded fluids may not be culled.", t);
            }
            return false;
        }
    }

    private static List<LoadedShip> selectClosestShips(final ClientLevel level, final Vec3 cameraPos, final int maxCount) {
        final List<LoadedShip> candidates = new ArrayList<>();
        for (final LoadedShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            candidates.add(ship);
        }

        candidates.sort(Comparator.comparingDouble(ship -> distanceSqToShipAabb(cameraPos, ship)));
        if (candidates.size() > maxCount) {
            return candidates.subList(0, maxCount);
        }
        return candidates;
    }

    private static double distanceSqToShipAabb(final Vec3 cameraPos, final LoadedShip ship) {
        final AABBdc shipWorldAabbDc = getShipWorldAabb(ship).orElse(null);
        if (shipWorldAabbDc == null) return Double.POSITIVE_INFINITY;

        final double closestX = Mth.clamp(cameraPos.x, shipWorldAabbDc.minX(), shipWorldAabbDc.maxX());
        final double closestY = Mth.clamp(cameraPos.y, shipWorldAabbDc.minY(), shipWorldAabbDc.maxY());
        final double closestZ = Mth.clamp(cameraPos.z, shipWorldAabbDc.minZ(), shipWorldAabbDc.maxZ());
        final double dx = closestX - cameraPos.x;
        final double dy = closestY - cameraPos.y;
        final double dz = closestZ - cameraPos.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Optional<AABBdc> getShipWorldAabb(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return Optional.ofNullable(clientShip.getRenderAABB());
        }
        return Optional.ofNullable(ship.getWorldAABB());
    }

    private static ShipTransform getShipTransform(final LoadedShip ship) {
        if (ship instanceof final ClientShip clientShip) {
            return clientShip.getRenderTransform();
        }
        return ship.getShipTransform();
    }

    private static void updateShipUniformsAndMasks(final ClientLevel level, final List<LoadedShip> ships,
        final double cameraX, final double cameraY, final double cameraZ) {
        final long gameTime = level.getGameTime();

        for (int slot = 0; slot < MAX_SHIPS; slot++) {
            if (slot >= ships.size()) {
                disableShipSlot(slot);
                continue;
            }

            final LoadedShip ship = ships.get(slot);
            final long shipId = ship.getId();

            final AABBdc shipWorldAabbDc = getShipWorldAabb(ship).orElse(null);
            if (shipWorldAabbDc == null) {
                disableShipSlot(slot);
                continue;
            }

            final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot =
                ShipWaterPocketManager.getClientWaterReachableSnapshot(level, shipId);
            if (snapshot == null) {
                disableShipSlot(slot);
                continue;
            }

            final ShipMasks masks = SHIP_MASKS.computeIfAbsent(shipId, ShipMasks::new);

            final int minX = snapshot.getMinX();
            final int minY = snapshot.getMinY();
            final int minZ = snapshot.getMinZ();
            final int sizeX = snapshot.getSizeX();
            final int sizeY = snapshot.getSizeY();
            final int sizeZ = snapshot.getSizeZ();
            final long geometryRevision = snapshot.getGeometryRevision();

            final boolean boundsChanged =
                masks.minX != minX || masks.minY != minY || masks.minZ != minZ ||
                    masks.sizeX != sizeX || masks.sizeY != sizeY || masks.sizeZ != sizeZ;

            if (boundsChanged || masks.geometryRevision != geometryRevision) {
                rebuildMask(level, masks, snapshot, minX, minY, minZ, sizeX, sizeY, sizeZ, geometryRevision);
            }
            applyPendingMaskBuild(masks, geometryRevision);

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc worldToShip = shipTransform.getWorldToShip();
            final double biasedM30 = worldToShip.m30() - (double) minX;
            final double biasedM31 = worldToShip.m31() - (double) minY;
            final double biasedM32 = worldToShip.m32() - (double) minZ;

            // Bind sampler for this slot.
            SHADER.shader.setSampler("ValkyrienAir_Mask" + slot, masks.maskTexId);

            // Upload slot uniforms.
            SHADER.shipAabbMin[slot].set((float) shipWorldAabbDc.minX(), (float) shipWorldAabbDc.minY(), (float) shipWorldAabbDc.minZ(), 0.0f);
            SHADER.shipAabbMax[slot].set((float) shipWorldAabbDc.maxX(), (float) shipWorldAabbDc.maxY(), (float) shipWorldAabbDc.maxZ(), 0.0f);

            // IMPORTANT: Shipyard positions can be very large (depending on VS2's shipyard layout), which quickly
            // exceeds float integer precision. If we upload absolute ship-space coordinates and then do
            // `localPos = shipPos - gridMin` in the shader, the subtraction can lose multiple blocks of precision.
            //
            // To keep the shader math stable, we pre-bias worldToShip by -gridMin on the CPU (using doubles) and
            // then upload gridMin = 0 so the shader operates in a small local coordinate system.
            SHADER.gridMin[slot].set(0.0f, 0.0f, 0.0f, 0.0f);
            SHADER.gridSize[slot].set((float) sizeX, (float) sizeY, (float) sizeZ, 0.0f);
            masks.worldToShip.set(
                (float) worldToShip.m00(), (float) worldToShip.m01(), (float) worldToShip.m02(), (float) worldToShip.m03(),
                (float) worldToShip.m10(), (float) worldToShip.m11(), (float) worldToShip.m12(), (float) worldToShip.m13(),
                (float) worldToShip.m20(), (float) worldToShip.m21(), (float) worldToShip.m22(), (float) worldToShip.m23(),
                (float) biasedM30, (float) biasedM31, (float) biasedM32, (float) worldToShip.m33()
            );
            SHADER.worldToShip[slot].set(masks.worldToShip);

            final double camShipX = worldToShip.m00() * cameraX + worldToShip.m10() * cameraY + worldToShip.m20() * cameraZ + biasedM30;
            final double camShipY = worldToShip.m01() * cameraX + worldToShip.m11() * cameraY + worldToShip.m21() * cameraZ + biasedM31;
            final double camShipZ = worldToShip.m02() * cameraX + worldToShip.m12() * cameraY + worldToShip.m22() * cameraZ + biasedM32;
            SHADER.cameraShipPos[slot].set((float) camShipX, (float) camShipY, (float) camShipZ);

            SHADER.shipAabbMin[slot].upload();
            SHADER.shipAabbMax[slot].upload();
            SHADER.cameraShipPos[slot].upload();
            SHADER.gridMin[slot].upload();
            SHADER.gridSize[slot].upload();
            SHADER.worldToShip[slot].upload();
        }

        // Clear stale mask entries for ships that are no longer loaded to avoid leaks.
        // (Ship IDs can be reused across levels; lastLevel guards that case.)
        final LongOpenHashSet loadedIds = new LongOpenHashSet();
        for (final LoadedShip loadedShip : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            loadedIds.add(loadedShip.getId());
        }
        SHIP_MASKS.entrySet().removeIf(entry -> {
            if (loadedIds.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
    }

    private static void updateShipUniformsAndMasksProgram(final ProgramHandles handles, final ClientLevel level,
        final List<LoadedShip> ships, final double cameraX, final double cameraY, final double cameraZ) {
        final long gameTime = level.getGameTime();

        for (int slot = 0; slot < MAX_SHIPS; slot++) {
            if (!handles.shipSlotSupported[slot]) {
                // If this slot is unusable due to texture-unit limits (or missing uniforms), ensure it is disabled so
                // the shader's slot-N helpers early-out on GridSizeN <= 0.
                disableShipSlotProgram(handles, slot);
                continue;
            }
            if (slot >= ships.size()) {
                disableShipSlotProgram(handles, slot);
                continue;
            }

            final LoadedShip ship = ships.get(slot);
            final long shipId = ship.getId();

            final AABBdc shipWorldAabbDc = getShipWorldAabb(ship).orElse(null);
            if (shipWorldAabbDc == null) {
                disableShipSlotProgram(handles, slot);
                continue;
            }

            final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot =
                ShipWaterPocketManager.getClientWaterReachableSnapshot(level, shipId);
            if (snapshot == null) {
                disableShipSlotProgram(handles, slot);
                continue;
            }

            final ShipMasks masks = SHIP_MASKS.computeIfAbsent(shipId, ShipMasks::new);

            final int minX = snapshot.getMinX();
            final int minY = snapshot.getMinY();
            final int minZ = snapshot.getMinZ();
            final int sizeX = snapshot.getSizeX();
            final int sizeY = snapshot.getSizeY();
            final int sizeZ = snapshot.getSizeZ();
            final long geometryRevision = snapshot.getGeometryRevision();

            final boolean boundsChanged =
                masks.minX != minX || masks.minY != minY || masks.minZ != minZ ||
                    masks.sizeX != sizeX || masks.sizeY != sizeY || masks.sizeZ != sizeZ;

            if (boundsChanged || masks.geometryRevision != geometryRevision) {
                rebuildMask(level, masks, snapshot, minX, minY, minZ, sizeX, sizeY, sizeZ, geometryRevision);
            }
            applyPendingMaskBuild(masks, geometryRevision);

            final ShipTransform shipTransform = getShipTransform(ship);
            final Matrix4dc worldToShip = shipTransform.getWorldToShip();
            final double biasedM30 = worldToShip.m30() - (double) minX;
            final double biasedM31 = worldToShip.m31() - (double) minY;
            final double biasedM32 = worldToShip.m32() - (double) minZ;

            bindProgramMaskTexture(handles, slot, masks.maskTexId);

            // Slot uniforms.
            if (handles.shipAabbMinLoc[slot] >= 0) {
                GL20.glUniform4f(handles.shipAabbMinLoc[slot], (float) shipWorldAabbDc.minX(), (float) shipWorldAabbDc.minY(),
                    (float) shipWorldAabbDc.minZ(), 0.0f);
            }
            if (handles.shipAabbMaxLoc[slot] >= 0) {
                GL20.glUniform4f(handles.shipAabbMaxLoc[slot], (float) shipWorldAabbDc.maxX(), (float) shipWorldAabbDc.maxY(),
                    (float) shipWorldAabbDc.maxZ(), 0.0f);
            }

            // See comment in updateShipUniformsAndMasks() for precision rationale.
            if (handles.gridMinLoc[slot] >= 0) {
                GL20.glUniform4f(handles.gridMinLoc[slot], 0.0f, 0.0f, 0.0f, 0.0f);
            }
            if (handles.gridSizeLoc[slot] >= 0) {
                GL20.glUniform4f(handles.gridSizeLoc[slot], (float) sizeX, (float) sizeY, (float) sizeZ, 0.0f);
            }

            masks.worldToShip.set(
                (float) worldToShip.m00(), (float) worldToShip.m01(), (float) worldToShip.m02(), (float) worldToShip.m03(),
                (float) worldToShip.m10(), (float) worldToShip.m11(), (float) worldToShip.m12(), (float) worldToShip.m13(),
                (float) worldToShip.m20(), (float) worldToShip.m21(), (float) worldToShip.m22(), (float) worldToShip.m23(),
                (float) biasedM30, (float) biasedM31, (float) biasedM32, (float) worldToShip.m33()
            );
            uploadMatrixUniform(handles.worldToShipLoc[slot], masks.worldToShip);

            final double camShipX = worldToShip.m00() * cameraX + worldToShip.m10() * cameraY + worldToShip.m20() * cameraZ + biasedM30;
            final double camShipY = worldToShip.m01() * cameraX + worldToShip.m11() * cameraY + worldToShip.m21() * cameraZ + biasedM31;
            final double camShipZ = worldToShip.m02() * cameraX + worldToShip.m12() * cameraY + worldToShip.m22() * cameraZ + biasedM32;
            if (handles.cameraShipPosLoc[slot] >= 0) {
                GL20.glUniform3f(handles.cameraShipPosLoc[slot], (float) camShipX, (float) camShipY, (float) camShipZ);
            }
        }

        // Clear stale mask entries for ships that are no longer loaded to avoid leaks.
        final LongOpenHashSet loadedIds = new LongOpenHashSet();
        for (final LoadedShip loadedShip : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            loadedIds.add(loadedShip.getId());
        }
        SHIP_MASKS.entrySet().removeIf(entry -> {
            if (loadedIds.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
    }

    private static void disableShipSlot(final int slot) {
        SHADER.shader.setSampler("ValkyrienAir_Mask" + slot, 0);

        SHADER.shipAabbMin[slot].set(0.0f, 0.0f, 0.0f, 0.0f);
        SHADER.shipAabbMax[slot].set(-1.0f, -1.0f, -1.0f, 0.0f);
        SHADER.cameraShipPos[slot].set(0.0f, 0.0f, 0.0f);
        SHADER.gridMin[slot].set(0.0f, 0.0f, 0.0f, 0.0f);
        SHADER.gridSize[slot].set(0.0f, 0.0f, 0.0f, 0.0f);
        SHADER.worldToShip[slot].set(IDENTITY_MAT4);

        SHADER.shipAabbMin[slot].upload();
        SHADER.shipAabbMax[slot].upload();
        SHADER.cameraShipPos[slot].upload();
        SHADER.gridMin[slot].upload();
        SHADER.gridSize[slot].upload();
        SHADER.worldToShip[slot].upload();
    }

    private static void disableShipSlotProgram(final ProgramHandles handles, final int slot) {
        if (handles.shipAabbMinLoc[slot] >= 0) {
            GL20.glUniform4f(handles.shipAabbMinLoc[slot], 0.0f, 0.0f, 0.0f, 0.0f);
        }
        if (handles.shipAabbMaxLoc[slot] >= 0) {
            GL20.glUniform4f(handles.shipAabbMaxLoc[slot], -1.0f, -1.0f, -1.0f, 0.0f);
        }
        if (handles.cameraShipPosLoc[slot] >= 0) {
            GL20.glUniform3f(handles.cameraShipPosLoc[slot], 0.0f, 0.0f, 0.0f);
        }
        if (handles.gridMinLoc[slot] >= 0) {
            GL20.glUniform4f(handles.gridMinLoc[slot], 0.0f, 0.0f, 0.0f, 0.0f);
        }
        if (handles.gridSizeLoc[slot] >= 0) {
            GL20.glUniform4f(handles.gridSizeLoc[slot], 0.0f, 0.0f, 0.0f, 0.0f);
        }
        uploadMatrixUniform(handles.worldToShipLoc[slot], IDENTITY_MAT4);

        if (slot < handles.maxMaskSlots) {
            bindProgramMaskTexture(handles, slot, 0);
        }
    }

    private static void bindProgramMaskTexture(final ProgramHandles handles, final int slot, final int maskTexId) {
        if (handles == null) return;
        if (slot < 0 || slot >= handles.maxMaskSlots) return;

        final int unit = BASE_MASK_TEX_UNIT + slot;
        if (unit < 0 || unit >= handles.maxSafeTextureUnits) return;

        if (handles.maskLoc[slot] >= 0) {
            GL20.glUniform1i(handles.maskLoc[slot], unit);
        }

        final int liveMaskTexId = isLiveTextureId(maskTexId) ? maskTexId : 0;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(liveMaskTexId);

        // Avoid surprising other render code by leaving the active texture on a high unit.
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static void bindProgramFluidMaskTexture(final ProgramHandles handles, final int fluidMaskTexId) {
        if (handles == null) return;
        if (handles.fluidMaskLoc < 0) return;

        // Bind after the per-ship units.
        final int fluidUnit = BASE_MASK_TEX_UNIT + handles.maxMaskSlots;
        if (fluidUnit < 0 || fluidUnit >= handles.maxSafeTextureUnits) return;

        GL20.glUniform1i(handles.fluidMaskLoc, fluidUnit);
        final int liveFluidMaskTexId = isLiveTextureId(fluidMaskTexId) ? fluidMaskTexId : 0;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + fluidUnit);
        GlStateManager._bindTexture(liveFluidMaskTexId);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private static void uploadMatrixUniform(final int location, final Matrix4f matrix) {
        if (location < 0) return;
        final FloatBuffer buffer = MATRIX_BUFFER.get();
        buffer.clear();
        matrix.get(buffer);
        // JOML's Matrix4f#get(FloatBuffer) writes via absolute puts and does NOT advance the buffer position.
        // LWJGL uses the buffer's remaining() to determine how many values to upload.
        buffer.position(16);
        buffer.flip();
        GL20.glUniformMatrix4fv(location, false, buffer);
    }

    private static void rebuildMask(
        final ClientLevel level,
        final ShipMasks masks,
        final ShipWaterPocketManager.ClientWaterReachableSnapshot snapshot,
        final int minX,
        final int minY,
        final int minZ,
        final int sizeX,
        final int sizeY,
        final int sizeZ,
        final long geometryRevision
    ) {
        masks.geometryRevision = geometryRevision;
        masks.minX = minX;
        masks.minY = minY;
        masks.minZ = minZ;
        masks.sizeX = sizeX;
        masks.sizeY = sizeY;
        masks.sizeZ = sizeZ;

        final int volume = sizeX * sizeY * sizeZ;
        ensureMaskTextureStorage(masks, volume);

        applyPendingMaskBuild(masks, geometryRevision);
        if (masks.lastMaskUploadRevision == geometryRevision && masks.maskTexId != 0) return;
        if (masks.pendingMaskWordsFuture != null && masks.pendingMaskBuildRevision == geometryRevision) return;

        final VoxelShape[] shapeSnapshot = new VoxelShape[volume];
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int idx = 0;
        for (int lz = 0; lz < sizeZ; lz++) {
            for (int ly = 0; ly < sizeY; ly++) {
                for (int lx = 0; lx < sizeX; lx++) {
                    pos.set(minX + lx, minY + ly, minZ + lz);
                    final BlockState state = level.getBlockState(pos);
                    shapeSnapshot[idx++] = ShipWaterPocketClientCullBridge.buildCullVoxelShape(level, pos, state);
                }
            }
        }

        final BitSet interiorSnapshot =
            snapshot.getInterior() == null ? new BitSet() : (BitSet) snapshot.getInterior().clone();
        final BitSet openSnapshot =
            snapshot.getOpen() == null ? new BitSet() : (BitSet) snapshot.getOpen().clone();
        final BitSet waterReachableSnapshot =
            snapshot.getWaterReachable() == null ? new BitSet() : (BitSet) snapshot.getWaterReachable().clone();
        final BitSet submergedBoundarySnapshot = new BitSet(volume);
        for (int waterIdx = waterReachableSnapshot.nextSetBit(0); waterIdx >= 0 && waterIdx < volume;
             waterIdx = waterReachableSnapshot.nextSetBit(waterIdx + 1)) {
            if (!ShipWaterPocketLiquidOverlay.isOutsideSubmergedFluid(openSnapshot, interiorSnapshot, waterReachableSnapshot, waterIdx)) {
                continue;
            }
            if (ShipWaterPocketLiquidOverlay.touchesOverlayBoundary(
                openSnapshot,
                interiorSnapshot,
                waterReachableSnapshot,
                null,
                null,
                waterIdx,
                sizeX,
                sizeY,
                sizeZ
            )) {
                submergedBoundarySnapshot.set(waterIdx);
            }
        }

        final Supplier<int[]> task = () -> {
            final int[] occWords =
                ShipWaterPocketAsyncCull.buildOccMaskWords(shapeSnapshot, sizeX, sizeY, sizeZ, SUB);
            final int[] airWords = ShipWaterPocketAsyncCull.buildAirMaskWords(interiorSnapshot, volume);
            final int[] waterWords = ShipWaterPocketAsyncCull.buildAirMaskWords(submergedBoundarySnapshot, volume);
            final int[] out = new int[occWords.length + airWords.length + waterWords.length];
            System.arraycopy(occWords, 0, out, 0, occWords.length);
            System.arraycopy(airWords, 0, out, occWords.length, airWords.length);
            System.arraycopy(waterWords, 0, out, occWords.length + airWords.length, waterWords.length);
            return out;
        };

        final CompletableFuture<int[]> submitted =
            ShipPocketAsyncRuntime.trySubmitJava(ShipPocketAsyncSubsystem.CLIENT_CULL, task);
        if (submitted == null) {
            if (masks.pendingMaskWordsFuture != null) {
                masks.pendingMaskWordsFuture.cancel(true);
                masks.pendingMaskWordsFuture = null;
            }
            applyMaskWords(masks, task.get(), geometryRevision);
            return;
        }

        if (masks.pendingMaskWordsFuture != null) {
            masks.pendingMaskWordsFuture.cancel(true);
        }
        masks.pendingMaskWordsFuture = submitted;
        masks.pendingMaskBuildRevision = geometryRevision;
    }

    private static void ensureMaskTextureStorage(final ShipMasks masks, final int volume) {
        final int occWordCount = volume * OCC_WORDS_PER_VOXEL;
        final int airWordCount = (volume + 31) >> 5;
        final int waterWordCount = (volume + 31) >> 5;
        final int wordCount = occWordCount + airWordCount + waterWordCount;
        final int height = Math.max(1, (wordCount + MASK_TEX_WIDTH - 1) / MASK_TEX_WIDTH);

        boolean newOrResized = false;
        if (masks.maskTexId != 0 && (!isLiveTextureId(masks.maskTexId) || masks.maskTexHeight != height)) {
            if (isLiveTextureId(masks.maskTexId)) {
                TextureUtil.releaseTextureId(masks.maskTexId);
            }
            masks.maskTexId = 0;
            newOrResized = true;
        }

        final int prevId = masks.maskTexId;
        masks.maskTexId = ensureWordTexture(masks.maskTexId, MASK_TEX_WIDTH, height);
        masks.maskTexHeight = height;
        newOrResized |= (prevId == 0 && masks.maskTexId != 0);

        // Clear newly allocated storage to avoid undefined sampler reads during async rebuild.
        if (newOrResized && masks.maskTexId != 0) {
            final int capacity = MASK_TEX_WIDTH * height;
            final int byteCapacity = capacity * 4;
            if (masks.maskData == null || masks.maskData.length != capacity) {
                masks.maskData = new int[capacity];
            } else {
                Arrays.fill(masks.maskData, 0);
            }
            if (masks.maskBytes == null || masks.maskBytes.length != byteCapacity) {
                masks.maskBytes = new byte[byteCapacity];
                masks.maskByteBuffer = BufferUtils.createByteBuffer(byteCapacity);
            } else {
                Arrays.fill(masks.maskBytes, (byte) 0);
            }
            packMaskWords(masks);
            uploadWordTexture(masks.maskTexId, MASK_TEX_WIDTH, height, masks.maskByteBuffer);
            masks.lastMaskUploadRevision = Long.MIN_VALUE;
        }
    }

    private static void applyPendingMaskBuild(final ShipMasks masks, final long currentGeometryRevision) {
        final CompletableFuture<int[]> pending = masks.pendingMaskWordsFuture;
        if (pending == null || !pending.isDone()) return;

        final long uploadRevision = masks.pendingMaskBuildRevision;
        masks.pendingMaskWordsFuture = null;
        if (uploadRevision != currentGeometryRevision) return;

        final int[] words;
        try {
            words = pending.join();
        } catch (final Throwable ignored) {
            return;
        }
        applyMaskWords(masks, words, uploadRevision);
    }

    private static void applyMaskWords(final ShipMasks masks, final int[] words, final long uploadRevision) {
        if (!isLiveTextureId(masks.maskTexId)) {
            masks.maskTexId = 0;
            return;
        }
        final int capacity = MASK_TEX_WIDTH * masks.maskTexHeight;
        final int byteCapacity = capacity * 4;
        if (masks.maskData == null || masks.maskData.length != capacity) {
            masks.maskData = new int[capacity];
        } else {
            Arrays.fill(masks.maskData, 0);
        }
        if (masks.maskBytes == null || masks.maskBytes.length != byteCapacity) {
            masks.maskBytes = new byte[byteCapacity];
            masks.maskByteBuffer = BufferUtils.createByteBuffer(byteCapacity);
        } else {
            Arrays.fill(masks.maskBytes, (byte) 0);
        }

        final int maxWords = Math.min(words.length, masks.maskData.length);
        for (int wordIdx = 0; wordIdx < maxWords; wordIdx++) {
            final int texIdx =
                (wordIdx & MASK_TEX_WIDTH_MASK) + (wordIdx >> MASK_TEX_WIDTH_SHIFT) * MASK_TEX_WIDTH;
            if (texIdx < 0 || texIdx >= masks.maskData.length) continue;
            masks.maskData[texIdx] = words[wordIdx];
        }

        packMaskWords(masks);
        uploadWordTexture(masks.maskTexId, MASK_TEX_WIDTH, masks.maskTexHeight, masks.maskByteBuffer);
        masks.lastMaskUploadRevision = uploadRevision;
    }

    private static int ensureFluidMaskTexture(final ClientLevel level) {
        if (level == null) return 0;

        final Minecraft mc = Minecraft.getInstance();
        final AbstractTexture atlasTexture = mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS);
        if (atlasTexture == null) return 0;

        final int atlasTexId = atlasTexture.getId();
        if (!isLiveTextureId(atlasTexId)) return 0;

        int atlasWidth = 0;
        int atlasHeight = 0;

        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(atlasTexId);
            atlasWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            atlasHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        } finally {
            GlStateManager._bindTexture(prevBinding);
        }

        if (atlasWidth <= 0 || atlasHeight <= 0) return 0;

        if (fluidMaskTexId != 0 && (!isLiveTextureId(fluidMaskTexId) || fluidMaskWidth != atlasWidth || fluidMaskHeight != atlasHeight)) {
            if (isLiveTextureId(fluidMaskTexId)) {
                TextureUtil.releaseTextureId(fluidMaskTexId);
            }
            fluidMaskTexId = 0;
        }

        fluidMaskTexId = ensureByteTexture(fluidMaskTexId, atlasWidth, atlasHeight);
        fluidMaskWidth = atlasWidth;
        fluidMaskHeight = atlasHeight;
        applyPendingFluidMaskBuild(atlasTexId, atlasWidth, atlasHeight);

        final boolean needsRebuild =
            fluidMaskTexId == 0 ||
                fluidMaskWidth != atlasWidth ||
                fluidMaskHeight != atlasHeight ||
                fluidMaskLastAtlasTexId != atlasTexId;

        if (!needsRebuild) return fluidMaskTexId;
        if (pendingFluidMaskFuture != null &&
            pendingFluidMaskAtlasTexId == atlasTexId &&
            pendingFluidMaskWidth == atlasWidth &&
            pendingFluidMaskHeight == atlasHeight
        ) {
            return fluidMaskTexId;
        }

        final Function<ResourceLocation, TextureAtlasSprite> atlas = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        final List<TextureAtlasSprite> sprites = new ArrayList<>();

        // Always include vanilla fluids.
        sprites.add(atlas.apply(WATER_STILL));
        sprites.add(atlas.apply(WATER_FLOW));
        sprites.add(atlas.apply(WATER_OVERLAY));
        sprites.add(atlas.apply(LAVA_STILL));
        sprites.add(atlas.apply(LAVA_FLOW));

        // Best-effort: include modded fluids by enumerating registered fluids and asking their client render props.
        final HashSet<ResourceLocation> textureIds = new HashSet<>();
        final BlockPos fluidLookupPos = BlockPos.ZERO;
        for (final Fluid regFluid : BuiltInRegistries.FLUID) {
            try {
                final Fluid fluid = regFluid instanceof final FlowingFluid flowing ? flowing.getSource() : regFluid;
                final FluidState fs = fluid.defaultFluidState();

                final TextureAtlasSprite[] resolved = ShipWaterPocketFluidVisualHelper.getFluidSprites(level, fluidLookupPos, fluid, fs);
                if (resolved != null) {
                    for (final TextureAtlasSprite sprite : resolved) {
                        if (sprite == null) continue;
                        if (textureIds.add(sprite.contents().name())) {
                            sprites.add(sprite);
                        }
                    }
                }
            } catch (final Throwable ignored) {
                // Per-fluid errors should not break the entire mask.
            }
        }

        final HashSet<ResourceLocation> seenSprites = new HashSet<>();
        final int[] rects = new int[sprites.size() * 4];
        int rectCount = 0;
        for (final TextureAtlasSprite sprite : sprites) {
            if (sprite == null) continue;
            final ResourceLocation name = sprite.contents().name();
            if (!seenSprites.add(name)) continue;

            final int x0 = sprite.getX();
            final int y0 = sprite.getY();
            final int w = sprite.contents().width();
            final int h = sprite.contents().height();

            if (w <= 0 || h <= 0) continue;
            if (x0 < 0 || y0 < 0) continue;

            final int x1 = Math.min(atlasWidth, x0 + w);
            final int y1 = Math.min(atlasHeight, y0 + h);
            if (x1 <= x0 || y1 <= y0) continue;

            final int base = rectCount * 4;
            rects[base] = x0;
            rects[base + 1] = y0;
            rects[base + 2] = x1;
            rects[base + 3] = y1;
            rectCount++;
        }

        final int[] rectPayload = Arrays.copyOf(rects, rectCount * 4);
        final int buildWidth = atlasWidth;
        final int buildHeight = atlasHeight;
        final Supplier<byte[]> task =
            () -> ShipWaterPocketAsyncCull.paintFluidMask(buildWidth, buildHeight, rectPayload);
        final CompletableFuture<byte[]> submitted =
            ShipPocketAsyncRuntime.trySubmitJava(ShipPocketAsyncSubsystem.CLIENT_CULL, task);
        if (submitted == null) {
            if (pendingFluidMaskFuture != null) {
                pendingFluidMaskFuture.cancel(true);
                pendingFluidMaskFuture = null;
            }
            applyFluidMaskBytes(task.get(), atlasWidth, atlasHeight, atlasTexId);
            return fluidMaskTexId;
        }

        if (pendingFluidMaskFuture != null) {
            pendingFluidMaskFuture.cancel(true);
        }
        pendingFluidMaskFuture = submitted;
        pendingFluidMaskAtlasTexId = atlasTexId;
        pendingFluidMaskWidth = atlasWidth;
        pendingFluidMaskHeight = atlasHeight;
        return fluidMaskTexId;
    }

    private static void applyPendingFluidMaskBuild(final int atlasTexId, final int atlasWidth, final int atlasHeight) {
        if (pendingFluidMaskFuture == null || !pendingFluidMaskFuture.isDone()) return;
        if (pendingFluidMaskAtlasTexId != atlasTexId ||
            pendingFluidMaskWidth != atlasWidth ||
            pendingFluidMaskHeight != atlasHeight
        ) {
            pendingFluidMaskFuture = null;
            return;
        }

        final byte[] bytes;
        try {
            bytes = pendingFluidMaskFuture.join();
        } catch (final Throwable ignored) {
            pendingFluidMaskFuture = null;
            return;
        }
        pendingFluidMaskFuture = null;
        applyFluidMaskBytes(bytes, atlasWidth, atlasHeight, atlasTexId);
    }

    private static void applyFluidMaskBytes(
        final byte[] bytes,
        final int atlasWidth,
        final int atlasHeight,
        final int atlasTexId
    ) {
        final int capacity = atlasWidth * atlasHeight;
        if (fluidMaskData == null || fluidMaskData.length != capacity) {
            fluidMaskData = new byte[capacity];
            fluidMaskBuffer = BufferUtils.createByteBuffer(capacity);
        }
        Arrays.fill(fluidMaskData, (byte) 0);
        System.arraycopy(bytes, 0, fluidMaskData, 0, Math.min(bytes.length, fluidMaskData.length));

        fluidMaskBuffer.clear();
        fluidMaskBuffer.put(fluidMaskData);
        fluidMaskBuffer.flip();

        if (!isLiveTextureId(fluidMaskTexId)) {
            fluidMaskTexId = 0;
            fluidMaskLastAtlasTexId = 0;
            return;
        }
        uploadByteTexture(fluidMaskTexId, atlasWidth, atlasHeight, fluidMaskBuffer);
        fluidMaskLastAtlasTexId = atlasTexId;
    }

    private static Method findMethod(final Class<?> owner, final String name, final Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (final NoSuchMethodException ignored) {
            return null;
        } catch (final Throwable t) {
            return null;
        }
    }

    private static ResourceLocation invokeTexture(final Object ext, final Method noArgs, final Method withState,
        final FluidState fluidState, final ClientLevel level) throws Exception {
        if (noArgs != null) {
            final Object v = noArgs.invoke(ext);
            if (v instanceof final ResourceLocation rl) return rl;
        }
        if (withState != null && level != null) {
            final Object v = withState.invoke(ext, fluidState, level, BlockPos.ZERO);
            if (v instanceof final ResourceLocation rl) return rl;
        }
        return null;
    }

    private static void packMaskWords(final ShipMasks masks) {
        if (masks.maskData == null || masks.maskBytes == null || masks.maskByteBuffer == null) return;
        final int wordCount = Math.min(masks.maskData.length, masks.maskBytes.length / 4);
        for (int i = 0; i < wordCount; i++) {
            final int word = masks.maskData[i];
            final int base = i * 4;
            masks.maskBytes[base] = (byte) (word & 0xFF);
            masks.maskBytes[base + 1] = (byte) ((word >>> 8) & 0xFF);
            masks.maskBytes[base + 2] = (byte) ((word >>> 16) & 0xFF);
            masks.maskBytes[base + 3] = (byte) ((word >>> 24) & 0xFF);
        }
        masks.maskByteBuffer.clear();
        masks.maskByteBuffer.put(masks.maskBytes);
        masks.maskByteBuffer.flip();
    }

    private static int ensureWordTexture(final int existingId, final int width, final int height) {
        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (isLiveTextureId(existingId)) {
                GlStateManager._bindTexture(existingId);
                return existingId;
            }

            final int id = TextureUtil.generateTextureId();
            GlStateManager._bindTexture(id);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            return id;
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }

    private static int ensureByteTexture(final int existingId, final int width, final int height) {
        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (isLiveTextureId(existingId)) {
                GlStateManager._bindTexture(existingId);
                return existingId;
            }

            final int id = TextureUtil.generateTextureId();
            GlStateManager._bindTexture(id);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);

            return id;
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }

    private static void uploadByteTexture(final int texId, final int width, final int height, final java.nio.ByteBuffer data) {
        if (!isLiveTextureId(texId)) return;
        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GlStateManager._bindTexture(texId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, data);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }

    private static void uploadWordTexture(final int texId, final int width, final int height, final ByteBuffer data) {
        if (!isLiveTextureId(texId)) return;
        final int prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        final int prevUnpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GlStateManager._bindTexture(texId);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlignment);
            GlStateManager._bindTexture(prevBinding);
        }
    }
}
