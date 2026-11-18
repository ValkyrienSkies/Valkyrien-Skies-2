package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.client.render.ShipMaterialStack;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.api.render.ShipRenderView;
import org.valkyrienskies.mod.api.render.ShipGeometryMask;
import org.valkyrienskies.mod.client.render.ShipMaskTerrain;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSRenderTypes;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.render.light.ShipShadowRenderer;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;


public final class ShipBatchRenderer {

    public static final ShipBatchRenderer INSTANCE = new ShipBatchRenderer();

    private Long2ObjectMap<ShipRenderObject> ships = new Long2ObjectOpenHashMap<>();
    private final IdentityHashMap<ClientLevel, Long2ObjectMap<ShipRenderObject>> inactiveLevels = new IdentityHashMap<>();
    private final ShipSectionCompiler compiler = new ShipSectionCompiler();
    private final LongOpenHashSet presentScratch = new LongOpenHashSet();
    private final ArrayList<ShipRenderObject> drawOrder = new ArrayList<>();

    private final ArrayList<ShipFrameData> frameData = new ArrayList<>();
    private final ArrayList<ShipFrameData> translucentShips = new ArrayList<>();
    private int preparedFrameToken = -1;
    private ShipRenderView preparedView;
    private int currentFrameToken = 0;
    private long lastLightPopulationGameTime = Long.MIN_VALUE;

    private final ShipTransformStorage transformStorage = new ShipTransformStorage();
    private static final int SHIP_TRANSFORMS_TEXTURE_UNIT = 4;

    private final Matrix4d localToCameraRelScratch = new Matrix4d();
    private final Matrix4d modelViewScratch = new Matrix4d();
    private final Matrix4f localToCameraRelFloat = new Matrix4f();
    private final Vector3d cameraInShip = new Vector3d();
    private ClientLevel currentLevel;

    private static final int MAX_SHIP_REMESH_PER_FRAME = 2;

    private static final class ShipFrameData {
        final Matrix4f modelView = new Matrix4f();
        double camShipX, camShipY, camShipZ;
        boolean visible;
        ShipMesh mesh;
        ShipMaterialStack materials;
        boolean blended;
        int claimX, claimZ;
        double distanceSquared;
        // Slot of this ship's matrix in transformStorage (= the ShipIndex uniform value).
        int transformIndex;
        // ChunkOffset for the merged opaque buffers: ship reference R minus the camera in ship space.
        // The opaque vertices are stored relative to R, so this single offset covers the whole ship.
        float opaqueOffsetX, opaqueOffsetY, opaqueOffsetZ;
        // Translucent sections sorted back-to-front, computed once here and used by the translucent
        // layer's drawLayer (translucent stays per-section so its ordering is preserved).
        final ShipTranslucentOrder<ShipSectionMesh> translucentOrder =
            new ShipTranslucentOrder<>(ShipBatchRenderer::sortTranslucentSections);
    }

    private ShipBatchRenderer() {
    }

    public void markSectionDirty(final long shipId, final int sx, final int sy, final int sz) {
        synchronized (inactiveLevels) {
            final ShipRenderObject renderObject = ships.get(shipId);
            if (renderObject != null) renderObject.markSectionDirty(sx, sy, sz);
            for (final var meshes : inactiveLevels.values()) {
                final ShipRenderObject inactive = meshes.get(shipId);
                if (inactive != null) inactive.markSectionDirty(sx, sy, sz);
            }
        }
    }

    public void markSectionLightDirty(final long shipId, final int sx, final int sy, final int sz) {
        synchronized (inactiveLevels) {
            final ShipRenderObject renderObject = ships.get(shipId);
            if (renderObject != null) renderObject.markSectionLightDirty(sx, sy, sz);
            for (final var meshes : inactiveLevels.values()) {
                final ShipRenderObject inactive = meshes.get(shipId);
                if (inactive != null) inactive.markSectionLightDirty(sx, sy, sz);
            }
        }
    }

