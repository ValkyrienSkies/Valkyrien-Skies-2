package org.valkyrienskies.mod.compat.sodium;

import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkStatus;
import me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import me.jellysquid.mods.sodium.client.render.viewport.CameraTransform;
import me.jellysquid.mods.sodium.client.render.viewport.Viewport;
import me.jellysquid.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.common.render.batched.ShipSectionMesh;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.hooks.VSGameEvents;
import org.valkyrienskies.mod.common.hooks.VSGameEvents.ShipRenderEventSodium;
import org.valkyrienskies.mod.compat.LoadedMods;
import org.valkyrienskies.mod.compat.VSRenderer;
import org.valkyrienskies.mod.compat.iris.IrisCompat;
import org.valkyrienskies.mod.compat.sodium.light.VsShipBiomeColorStorage;
import org.valkyrienskies.mod.compat.sodium.light.VsShipEmitterList;
import org.valkyrienskies.mod.compat.sodium.light.VsShipOccluderList;
import org.valkyrienskies.mod.compat.sodium.light.VsShipLightStorage;
import org.valkyrienskies.mod.compat.sodium.light.VsWorldFromShipLightStorage;
import org.valkyrienskies.mod.mixin.ValkyrienCommonMixinConfigPlugin;
import org.valkyrienskies.mod.mixin.mod_compat.sodium.RenderSectionManagerAccessor;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.RenderSectionManagerDuck;
import org.valkyrienskies.mod.mixinducks.mod_compat.sodium.SodiumWorldRendererDuck;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.joml.primitives.AABBdc;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

public class SodiumCompat {
    /**
     * Composite cache key for ship shader programs. Sodium's
     * {@link ChunkShaderOptions} alone isn't enough — our shaders branch on
     * compile-time defines derived from VS config, so we need a separate
     * compiled program per combination.
     */
    private record ShaderCacheKey(ChunkShaderOptions options, int vsFeatureBits) {}

    /** Bit flags for VS-specific shader feature defines. Package-visible because
     * {@link ShipThing} needs them at construction to decide which uniforms to bind. */
    static final int FEATURE_BIOME = 1;
    static final int FEATURE_LIGHT = 2;
    static final int FEATURE_SHADE = 4;
    /** Ship FSH also queries the world-from-ship storage (populated for the
     *  world chunk shader) so ship-A voxels can shadow / illuminate ship-B. */
    static final int FEATURE_SHIP_ON_SHIP = 8;

    static Map<ShaderCacheKey, GlProgram<ShipThing>> cachedPrograms = new HashMap<>();
    private static final ThreadLocal<Matrix4f> CURRENT_TRANSFORM = new ThreadLocal<>();
    private static final ThreadLocal<Matrix4f> CURRENT_LOCAL_TO_WORLD = new ThreadLocal<>();
    private static final ThreadLocal<int[]> CURRENT_RENDER_ORIGIN = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_RENDERING_SHIP = ThreadLocal.withInitial(() -> false);

    // Texture units used for the ship light buffer textures.
    // Sodium uses unit 0 for the block atlas and 2 for the lightmap (LIGHT_TEXTURE_TARGET).
    // Pick free units past those.
    public static final int LIGHT_SECTIONS_TEXTURE_UNIT = 6;
    public static final int LIGHT_LUT_TEXTURE_UNIT = 7;
    public static final int BIOME_SECTIONS_TEXTURE_UNIT = 8;
    public static final int BIOME_LUT_TEXTURE_UNIT = 9;
    /** Ship voxels projected into world coords for sky-occlusion + emitter
     *  contribution on the world's chunk shader (and ship-on-ship in the ship
     *  shader). Populated by {@link VsWorldFromShipLightStorage}. */
    public static final int WORLD_FROM_SHIP_SECTIONS_TEXTURE_UNIT = 10;
    public static final int WORLD_FROM_SHIP_LUT_TEXTURE_UNIT = 11;
    /** Buffer texture (RGBA32F) holding the per-frame ship-emitter list as
     *  vec4(worldX, worldY, worldZ, lightLevel) entries. Used by both the
     *  world chunk shader (ship lights world) and ship chunk shader (ship
     *  lights other ships) for sub-block-precise glow that tracks ship
     *  motion smoothly. */
    public static final int SHIP_EMITTER_LIST_TEXTURE_UNIT = 12;
    /** Buffer texture (RGBA32F) holding the per-frame ship-occluder list as
     *  vec4(worldX, worldY, worldZ, 0) entries — every solid voxel of every
     *  loaded ship. Used by the world chunk shader's per-fragment ship AO so
     *  the shadow shape follows ship rotation/translation continuously
     *  (cell-storage-based AO can only morph between cell-aligned configs). */
    public static final int SHIP_OCCLUDER_LIST_TEXTURE_UNIT = 13;

