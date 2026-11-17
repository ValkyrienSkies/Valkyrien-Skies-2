package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSRenderTypes;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.lod.LodCompat;
import org.valkyrienskies.mod.compat.lod.LodOcclusionTest;


public final class ShipBatchRenderer {

    public static final ShipBatchRenderer INSTANCE = new ShipBatchRenderer();

    private final Long2ObjectMap<ShipRenderObject> ships = new Long2ObjectOpenHashMap<>();
    private final ShipSectionCompiler compiler = new ShipSectionCompiler();
    private final LongOpenHashSet presentScratch = new LongOpenHashSet();
    private final ArrayList<BatchedShipDrawable> drawOrder = new ArrayList<>();

    private final ArrayList<ShipFrameData> frameData = new ArrayList<>();
    private int preparedFrameToken = -1;
    private int currentFrameToken = 0;
    private long lastLightPopulationGameTime = Long.MIN_VALUE;

    private final ShipTransformStorage transformStorage = new ShipTransformStorage();
    private static final int SHIP_TRANSFORMS_TEXTURE_UNIT = 4;

    private final Matrix4d localToCameraRelScratch = new Matrix4d();
    private final Matrix4d modelViewScratch = new Matrix4d();
    private final Matrix4f localToCameraRelFloat = new Matrix4f();
    private final Vector3d cameraInShip = new Vector3d();
    private final float[] shipFog = new float[2];
    private ClientLevel currentLevel;
    private double preparedCamX, preparedCamY, preparedCamZ;
    private boolean hasDeferredShips;
    private int deferredDrawnFrameToken = -1;

    private static final int MAX_SHIP_REMESH_PER_FRAME = 2;

    private static final class ShipFrameData {
        final Matrix4f modelView = new Matrix4f();
        double camShipX, camShipY, camShipZ;
        boolean visible;
        boolean deferred;
        // Slot of this ship's matrix in transformStorage (= the ShipIndex uniform value).
        int transformIndex;
        // ChunkOffset for the merged buffers: ship reference R minus the camera in ship space.
        // The merged vertices are stored relative to R, so this single offset covers the whole ship.
        float mergedOffsetX, mergedOffsetY, mergedOffsetZ;
        // Sections with sorted layers, back to front, computed once per frame and used by every sorted
        // layer's draw (sorted layers stay per section so their ordering is preserved).
        final ShipTranslucentOrder<ShipSectionMesh> sortedOrder =
            new ShipTranslucentOrder<>(ShipBatchRenderer::sortTranslucentSections);
    }

    private ShipBatchRenderer() {
    }

    public void markSectionDirty(final long shipId, final int sx, final int sy, final int sz) {
        // Block and chunk changes clear the light caches through their own hooks.
        // A render update can also come from light changes, which do not change shapes.
        final ShipRenderObject renderObject;
        synchronized (ships) {
            renderObject = ships.get(shipId);
        }
        if (renderObject != null) {
            renderObject.markSectionDirty(sx, sy, sz);
        }
    }

    public void markSectionLightDirty(final long shipId, final int sx, final int sy, final int sz) {
        final ShipRenderObject renderObject;
        synchronized (ships) {
            renderObject = ships.get(shipId);
        }
        if (renderObject != null) {
            renderObject.markSectionLightDirty(sx, sy, sz);
        }
    }

    public void onShipUnload(final long shipId) {
        final ShipRenderObject removed;
        synchronized (ships) {
            removed = ships.remove(shipId);
        }
        if (removed != null) {
            drawOrder.remove(removed);
            removed.close();
        }
    }

    public boolean hasRenderableShips() {
        return !drawOrder.isEmpty();
    }