    public void onShipUnload(final long shipId) {
        final ShipRenderObject removed;
        synchronized (inactiveLevels) {
            removed = ships.remove(shipId);
            for (final var meshes : inactiveLevels.values()) {
                final ShipRenderObject inactive = meshes.remove(shipId);
                if (inactive != null) inactive.close();
            }
        }
        if (removed != null) {
            drawOrder.remove(removed);
            preparedFrameToken = -1;
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
        if (currentLevel != level) {
            synchronized (inactiveLevels) {
                if (currentLevel != null) inactiveLevels.put(currentLevel, ships);
                final var next = inactiveLevels.remove(level);
                ships = next == null ? new Long2ObjectOpenHashMap<>() : next;
                lastLightPopulationGameTime = Long.MIN_VALUE;
                currentLevel = level;
            }
        }

        final long gameTime = level.getGameTime();
        if (gameTime != lastLightPopulationGameTime) {
            lastLightPopulationGameTime = gameTime;
            VsDynamicLight.populateWorldLightForBatched(level);
        }
        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        presentScratch.clear();

        int reMeshBudget = MAX_SHIP_REMESH_PER_FRAME;
        final String dimension = VSGameUtilsKt.getDimensionId(level);
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesBatchedRenderer(ship) || !dimension.equals(ship.getChunkClaimDimension())) {
                continue;
            }
            presentScratch.add(ship.getId());
            ShipRenderObject renderObject;
            synchronized (inactiveLevels) {
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

        synchronized (inactiveLevels) {
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
    }

    public void drawLayer(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final Frustum frustum) {

        final int layerIndex = ShipSectionMesh.layerIndex(renderType);
        final ShipRenderView view = ShipRenderViews.getCurrent();
        if (view != null && view.getLevel() != currentLevel) beginFrame(view.getLevel());
        if (layerIndex < 0 || ships.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        prepareFrameData(poseStack, camX, camY, camZ, frustum);

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
        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
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
            if (renderType == RenderType.solid()) {
                ShipRenderViews.recordTerrain(poseStack, projectionMatrix, camX, camY, camZ);
            }
            transformStorage.bind(SHIP_TRANSFORMS_TEXTURE_UNIT);
            final int samplerLoc = GL20.glGetUniformLocation(programId, "ShipTransforms");
            if (samplerLoc >= 0) {
                GL20.glUniform1i(samplerLoc, SHIP_TRANSFORMS_TEXTURE_UNIT);
            }
            shipIndexLoc = GL20.glGetUniformLocation(programId, "ShipIndex");

            bindLighting(programId, camX, camY, camZ);
        }

        final Uniform modelViewUniform = shader.MODEL_VIEW_MATRIX;
        final Uniform chunkOffsetUniform = shader.CHUNK_OFFSET;
        final boolean translucent = renderType == RenderType.translucent();
        final ShipMaterialUniforms materialUniforms = usingBatchedShader ? ShipMaterialUniforms.forShader(shader) : null;
        final boolean useBlendedOrder = translucent && !translucentShips.isEmpty();
        final boolean depthWrite = useBlendedOrder && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int previousTable = materialUniforms == null ? -1 : materialUniforms.bindTable(ShipRenderViews.getMaterialTable());
        try {
            for (final ShipFrameData data : useBlendedOrder ? translucentShips : frameData) {
                if (!data.visible || data.mesh == null || (data.blended && !translucent && renderType != RenderType.tripwire())) {
                    continue;
                }
                if (materialUniforms != null) materialUniforms.select(data.materials);
                if (translucent) {
                    if (!data.blended && data.translucentOrder.sections().isEmpty()) continue;
                    setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                    if (data.blended) {
                        RenderSystem.depthMask(false);
                        try {
                            drawBlendedHull(data, chunkOffsetUniform, materialUniforms);
                            drawSections(data, layerIndex, chunkOffsetUniform, materialUniforms);
                        } finally {
                            RenderSystem.depthMask(depthWrite);
                        }
                    } else {
                        drawSections(data, layerIndex, chunkOffsetUniform, materialUniforms);
                    }
                } else {
                    final VertexBuffer buffer = data.mesh.getOpaque(layerIndex);
                    if (buffer == null) {
                        continue;
                    }
                    setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                    setOpaqueOrigin(data, chunkOffsetUniform, materialUniforms);
                    buffer.bind();
                    buffer.draw();
                }
            }
        } finally {
            if (materialUniforms != null) materialUniforms.select(null);
            ShipMaterialUniforms.restoreTable(previousTable);
        }

        if (chunkOffsetUniform != null) {
            chunkOffsetUniform.set(0.0f, 0.0f, 0.0f);
        }
        shader.clear();
        VertexBuffer.unbind();
        renderType.clearRenderState();
    }

    private static void drawSections(final ShipFrameData data, final int layer, final Uniform chunkOffset,
        final ShipMaterialUniforms uniforms) {
        for (final ShipSectionMesh section : data.translucentOrder.sections()) {
            final VertexBuffer buffer = section.getBuffer(layer);
            if (buffer == null) continue;
            if (uniforms != null && data.materials != null) {
                uniforms.origin(section.originX - data.claimX, section.originY, section.originZ - data.claimZ);
            }
            if (chunkOffset != null) {
                chunkOffset.set((float) (section.originX - data.camShipX),
                    (float) (section.originY - data.camShipY), (float) (section.originZ - data.camShipZ));
                chunkOffset.upload();
            }
            buffer.bind();
            buffer.draw();
        }
    }

    private static void setOpaqueOrigin(final ShipFrameData data, final Uniform chunkOffset,
        final ShipMaterialUniforms uniforms) {
        if (chunkOffset != null) {
            chunkOffset.set(data.opaqueOffsetX, data.opaqueOffsetY, data.opaqueOffsetZ);
            chunkOffset.upload();
        }
        if (uniforms != null && data.materials != null) {
            uniforms.origin(data.mesh.refX - data.claimX, data.mesh.refY, data.mesh.refZ - data.claimZ);
        }
    }

    private static void drawBlendedHull(final ShipFrameData data, final Uniform chunkOffset,
        final ShipMaterialUniforms uniforms) {
        setOpaqueOrigin(data, chunkOffset, uniforms);
        for (int layer = 0; layer < ShipSectionMesh.CHUNK_LAYERS.length; layer++) {
            final RenderType type = ShipSectionMesh.CHUNK_LAYERS[layer];
            if (type == RenderType.translucent() || type == RenderType.tripwire()) continue;
            final VertexBuffer buffer = data.mesh.getOpaque(layer);
            if (buffer == null) continue;
            uniforms.setDeferredLayer(type);
            buffer.bind();
            buffer.draw();
        }
        uniforms.setDeferredLayer(RenderType.translucent());
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

    static void cameraInShip(final ShipTransform transform, final double camX, final double camY,
        final double camZ, final Vector3d camScratch) {
        camScratch.set(camX, camY, camZ).sub(transform.getPosition());
        transform.getRotation().transformInverse(camScratch);
        final Vector3dc scaling = transform.getScaling();
        camScratch.x /= scaling.x();
        camScratch.y /= scaling.y();
        camScratch.z /= scaling.z();
        camScratch.add(transform.getPositionInModel());
    }

    static void bindLighting(final int programId, final double camX, final double camY, final double camZ) {
        ShipShadowRenderer.bind(programId);
        final boolean dynamicLight = VSGameConfig.CLIENT.getDynamicShipLighting();
        final boolean shipLight = VSGameConfig.CLIENT.isShipToWorldLightingEnabled();
        setFeatureUniform(programId, "u_VsDynamicShipLighting", dynamicLight);
        setFeatureUniform(programId, "u_VsShipToWorldLighting", shipLight);
        setFeatureUniform(programId, "u_VsBetterShipShading", VSGameConfig.CLIENT.getBetterVanillaShipShading());
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

    public ShipGeometryMask geometryMask(final ShipMaskTarget target, final ClientShip ship, final ShipRenderView view,
        final ShipMaskTerrain terrain) {
        final ShaderInstance shader = VSRenderTypes.Companion.getShipMaskShader();
        if (shader == null || !ShipRendererKt.getUsesBatchedRenderer(ship)) return null;
        final ShipRenderObject object;
        synchronized (inactiveLevels) {
            final var meshes = currentLevel == view.getLevel() ? ships : inactiveLevels.get(view.getLevel());
            object = meshes == null ? null : meshes.get(ship.getId());
        }
        if (object == null || object.ship != ship || object.isEmpty()) return null;
        return target.render(terrain, ship.getRenderAABB(), () ->
            ShipMaskDraw.draw(shader, terrain, ship, object.getMesh(), ShipRenderViews.materialsFor(ship)));
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
        final ShipRenderView view = ShipRenderViews.getCurrent();
        if (preparedFrameToken == currentFrameToken && preparedView == view) {
            return;
        }
        preparedFrameToken = currentFrameToken;
        preparedView = view;
        final Frustum viewFrustum = view == null ? frustum : view.getFrustum();

        while (frameData.size() < drawOrder.size()) {
            frameData.add(new ShipFrameData());
        }
        while (frameData.size() > drawOrder.size()) {
            frameData.remove(frameData.size() - 1);
        }

        transformStorage.beginFrame();
        translucentShips.clear();
        boolean hasBlended = false;
        final Vector3d camScratch = cameraInShip;
        for (int i = 0; i < drawOrder.size(); i++) {
            final ShipRenderObject renderObject = drawOrder.get(i);
            final ShipFrameData data = frameData.get(i);
            data.mesh = renderObject.getMesh();
            data.materials = null;
            data.blended = false;

            final ClientShip ship = renderObject.ship;
            if (!ShipRenderViews.shouldRenderShip(ship)) {
                data.visible = false;
                continue;
            }
            if (renderObject.isEmpty()) {
                data.translucentOrder.clear();
                data.visible = true;
                continue;
            }

            if (viewFrustum != null
                && !viewFrustum.isVisible(VectorConversionsMCKt.toMinecraft(ship.getRenderAABB()))) {
                data.visible = false;
                continue;
            }
            data.visible = true;

            final ShipTransform transform = ship.getRenderTransform();
            data.materials = ShipRenderViews.materialsFor(ship);
            if (data.materials != null) {
                data.claimX = ship.getChunkClaim().getXStart() << 4;
                data.claimZ = ship.getChunkClaim().getZStart() << 4;
                data.blended = data.materials.getBlended() &&
                    ShipMaterialUniforms.forShader(VSRenderTypes.Companion.getShipBatchedTranslucentShader()) != null;
                hasBlended |= data.blended;
            }
            cameraInShip(transform, camX, camY, camZ, camScratch);
            data.camShipX = camScratch.x;
            data.camShipY = camScratch.y;
            data.camShipZ = camScratch.z;

            ShipRenderMatrices.prepare(transform.getShipToWorld(), levelPoseStack.last().pose(),
                camX, camY, camZ, data.camShipX, data.camShipY, data.camShipZ,
                localToCameraRelScratch, modelViewScratch, data.modelView, localToCameraRelFloat);

            data.transformIndex = transformStorage.append(data.modelView, localToCameraRelFloat);

            final ShipMesh mesh = data.mesh;
            data.opaqueOffsetX = (float) (mesh.refX - data.camShipX);
            data.opaqueOffsetY = (float) (mesh.refY - data.camShipY);
            data.opaqueOffsetZ = (float) (mesh.refZ - data.camShipZ);

            if (!mesh.translucentSections.isEmpty()) {
                data.translucentOrder.update(mesh, mesh.translucentSections.values(),
                    data.camShipX, data.camShipY, data.camShipZ);
            } else {
                data.translucentOrder.clear();
            }
        }
        if (hasBlended) {
            for (int i = 0; i < frameData.size(); i++) {
                final ShipFrameData data = frameData.get(i);
                if (data.visible && data.mesh != null) {
                    data.distanceSquared = drawOrder.get(i).ship.getRenderTransform().getPosition().distanceSquared(camX, camY, camZ);
                    translucentShips.add(data);
                }
            }
            translucentShips.sort((a, b) -> Double.compare(b.distanceSquared, a.distanceSquared));
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
        if (currentLevel != null && currentLevel != level && ShipRenderViews.getCurrent() != null) beginFrame(level);
        if (drawOrder.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        final BlockEntityRenderDispatcher dispatcher =
            Minecraft.getInstance().getBlockEntityRenderDispatcher();
        final Vec3 cam = camera.getPosition();
        final ShipRenderView view = ShipRenderViews.getCurrent();
        for (final ShipRenderObject renderObject : List.copyOf(drawOrder)) {
            if (!ShipRenderViews.shouldRenderShip(renderObject.ship)) continue;
            if (view != null && !renderObject.isEmpty()
                && !view.getFrustum().isVisible(VectorConversionsMCKt.toMinecraft(renderObject.ship.getRenderAABB()))) continue;
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
        ShipMaskTarget.clearPool();
        freeShipMeshes();
        VsDynamicLight.deleteStorages();
    }

    public void releaseLevel(final ClientLevel level) {
        ShipMaskTarget.clearPool();
        synchronized (inactiveLevels) {
            final Long2ObjectMap<ShipRenderObject> meshes;
            if (currentLevel == level) {
                meshes = ships;
                ships = new Long2ObjectOpenHashMap<>();
                drawOrder.clear();
                frameData.clear();
                translucentShips.clear();
                preparedView = null;
                currentLevel = null;
                preparedFrameToken = -1;
            } else {
                meshes = inactiveLevels.remove(level);
            }
            if (meshes != null) meshes.values().forEach(ShipRenderObject::close);
        }
    }

    private void freeShipMeshes() {
        synchronized (inactiveLevels) {
            for (final var meshes : inactiveLevels.values()) meshes.values().forEach(ShipRenderObject::close);
            inactiveLevels.clear();
            for (final ShipRenderObject renderObject : ships.values()) {
                renderObject.close();
            }
            ships.clear();
        }
        drawOrder.clear();
        frameData.clear();
        translucentShips.clear();
        ShipMaterialUniforms.release();
        transformStorage.delete();
        preparedFrameToken = -1;
        preparedView = null;
        currentLevel = null;
        lastLightPopulationGameTime = Long.MIN_VALUE;
    }
}