    private static VsShipBiomeColorStorage biomeStorage;

    private static final double WORLD_FROM_SHIP_VISIBILITY_PADDING = 32.0;

    /** Cached VS world chunk programs, keyed by sodium's render-pass options. */
    private static final Map<ChunkShaderOptions, GlProgram<WorldThing>> cachedWorldPrograms = new HashMap<>();

    public static VsShipLightStorage getLightStorage() {
        return VsDynamicLight.getLightStorage();
    }

    public static VsShipBiomeColorStorage getBiomeStorage() {
        if (biomeStorage == null) {
            biomeStorage = new VsShipBiomeColorStorage();
        }
        return biomeStorage;
    }

    public static VsWorldFromShipLightStorage getWorldFromShipStorage() {
        return VsDynamicLight.getWorldFromShipStorage();
    }

    public static VsShipEmitterList getShipEmitterList() {
        return VsDynamicLight.getShipEmitterList();
    }

    public static VsShipOccluderList getShipOccluderList() {
        return VsDynamicLight.getShipOccluderList();
    }

    public static void deleteStorages() {
        if (biomeStorage != null) {
            biomeStorage.delete();
            biomeStorage = null;
        }
        VsDynamicLight.deleteStorages();
    }

    /**
     * Populate the world-from-ship storage AND the ship-emitter list,
     * called from {@code MixinLevelRenderer.updateDynamicLight} so the data updates every game tick.
     */
    public static void populateWorldFromShipsForFrame(net.minecraft.client.multiplayer.ClientLevel level) {
        populateWorldFromShipsForFrame(level, null);
    }

    public static void populateWorldFromShipsForFrame(net.minecraft.client.multiplayer.ClientLevel level,
            Viewport viewport) {
        if (!VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) return;
        if (level == null) return;
        VsWorldFromShipLightStorage storage = getWorldFromShipStorage();
        VsShipEmitterList emitters = getShipEmitterList();
        VsShipOccluderList occluders = getShipOccluderList();
        storage.beginFrame();
        emitters.beginFrame();
        occluders.beginFrame();
        org.valkyrienskies.mod.common.VSGameUtilsKt.getShipObjectWorld(
                net.minecraft.client.Minecraft.getInstance()).getLoadedShips().forEach(ship -> {
            ClientShip cs = ship;
            if (!isShipRelevantToWorldFromShipFrame(cs, viewport)) return;
            storage.populateFromShip(level, cs, emitters, occluders);
        });
        storage.pruneUnused();
        storage.upload();
        emitters.upload();
        occluders.upload();
    }

    public static void populateLightSectionStorage(ClientLevel level) {
        if (!VSGameConfig.CLIENT.getDynamicShipLighting()) return;
        final VsShipLightStorage storage = getLightStorage();
        storage.beginFrame();
        for (ClientShip clientShip : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            final AABBdc aabb = clientShip.getRenderAABB();
            storage.requestSectionsInAabb(level,
                aabb.minX(), aabb.minY(), aabb.minZ(),
                aabb.maxX(), aabb.maxY(), aabb.maxZ());
        }
        storage.pruneUnused();
        storage.upload();
    }

    public static void populateBiomeSectionStorage(ClientLevel level) {
        if (!VSGameConfig.CLIENT.getDynamicShipBiomeTinting()) return;
        final VsShipBiomeColorStorage biomeStorageLocal = getBiomeStorage();
        biomeStorageLocal.beginFrame();
        for (ClientShip clientShip : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            final AABBdc aabb = clientShip.getRenderAABB();
            biomeStorageLocal.requestSectionsInAabb(level,
                aabb.minX(), aabb.minY(), aabb.minZ(),
                aabb.maxX(), aabb.maxY(), aabb.maxZ());
        }
        biomeStorageLocal.pruneUnused();
        biomeStorageLocal.upload();
    }

    private static boolean isShipRelevantToWorldFromShipFrame(ClientShip ship, Viewport viewport) {
        if (viewport == null) return true;
        final AABBdc aabb = ship.getRenderAABB();
        if (aabb == null) return false;
        return isExpandedAabbVisible(viewport, aabb, WORLD_FROM_SHIP_VISIBILITY_PADDING);
    }

