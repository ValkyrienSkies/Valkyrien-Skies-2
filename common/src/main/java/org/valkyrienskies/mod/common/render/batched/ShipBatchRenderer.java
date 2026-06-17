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
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSRenderTypes;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

public final class ShipBatchRenderer {

    public static final ShipBatchRenderer INSTANCE = new ShipBatchRenderer();

    private final Long2ObjectMap<ShipRenderObject> ships = new Long2ObjectOpenHashMap<>();
    private final ShipSectionCompiler compiler = new ShipSectionCompiler();
    private final LongOpenHashSet presentScratch = new LongOpenHashSet();
    private final ArrayList<ShipRenderObject> drawOrder = new ArrayList<>();

    private final ArrayList<ShipFrameData> frameData = new ArrayList<>();
    private int preparedFrameToken = -1;
    private int currentFrameToken = 0;
    private long lastLightPopulationGameTime = Long.MIN_VALUE;

    private final ShipTransformStorage transformStorage = new ShipTransformStorage();
    private static final int SHIP_TRANSFORMS_TEXTURE_UNIT = 4;

    private final Matrix4d localToCameraRelScratch = new Matrix4d();
    private final Matrix4f localToCameraRelFloat = new Matrix4f();

    private static final int MAX_SHIP_REMESH_PER_FRAME = 2;

    private static final class ShipFrameData {
        final Matrix4f modelView = new Matrix4f();
        double camShipX, camShipY, camShipZ;
        boolean visible;
        // Slot of this ship's matrix in transformStorage (= the ShipIndex uniform value).
        int transformIndex;
        // ChunkOffset for the merged opaque buffers: ship reference R minus the camera in ship space.
        // The opaque vertices are stored relative to R, so this single offset covers the whole ship.
        float opaqueOffsetX, opaqueOffsetY, opaqueOffsetZ;
        // Translucent sections sorted back-to-front, computed once here and used by the translucent
        // layer's drawLayer (translucent stays per-section so its ordering is preserved).
        final ArrayList<ShipSectionMesh> translucentOrder = new ArrayList<>();
    }

    private ShipBatchRenderer() {
    }