    public void beginFrame(final ClientLevel level) {
        RenderSystem.assertOnRenderThread();
        currentFrameToken++;
        if (level == null) {
            freeAll();
            return;
        }
        if (currentLevel != null && level != currentLevel) {
            freeShipMeshes();
        }
        currentLevel = level;

        final long gameTime = level.getGameTime();
        if (gameTime != lastLightPopulationGameTime) {
            lastLightPopulationGameTime = gameTime;
            VsDynamicLight.populateWorldLightForBatched(level);
        }
        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        presentScratch.clear();

        int reMeshBudget = MAX_SHIP_REMESH_PER_FRAME;
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesBatchedRenderer(ship)) {
                continue;
            }
            presentScratch.add(ship.getId());
            ShipRenderObject renderObject;
            synchronized (ships) {
                renderObject = ships.get(ship.getId());
                if (renderObject == null) {
                    renderObject = new ShipRenderObject(ship);
                    ships.put(ship.getId(), renderObject);
                }
            }
            if (renderObject.ensureCompiled(level, dispatcher, compiler, reMeshBudget > 0)) {
                reMeshBudget--;
            }
        }

        synchronized (ships) {
            if (ships.size() != presentScratch.size()) {
                final var it = ships.long2ObjectEntrySet().iterator();
                while (it.hasNext()) {
                    final var entry = it.next();
                    if (!presentScratch.contains(entry.getLongKey())) {
                        entry.getValue().close();
                        it.remove();
                    }
                }
            }
            drawOrder.clear();
            for (final ShipRenderObject ship : ships.values()) drawOrder.add(ship);
        }
        DistantShips.collect(level, dispatcher, compiler, ships, drawOrder);
    }

    public void drawLayer(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final Frustum frustum) {

        final int layerIndex = ShipSectionMesh.layerIndex(renderType);
        if (layerIndex < 0 || drawOrder.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        prepareFrameData(poseStack, camX, camY, camZ, frustum);
        drawPreparedLayer(renderType, layerIndex, camX, camY, camZ, projectionMatrix, false);
    }

    public void drawDeferredShips(final Matrix4f projectionMatrix) {
        if (!hasDeferredShips || preparedFrameToken != currentFrameToken
            || deferredDrawnFrameToken == currentFrameToken || drawOrder.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        deferredDrawnFrameToken = currentFrameToken;
        for (final RenderType layer : ShipSectionMesh.CHUNK_LAYERS) {
            drawPreparedLayer(layer, ShipSectionMesh.layerIndex(layer), preparedCamX, preparedCamY, preparedCamZ,
                projectionMatrix, true);
        }
    }

    private void drawPreparedLayer(final RenderType renderType, final int layerIndex,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final boolean deferredPass) {

        renderType.setupRenderState();

        ShaderInstance shader = VSRenderTypes.Companion.shipBatchedShaderFor(renderType);
        final boolean usingBatchedShader = shader != null;
        if (shader == null) {
            shader = VSRenderTypes.Companion.shipShaderFor(renderType);
        }
        if (shader == null) {
            shader = RenderSystem.getShader();
        }
        if (shader == null) {
            renderType.clearRenderState();
            return;
        }

        for (int k = 0; k < 12; ++k) {
            shader.setSampler("Sampler" + k, RenderSystem.getShaderTexture(k));
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(projectionMatrix);
        }
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        shipFog[0] = RenderSystem.getShaderFogStart();
        shipFog[1] = RenderSystem.getShaderFogEnd();
        LodCompat.adjustShipFog(shipFog);
        if (shader.FOG_START != null) {
            shader.FOG_START.set(shipFog[0]);
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(shipFog[1]);
        }
        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        RenderSystem.setupShaderLights(shader);
        shader.apply();

        int shipIndexLoc = -1;
        if (usingBatchedShader) {
            final int programId = shader.getId();
            org.valkyrienskies.mod.common.render.light.ShipShadowRenderer.bind(programId);
            transformStorage.bind(SHIP_TRANSFORMS_TEXTURE_UNIT);
            final int samplerLoc = GL20.glGetUniformLocation(programId, "ShipTransforms");
            if (samplerLoc >= 0) {
                GL20.glUniform1i(samplerLoc, SHIP_TRANSFORMS_TEXTURE_UNIT);
            }
            shipIndexLoc = GL20.glGetUniformLocation(programId, "ShipIndex");

            final boolean dynamicLight = VSGameConfig.CLIENT.getDynamicShipLighting();
            final boolean shipLight = VSGameConfig.CLIENT.isShipToWorldLightingEnabled();
            setFeatureUniform(programId, "u_VsDynamicShipLighting", dynamicLight);
            setFeatureUniform(programId, "u_VsShipToWorldLighting", shipLight);
            setFeatureUniform(programId, "u_VsBetterShipShading", VSGameConfig.CLIENT.getBetterVanillaShipShading());
            LodCompat.bindShipShader(programId);
            if (dynamicLight) {
                VsDynamicLight.getLightStorage().bind(
                    VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT, VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);
            }

            bindSamplerUniform(programId, "u_VsLightSections", VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT);
            bindSamplerUniform(programId, "u_VsLightLut", VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);
            if (shipLight) VsDynamicLight.getShipEmitterList().bind(VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            bindSamplerUniform(programId, "u_VsShipEmitters", VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            final int emitterCountLoc = GL20.glGetUniformLocation(programId, "u_VsShipEmitterCount");
            if (emitterCountLoc >= 0) {
                GL20.glUniform1i(emitterCountLoc, shipLight ? VsDynamicLight.getShipEmitterList().size() : 0);
            }

            final int originX = (int) Math.floor(camX);
            final int originY = (int) Math.floor(camY);
            final int originZ = (int) Math.floor(camZ);
            final int originLoc = GL20.glGetUniformLocation(programId, "u_VsRenderOrigin");
            if (originLoc >= 0) {
                GL20.glUniform3i(originLoc, originX, originY, originZ);
            }
            final int fracLoc = GL20.glGetUniformLocation(programId, "u_VsCameraFrac");
            if (fracLoc >= 0) {
                GL20.glUniform3f(fracLoc,
                    (float) (camX - originX), (float) (camY - originY), (float) (camZ - originZ));
            }
        }

        final Uniform modelViewUniform = shader.MODEL_VIEW_MATRIX;
        final Uniform chunkOffsetUniform = shader.CHUNK_OFFSET;
        final boolean sorted = ShipSectionMesh.isSorted(layerIndex);

        for (int shipIdx = 0; shipIdx < frameData.size(); shipIdx++) {
            final ShipFrameData data = frameData.get(shipIdx);
            if (!data.visible || data.deferred != deferredPass) {
                continue;
            }
            if (sorted) {
                final ArrayList<ShipSectionMesh> order = data.sortedOrder.sections();
                boolean shipTransformSet = false;
                for (int i = 0; i < order.size(); i++) {
                    final ShipSectionMesh mesh = order.get(i);
                    final VertexBuffer buffer = mesh.getBuffer(layerIndex);
                    if (buffer == null) {
                        continue;
                    }
                    if (!shipTransformSet) {
                        setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                        shipTransformSet = true;
                    }
                    if (chunkOffsetUniform != null) {
                        chunkOffsetUniform.set(
                            (float) (mesh.originX - data.camShipX),
                            (float) (mesh.originY - data.camShipY),
                            (float) (mesh.originZ - data.camShipZ));
                        chunkOffsetUniform.upload();
                    }
                    buffer.bind();
                    buffer.draw();
                }
            } else {
                final ShipMesh mesh = drawOrder.get(shipIdx).getMesh();
                final VertexBuffer buffer = mesh == null ? null : mesh.getMerged(layerIndex);
                if (buffer == null) {
                    continue;
                }
                setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                if (chunkOffsetUniform != null) {
                    chunkOffsetUniform.set(data.mergedOffsetX, data.mergedOffsetY, data.mergedOffsetZ);
                    chunkOffsetUniform.upload();
                }
                buffer.bind();
                buffer.draw();
            }
        }

        if (chunkOffsetUniform != null) {
            chunkOffsetUniform.set(0.0f, 0.0f, 0.0f);
        }
        shader.clear();
        LodCompat.unbindShipShader();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    private static void setShipTransform(final ShipFrameData data, final boolean usingBatchedShader,
        final int shipIndexLoc, final Uniform modelViewUniform) {
        if (usingBatchedShader) {
            if (shipIndexLoc >= 0) {
                GL20.glUniform1i(shipIndexLoc, data.transformIndex);
            }
        } else if (modelViewUniform != null) {
            modelViewUniform.set(data.modelView);
            modelViewUniform.upload();
        }
    }

    private static void bindSamplerUniform(final int programId, final String name, final int unit) {
        final int loc = GL20.glGetUniformLocation(programId, name);
        if (loc >= 0) {
            GL20.glUniform1i(loc, unit);
        }
    }

    private static void setFeatureUniform(final int programId, final String name, final boolean enabled) {
        final int location = GL20.glGetUniformLocation(programId, name);
        if (location >= 0) GL20.glUniform1i(location, enabled ? 1 : 0);
    }

    private void prepareFrameData(final PoseStack levelPoseStack, final double camX, final double camY,
        final double camZ, final Frustum frustum) {
        if (preparedFrameToken == currentFrameToken) {
            return;
        }
        preparedFrameToken = currentFrameToken;

        while (frameData.size() < drawOrder.size()) {
            frameData.add(new ShipFrameData());
        }
        while (frameData.size() > drawOrder.size()) {
            frameData.remove(frameData.size() - 1);
        }

        transformStorage.beginFrame();
        preparedCamX = camX;
        preparedCamY = camY;
        preparedCamZ = camZ;
        hasDeferredShips = false;
        final float overpaint = LodCompat.overpaintDistanceBlocks();
        final double overpaintSq = Float.isFinite(overpaint) ? (double) overpaint * overpaint : Double.POSITIVE_INFINITY;
        final LodOcclusionTest lodOcclusion = LodCompat.occlusionTest();
        final Vector3d camScratch = cameraInShip;
        for (int i = 0; i < drawOrder.size(); i++) {
            final BatchedShipDrawable renderObject = drawOrder.get(i);
            final ShipFrameData data = frameData.get(i);
            data.deferred = false;
            if (renderObject.isEmpty()) {
                data.sortedOrder.clear();
                data.visible = true;
                continue;
            }

            final AABBdc worldAABB = renderObject.renderWorldAABB();
            if ((frustum != null && !frustum.isVisible(VectorConversionsMCKt.toMinecraft(worldAABB)))
                || (lodOcclusion != null && lodOcclusion.isHidden(worldAABB))) {
                data.visible = false;
                continue;
            }
            data.visible = true;
            if (overpaintSq != Double.POSITIVE_INFINITY
                && VSClientGameUtils.farthestCornerDistanceSq(worldAABB, camX, camY, camZ) > overpaintSq) {
                data.deferred = true;
                hasDeferredShips = true;
            }

            camScratch.set(camX, camY, camZ).sub(renderObject.renderPosition());
            renderObject.renderRotation().transformInverse(camScratch);
            final Vector3dc scaling = renderObject.renderScaling();
            camScratch.x /= scaling.x();
            camScratch.y /= scaling.y();
            camScratch.z /= scaling.z();
            camScratch.add(renderObject.renderPositionInModel());
            data.camShipX = camScratch.x;
            data.camShipY = camScratch.y;
            data.camShipZ = camScratch.z;

            ShipRenderMatrices.prepare(renderObject.renderShipToWorld(), levelPoseStack.last().pose(),
                camX, camY, camZ, data.camShipX, data.camShipY, data.camShipZ,
                localToCameraRelScratch, modelViewScratch, data.modelView, localToCameraRelFloat);

            data.transformIndex = transformStorage.append(data.modelView, localToCameraRelFloat);

            final ShipMesh mesh = renderObject.getMesh();
            data.mergedOffsetX = (float) (mesh.refX - data.camShipX);
            data.mergedOffsetY = (float) (mesh.refY - data.camShipY);
            data.mergedOffsetZ = (float) (mesh.refZ - data.camShipZ);

            if (!mesh.sortedSections.isEmpty()) {
                data.sortedOrder.update(mesh, mesh.sortedSections.values(),
                    data.camShipX, data.camShipY, data.camShipZ);
            } else {
                data.sortedOrder.clear();
            }
        }
        transformStorage.upload();
    }

    private static void sortTranslucentSections(final ArrayList<ShipSectionMesh> sections,
        final double cx, final double cy, final double cz) {
        sections.sort((a, b) ->
            Double.compare(sectionCenterDistSq(b, cx, cy, cz), sectionCenterDistSq(a, cx, cy, cz)));
    }

    private static double sectionCenterDistSq(final ShipSectionMesh mesh,
        final double cx, final double cy, final double cz) {
        final double dx = mesh.originX + 8.0 - cx;
        final double dy = mesh.originY + 8.0 - cy;
        final double dz = mesh.originZ + 8.0 - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    public void renderBlockEntities(final ClientLevel level, final PoseStack poseStack,
        final MultiBufferSource bufferSource, final Camera camera, final float partialTick) {
        if (drawOrder.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        final BlockEntityRenderDispatcher dispatcher =
            Minecraft.getInstance().getBlockEntityRenderDispatcher();
        final Vec3 cam = camera.getPosition();

        for (int shipIdx = 0; shipIdx < drawOrder.size(); shipIdx++) {
            // Distant ships have no block entities on the client.
            if (!(drawOrder.get(shipIdx) instanceof final ShipRenderObject renderObject)) {
                continue;
            }
            final List<BlockEntity> shipBlockEntities = renderObject.getBlockEntities(level);
            if (shipBlockEntities.isEmpty()) {
                continue;
            }
            final ShipTransform transform = renderObject.ship.getRenderTransform();
            for (int i = 0; i < shipBlockEntities.size(); i++) {
                final BlockEntity blockEntity = shipBlockEntities.get(i);
                final BlockPos pos = blockEntity.getBlockPos();
                poseStack.pushPose();
                VSClientGameUtils.transformRenderWithShip(transform, poseStack, pos,
                    cam.x(), cam.y(), cam.z());
                dispatcher.render(blockEntity, partialTick, poseStack, bufferSource);
                poseStack.popPose();
            }
        }
    }

    public void freeAll() {
        freeShipMeshes();
        VsDynamicLight.deleteStorages();
    }

    private void freeShipMeshes() {
        synchronized (ships) {
            for (final ShipRenderObject renderObject : ships.values()) {
                renderObject.close();
            }
            ships.clear();
        }
        drawOrder.clear();
        DistantShips.freeMeshes();
        frameData.clear();
        transformStorage.delete();
        preparedFrameToken = -1;
        currentLevel = null;
        lastLightPopulationGameTime = Long.MIN_VALUE;
    }
}