    private static boolean isExpandedAabbVisible(Viewport viewport, AABBdc aabb, double padding) {
        final double minX = aabb.minX() - padding;
        final double minY = aabb.minY() - padding;
        final double minZ = aabb.minZ() - padding;
        final double maxX = aabb.maxX() + padding;
        final double maxY = aabb.maxY() + padding;
        final double maxZ = aabb.maxZ() + padding;
        final double centerX = (minX + maxX) * 0.5;
        final double centerY = (minY + maxY) * 0.5;
        final double centerZ = (minZ + maxZ) * 0.5;
        final int x = Mth.floor(centerX);
        final int y = Mth.floor(centerY);
        final int z = Mth.floor(centerZ);
        final float extentX = (float) ((maxX - minX) * 0.5 + Math.abs(centerX - x) + 1.0);
        final float extentY = (float) ((maxY - minY) * 0.5 + Math.abs(centerY - y) + 1.0);
        final float extentZ = (float) ((maxZ - minZ) * 0.5 + Math.abs(centerZ - z) + 1.0);
        return viewport.isBoxVisible(x, y, z, extentX, extentY, extentZ);
    }

    public static GlProgram<ChunkShaderInterface> getOrCreateShipProgram(ChunkShaderOptions options) {
        int features = computeFeatureBits();
        ShaderCacheKey key = new ShaderCacheKey(options, features);
        GlProgram<ShipThing> program = cachedPrograms.get(key);
        if (program == null) {
            program = createShader("blocks/block_layer_opaque", options, features);
            cachedPrograms.put(key, program);
        }
        return (GlProgram<ChunkShaderInterface>) (Object) program;
    }

    /** Snapshot the VS shader-feature config bits at program-build time. */
    private static int computeFeatureBits() {
        int bits = 0;
        if (VSGameConfig.CLIENT.getDynamicShipBiomeTinting()) bits |= FEATURE_BIOME;
        if (VSGameConfig.CLIENT.getDynamicShipLighting()) bits |= FEATURE_LIGHT;
        if (VSGameConfig.CLIENT.getBetterVanillaShipShading()) bits |= FEATURE_SHADE;
        if (VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) bits |= FEATURE_SHIP_ON_SHIP;
        return bits;
    }

    /**
     * True iff at least one of the three ship-shader features is enabled. When
     * false there's no reason to swap in the VS ship shader at all — the
     * mesher mixin's gates fall through to sodium's native byte format and
     * sodium's stock chunk shader can render ship blocks correctly.
     */
    public static boolean anyShipShaderFeatureEnabled() {
        return computeFeatureBits() != 0;
    }

    public static void setupShipShaderState(GlProgram<ChunkShaderInterface> program, ChunkRenderMatrices matrices, Matrix4fc transformMatrix) {
        ShipThing shipInterface = (ShipThing) program.getInterface();
        shipInterface.setupState();
        // Set projection and model-view matrices
        shipInterface.setProjectionMatrix(matrices.projection());
        shipInterface.setModelViewMatrix(matrices.modelView());
        // Set transform matrix (identity if not provided)
        shipInterface.setTransformMatrix(transformMatrix != null ? transformMatrix : new Matrix4f().identity());
        // Local-to-world maps the ship-local vertex space (after sodium's chunk
        // translation) into absolute world block coordinates so the shader can
        // look up world-space block/sky lighting.
        Matrix4f localToWorld = CURRENT_LOCAL_TO_WORLD.get();
        shipInterface.setLocalToWorldMatrix(localToWorld != null ? localToWorld : new Matrix4f().identity());
        int[] origin = CURRENT_RENDER_ORIGIN.get();
        if (origin != null) {
            shipInterface.setRenderOrigin(origin[0], origin[1], origin[2]);
        } else {
            shipInterface.setRenderOrigin(0, 0, 0);
        }
        shipInterface.setLightSectionsSampler(LIGHT_SECTIONS_TEXTURE_UNIT);
        shipInterface.setLightLutSampler(LIGHT_LUT_TEXTURE_UNIT);
        shipInterface.setBiomeSectionsSampler(BIOME_SECTIONS_TEXTURE_UNIT);
        shipInterface.setBiomeLutSampler(BIOME_LUT_TEXTURE_UNIT);
        shipInterface.setShipEmitters(SHIP_EMITTER_LIST_TEXTURE_UNIT, getShipEmitterList().size());
        shipInterface.setShipOccluders(SHIP_OCCLUDER_LIST_TEXTURE_UNIT, getShipOccluderList().size());
    }