    public void markSectionDirty(final long shipId, final int sx, final int sy, final int sz) {
        final ShipRenderObject renderObject;
        synchronized (ships) {
            renderObject = ships.get(shipId);
        }
        if (renderObject != null) {
            renderObject.markSectionDirty(sx, sy, sz);
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

    public void beginFrame(final ClientLevel level) {
        RenderSystem.assertOnRenderThread();
        currentFrameToken++;
        if (level == null) {
            freeAll();
            return;
        }

        final long gameTime = level.getGameTime();
        if (gameTime != lastLightPopulationGameTime) {
            lastLightPopulationGameTime = gameTime;
            VsDynamicLight.populateWorldLightForBatched(level);
        }
        VsDynamicLight.populateShipEmittersForBatched(level);

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
            drawOrder.addAll(ships.values());
        }
    }

    public void drawLayer(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final Frustum frustum) {

        final int layerIndex = ShipSectionMesh.layerIndex(renderType);
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
            transformStorage.bind(SHIP_TRANSFORMS_TEXTURE_UNIT);
            final int samplerLoc = GL20.glGetUniformLocation(programId, "ShipTransforms");
            if (samplerLoc >= 0) {
                GL20.glUniform1i(samplerLoc, SHIP_TRANSFORMS_TEXTURE_UNIT);
            }
            shipIndexLoc = GL20.glGetUniformLocation(programId, "ShipIndex");

            VsDynamicLight.getLightStorage().bind(
                VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT, VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);

            bindSamplerUniform(programId, "u_VsLightSections", VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT);
            bindSamplerUniform(programId, "u_VsLightLut", VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);
            VsDynamicLight.getShipEmitterList().bind(VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            bindSamplerUniform(programId, "u_VsShipEmitters", VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            final int emitterCountLoc = GL20.glGetUniformLocation(programId, "u_VsShipEmitterCount");
            if (emitterCountLoc >= 0) {
                GL20.glUniform1i(emitterCountLoc, VsDynamicLight.getShipEmitterList().size());
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
        final boolean translucent = renderType == RenderType.translucent();

        for (int shipIdx = 0; shipIdx < frameData.size(); shipIdx++) {
            final ShipFrameData data = frameData.get(shipIdx);
            if (!data.visible) {
                continue;
            }
            if (translucent) {
                final ArrayList<ShipSectionMesh> order = data.translucentOrder;
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
                final VertexBuffer buffer = mesh == null ? null : mesh.getOpaque(layerIndex);
                if (buffer == null) {
                    continue;
                }
                setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                if (chunkOffsetUniform != null) {
                    chunkOffsetUniform.set(data.opaqueOffsetX, data.opaqueOffsetY, data.opaqueOffsetZ);
                    chunkOffsetUniform.upload();
                }
                buffer.bind();
                buffer.draw();
            }
        }

        if (chunkOffsetUniform != null) {
            chunkOffsetUniform.set(new Vector3f());
        }
        shader.clear();
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
        final Vector3d camScratch = new Vector3d();
        final PoseStack poseStack = levelPoseStack;
        for (int i = 0; i < drawOrder.size(); i++) {
            final ShipRenderObject renderObject = drawOrder.get(i);
            final ShipFrameData data = frameData.get(i);
            data.translucentOrder.clear();

            final ClientShip ship = renderObject.ship;
            if (renderObject.isEmpty()
                || (frustum != null
                    && !frustum.isVisible(VectorConversionsMCKt.toMinecraft(ship.getRenderAABB())))) {
                data.visible = false;
                continue;
            }
            data.visible = true;

            final ShipTransform transform = ship.getRenderTransform();
            camScratch.set(camX, camY, camZ);
            final Vector3dc camShip = transform.getWorldToShip().transformPosition(camScratch);
            data.camShipX = camShip.x();
            data.camShipY = camShip.y();
            data.camShipZ = camShip.z();

            poseStack.pushPose();
            VSClientGameUtils.transformRenderWithShip(transform, poseStack,
                camShip.x(), camShip.y(), camShip.z(), camX, camY, camZ);
            data.modelView.set(poseStack.last().pose());
            poseStack.popPose();

            final int originX = (int) Math.floor(camX);
            final int originY = (int) Math.floor(camY);
            final int originZ = (int) Math.floor(camZ);
            localToCameraRelScratch
                .translation(camX - originX, camY - originY, camZ - originZ)
                .translate(-camX, -camY, -camZ)
                .mul(transform.getShipToWorld())
                .translate(data.camShipX, data.camShipY, data.camShipZ);
            localToCameraRelFloat.set(localToCameraRelScratch);

            data.transformIndex = transformStorage.append(data.modelView, localToCameraRelFloat);

            final ShipMesh mesh = renderObject.getMesh();
            data.opaqueOffsetX = (float) (mesh.refX - data.camShipX);
            data.opaqueOffsetY = (float) (mesh.refY - data.camShipY);
            data.opaqueOffsetZ = (float) (mesh.refZ - data.camShipZ);

            data.translucentOrder.addAll(mesh.translucentSections.values());
            final double cx = data.camShipX;
            final double cy = data.camShipY;
            final double cz = data.camShipZ;
            data.translucentOrder.sort((a, b) ->
                Double.compare(sectionCenterDistSq(b, cx, cy, cz), sectionCenterDistSq(a, cx, cy, cz)));
        }
        transformStorage.upload();
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
            final ShipRenderObject renderObject = drawOrder.get(shipIdx);
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
        synchronized (ships) {
            for (final ShipRenderObject renderObject : ships.values()) {
                renderObject.close();
            }
            ships.clear();
        }
        drawOrder.clear();
        VsDynamicLight.deleteStorages();
        lastLightPopulationGameTime = Long.MIN_VALUE;
    }
}