    /** Stores transform for the next render() call on the current thread. */
    public static void pushTransform(Matrix4f transform) {
        CURRENT_TRANSFORM.set(transform);
    }

    /** Retrieves and clears the stored transform for this thread. */
    public static Matrix4f popTransform() {
        Matrix4f transform = CURRENT_TRANSFORM.get();
        CURRENT_TRANSFORM.remove();
        return transform;
    }

    public static void pushLocalToWorld(Matrix4f m) {
        CURRENT_LOCAL_TO_WORLD.set(m);
    }

    public static void pushRenderOrigin(int x, int y, int z) {
        CURRENT_RENDER_ORIGIN.set(new int[] { x, y, z });
    }

    public static boolean isRenderingShip() {
        return IS_RENDERING_SHIP.get();
    }

    public static void onChunkAdded(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusAdded(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
            markShipSectionCacheDirty(level, x, z);
            if (VSGameUtilsKt.getShipManagingPos(level, x, z) instanceof final ClientShip ship
                    && ShipRendererKt.getUsesBatchedRenderer(ship)) {
                for (int sy = level.getMinSection(); sy < level.getMaxSection(); sy++) {
                    ShipBatchRenderer.INSTANCE.markSectionDirty(ship.getId(), x, sy, z);
                }
            }
        }
    }

    public static void onChunkRemoved(final ClientLevel level, final int x, final int z) {
        if (ValkyrienCommonMixinConfigPlugin.getVSRenderer() == VSRenderer.SODIUM) {
            ChunkTrackerHolder.get(level).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_HAS_BLOCK_DATA);
            markShipSectionCacheDirty(level, x, z);
        }
    }

    public static void markShipRenderListsDirty() {
        final SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer instanceof SodiumWorldRendererDuck duck) {
            duck.vs$markShipRenderListsDirty();
        }
    }

    public static void markShipSectionCacheDirty(final ClientShip ship) {
        final SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer instanceof SodiumWorldRendererDuck duck) {
            duck.vs$invalidateShipSectionCache(ship);
        }
    }

    public static void markShipSectionCacheDirty(final ClientLevel level, final int x, final int z) {
        if (VSGameUtilsKt.getShipManagingPos(level, x, z) instanceof ClientShip ship) {
            markShipSectionCacheDirty(ship);
        } else {
            markShipRenderListsDirty();
        }
    }

    public static void vsRenderLayer(RenderSectionManager renderSectionManager, ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z,
            CommandList commandList) {

        VSGameEvents.INSTANCE.getShipsStartRenderingSodium().emit(new VSGameEvents.ShipStartRenderEventSodium(
            pass, matrices, x, y, z
        ));
        final boolean dynamicLight = VSGameConfig.CLIENT.getDynamicShipLighting();
        final boolean dynamicBiome = VSGameConfig.CLIENT.getDynamicShipBiomeTinting();
        final VsShipLightStorage storage = dynamicLight ? getLightStorage() : null;
        final VsShipBiomeColorStorage biomeStorageLocal = dynamicBiome ? getBiomeStorage() : null;
        final ArrayList<ClientShip> renderableShips = new ArrayList<>();
        final ArrayList<SortedRenderLists> renderableRenderLists = new ArrayList<>();
        ((RenderSectionManagerDuck) renderSectionManager).vs_getShipRenderLists().forEach((ship, renderList) -> {
            if (hasRenderableGeometryForPass(renderList, pass)) {
                renderableShips.add(ship);
                renderableRenderLists.add(renderList);
            }
        });
        if (renderableShips.isEmpty()) {
            return;
        }

        for (int i = 0; i < renderableShips.size(); i++) {
            final ClientShip ship = renderableShips.get(i);
            final SortedRenderLists renderList = renderableRenderLists.get(i);
            VSGameEvents.INSTANCE.getRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
            final ShipTransform shipTransform = ship.getRenderTransform();

            final float distanceScaling = 1 / (float) shipTransform.getShipToWorldScaling().x();
            final float initialFogStart = RenderSystem.getShaderFogStart();
            final float initialFogEnd = RenderSystem.getShaderFogEnd();

            if (distanceScaling != 1f) {
                RenderSystem.setShaderFogStart(initialFogStart * distanceScaling);
                RenderSystem.setShaderFogEnd(initialFogEnd * distanceScaling);
            }

            final Vector3dc cameraShipSpace = shipTransform.getWorldToShip().transformPosition(new Vector3d(x, y, z));
            final Matrix4dc s = ship.getRenderTransform().getShipToWorld();
            final Matrix4d newModelView = new Matrix4d(matrices.modelView())
                .translate(-x, -y, -z)
                .mul(s)
                .translate(cameraShipSpace);

            // Build a precision-friendly matrix that maps a sodium-chunk-local vertex
            // pos to (worldPos - renderOrigin), where renderOrigin is the integer
            // camera world block position. Combined with the ivec3 renderOrigin
            // uniform, the shader can reconstruct an exact world block pos for the
            // flywheel-style light fetch.
            //
            // We want `M * p = worldPos - origin = S*(p + cameraShipSpace) - origin`.
            // To stay precision-friendly when origin can be ~30M, we build:
            //   T(camera-origin) * T(-camera) * S * T(cameraShipSpace)
            // = T(-origin) * S * T(cameraShipSpace)
            // where the FINAL translation column equals (cameraWorld - origin) ~= frac
            // and is therefore safe to truncate to float.
            final int originX = (int) Math.floor(x);
            final int originY = (int) Math.floor(y);
            final int originZ = (int) Math.floor(z);
            final Matrix4d localToCameraRel = new Matrix4d()
                .translate(x - originX, y - originY, z - originZ)
                .translate(-x, -y, -z)
                .mul(s)
                .translate(cameraShipSpace);

            final ChunkRenderMatrices newMatrices =
                new ChunkRenderMatrices(matrices.projection(), new Matrix4f(newModelView));
            DefaultChunkRenderer chunkRenderer = (DefaultChunkRenderer) ((RenderSectionManagerAccessor) renderSectionManager).getChunkRenderer();

            // Stash uniforms for the mixin's redirected begin() to consume
            pushTransform(new Matrix4f(s));
            pushLocalToWorld(new Matrix4f(localToCameraRel));
            pushRenderOrigin(originX, originY, originZ);
            IS_RENDERING_SHIP.set(true);

            // Bind the world-light + biome-color buffer textures so the ship
            // shader can sample them. Bound only when the corresponding feature
            // is enabled; the shaders' #ifdef gates ensure the matching sampler
            // is never read when its feature is off.
            if (storage != null) storage.bind(LIGHT_SECTIONS_TEXTURE_UNIT, LIGHT_LUT_TEXTURE_UNIT);
            if (biomeStorageLocal != null) biomeStorageLocal.bind(BIOME_SECTIONS_TEXTURE_UNIT, BIOME_LUT_TEXTURE_UNIT);
            // Same world-from-ship storage the world chunk shader queries —
            // bound here so the ship shader can read it for ship-on-ship.
            if (VSGameConfig.CLIENT.getDynamicShipToWorldLighting()) {
                getShipEmitterList().bind(SHIP_EMITTER_LIST_TEXTURE_UNIT);
            }

            chunkRenderer.render(newMatrices, commandList, renderList, pass,
                new CameraTransform(cameraShipSpace.x(), cameraShipSpace.y(), cameraShipSpace.z()));
            IS_RENDERING_SHIP.set(false);

             if (distanceScaling != 1f) {
                RenderSystem.setShaderFogStart(initialFogStart);
                RenderSystem.setShaderFogEnd(initialFogEnd);
            }

            VSGameEvents.INSTANCE.getPostRenderShipSodium().emit(new ShipRenderEventSodium(pass, matrices, x, y, z, ship, renderList));
        }
    }

    private static boolean hasRenderableGeometryForPass(final SortedRenderLists renderList, final TerrainRenderPass pass) {
        final Iterator<ChunkRenderList> iterator = renderList.iterator(pass.isReverseOrder());
        while (iterator.hasNext()) {
            final ChunkRenderList chunkRenderList = iterator.next();
            if (pass == DefaultTerrainRenderPasses.SOLID && chunkRenderList.getSectionsWithEntitiesCount() > 0) {
                return true;
            }
            if (chunkRenderList.getSectionsWithGeometryCount() <= 0) {
                continue;
            }

            final SectionRenderDataStorage storage = chunkRenderList.getRegion().getStorage(pass);
            if (storage == null) {
                continue;
            }

            final ByteIterator sections = chunkRenderList.sectionsWithGeometryIterator(pass.isReverseOrder());
            if (sections == null) {
                continue;
            }

            while (sections.hasNext()) {
                final int sectionIndex = sections.nextByteAsInt();
                if (SectionRenderDataUnsafe.getSliceMask(storage.getDataPointer(sectionIndex)) != 0) {
                    return true;
                }
            }
        }
        return false;
    }


    public static void renderShips(RenderSectionManager renderSectionManager, RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z) {
        if (renderLayer == RenderType.solid()) {
            renderShipsForPass(renderSectionManager, matrices, DefaultTerrainRenderPasses.SOLID, x, y, z);
            renderShipsForPass(renderSectionManager, matrices, DefaultTerrainRenderPasses.CUTOUT, x, y, z);
        } else if (renderLayer == RenderType.translucent()) {
            renderShipsForPass(renderSectionManager, matrices, DefaultTerrainRenderPasses.TRANSLUCENT, x, y, z);
        }
        renderBatchedShips(renderLayer, matrices, x, y, z);
    }

    public static void renderBatchedShips(RenderType renderLayer, ChunkRenderMatrices matrices,
            double x, double y, double z) {
        if (LoadedMods.getIris() && IrisCompat.isIrisShaderActive()) {
            return;
        }
        final PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(new Matrix4f(matrices.modelView()));
        final Matrix4f projection = new Matrix4f(matrices.projection());

        if (renderLayer == RenderType.solid()) {
            ShipBatchRenderer.INSTANCE.beginFrame(net.minecraft.client.Minecraft.getInstance().level);
            for (final RenderType layer : ShipSectionMesh.CHUNK_LAYERS) {
                if (layer == RenderType.translucent()) {
                    continue;
                }
                ShipBatchRenderer.INSTANCE.drawLayer(layer, poseStack, x, y, z, projection, null);
            }
        } else if (renderLayer == RenderType.translucent()) {
            ShipBatchRenderer.INSTANCE.drawLayer(RenderType.translucent(), poseStack, x, y, z, projection, null);
        }
    }

    private static void renderShipsForPass(RenderSectionManager renderSectionManager, ChunkRenderMatrices matrices,
            TerrainRenderPass pass, double x, double y, double z) {
        CommandList commandList = RenderDevice.INSTANCE.createCommandList();
        try {
            vsRenderLayer(renderSectionManager, matrices, pass, x, y, z, commandList);
        } finally {
            commandList.close();
            IS_RENDERING_SHIP.set(false);
        }
    }

    public static GlProgram<ChunkShaderInterface> getOrCreateWorldProgram(ChunkShaderOptions options) {
        GlProgram<WorldThing> program = cachedWorldPrograms.get(options);
        if (program == null) {
            program = createWorldShader("blocks/world_layer_opaque", options);
            cachedWorldPrograms.put(options, program);
        }
        return (GlProgram<ChunkShaderInterface>) (Object) program;
    }

    public static void setupWorldShaderState(GlProgram<ChunkShaderInterface> program, ChunkRenderMatrices matrices) {
        WorldThing wt = (WorldThing) program.getInterface();
        wt.setupState();
        wt.setProjectionMatrix(matrices.projection());
        wt.setModelViewMatrix(matrices.modelView());

        // Sodium hands the VSH `position = vertex - cameraExact`. We want
        // `vertex - floor(camera)` so that floor() at the fragment is stable
        // as the camera's fractional drifts through an integer boundary. The
        // VSH adds u_VsCameraFrac to convert; the FSH then does
        //   block = floor(v_CameraRelWorldPos) + u_VsRenderOrigin
        // = floor(vertex - floor(camera)) + floor(camera) = floor(vertex).
        net.minecraft.world.phys.Vec3 cameraPos =
                net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int ox = (int) Math.floor(cameraPos.x);
        int oy = (int) Math.floor(cameraPos.y);
        int oz = (int) Math.floor(cameraPos.z);
        wt.setRenderOrigin(ox, oy, oz);
        wt.setCameraFrac(
                (float) (cameraPos.x - ox),
                (float) (cameraPos.y - oy),
                (float) (cameraPos.z - oz));
        wt.setShipEmitters(SHIP_EMITTER_LIST_TEXTURE_UNIT, getShipEmitterList().size());
        wt.setShipOccluders(SHIP_OCCLUDER_LIST_TEXTURE_UNIT, getShipOccluderList().size());
    }

    private static GlProgram<WorldThing> createWorldShader(String path, ChunkShaderOptions options) {
        ShaderConstants constants = createWorldShaderConstants(options);

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                new ResourceLocation("valkyrienskies", path + ".vsh"), constants);
        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                new ResourceLocation("valkyrienskies", path + ".fsh"), constants);

        try {
            return GlProgram.builder(new ResourceLocation("valkyrienskies", "world_chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Position", ChunkShaderBindingPoints.ATTRIBUTE_POSITION)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE)
                    .bindAttribute("a_LightAndData", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .link((shader) -> new WorldThing(shader, options));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    private static ShaderConstants createWorldShaderConstants(ChunkShaderOptions options) {
        // Sodium's stock chunk shader uses USE_FRAGMENT_DISCARD / USE_FOG /
        // USE_VANILLA_COLOR_FORMAT defines from the pass options. We want the
        // same set so the world shader handles cutout / translucent passes
        // correctly. USE_VANILLA_COLOR_FORMAT is dropped — our shader doesn't
        // implement that compatibility branch.
        ShaderConstants.Builder builder = ShaderConstants.builder();
        for (String define : options.constants().getDefineStrings()) {
            String[] parts = define.split("\\s+", 3);
            if (parts.length < 2 || !"#define".equals(parts[0])) continue;
            String name = parts[1];
            if ("USE_VANILLA_COLOR_FORMAT".equals(name)) continue;
            if (parts.length == 2) builder.add(name);
            else builder.add(name, parts[2]);
        }
        return builder.build();
    }

    /**
     * True when ship-to-world dynamic lighting should override sodium's stock
     * chunk shader. Gated only on the config because the mesher mixin packs
     * face-slot bits into the alpha byte for world chunks whenever the config
     * is on; sodium's stock shader would misinterpret those bits as plain AO,
     * so the swap MUST stay aligned with the packing — no "skip when no ships
     * are nearby" optimization. The world shader runs fine with empty storage
     * (LUT lookups return 0, emitter loop runs 0 times, no visible effect).
     */
    public static boolean shouldUseWorldFromShipShader() {
        return VSGameConfig.CLIENT.getDynamicShipToWorldLighting();
    }

    private static GlProgram<ShipThing> createShader(String path, ChunkShaderOptions options, int features) {
        ShaderConstants constants = createShipShaderConstants(options, features);

        GlShader vertShader = ShaderLoader.loadShader(ShaderType.VERTEX,
                new ResourceLocation("valkyrienskies", path + ".vsh"), constants);
        
        GlShader fragShader = ShaderLoader.loadShader(ShaderType.FRAGMENT,
                new ResourceLocation("valkyrienskies", path + ".fsh"), constants);

        try {
            return GlProgram.builder(new ResourceLocation("valkyrienskies", "chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Position", ChunkShaderBindingPoints.ATTRIBUTE_POSITION)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_TEXTURE)
                    .bindAttribute("a_LightAndData", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_MATERIAL_INDEX)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .link((shader) -> new ShipThing(shader, options, features));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    private static ShaderConstants createShipShaderConstants(ChunkShaderOptions options, int features) {
        ShaderConstants.Builder builder = ShaderConstants.builder();

        for (String define : options.constants().getDefineStrings()) {
            String[] parts = define.split("\\s+", 3);
            if (parts.length < 2 || !"#define".equals(parts[0])) {
                throw new IllegalArgumentException("Unexpected shader define format: " + define);
            }

            String name = parts[1];
            if ("USE_VANILLA_COLOR_FORMAT".equals(name)) {
                continue;
            }

            if (parts.length == 2) {
                builder.add(name);
            } else {
                builder.add(name, parts[2]);
            }
        }

        // VS-specific feature defines, gated by config. Compile-time `#ifdef`
        // in the VSH/FSH means disabled features cost nothing on the GPU.
        if ((features & FEATURE_BIOME) != 0) builder.add("VS_DYNAMIC_BIOME");
        if ((features & FEATURE_LIGHT) != 0) builder.add("VS_DYNAMIC_LIGHT");
        if ((features & FEATURE_SHADE) != 0) builder.add("VS_DYNAMIC_SHADE");
        if ((features & FEATURE_SHIP_ON_SHIP) != 0) builder.add("VS_SHIP_ON_SHIP");

        return builder.build();
    }
}
