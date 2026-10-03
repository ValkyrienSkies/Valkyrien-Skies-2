package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
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
import org.joml.primitives.AABBd;
import org.lwjgl.opengl.GL20;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.VSRenderTypes;
import org.valkyrienskies.mod.common.config.ShipRendererKt;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.sodium.light.VsShipEmitterList;

public final class ShipBatchRenderer {

    public static final ShipBatchRenderer INSTANCE = new ShipBatchRenderer();

    private final ShipRenderObjects ships = new ShipRenderObjects();
    private final ShipSectionCompiler compiler = new ShipSectionCompiler();
    private final LongOpenHashSet presentScratch = new LongOpenHashSet();
    private final ArrayList<ShipRenderObject> drawOrder = new ArrayList<>();

    private final ArrayList<ShipFrameData> frameData = new ArrayList<>();
    private int preparedFrameToken = -1;
    private int currentFrameToken = 0;
    private int remeshCursor;
    private final IdentityHashMap<ShaderInstance, ShaderBindings> shaderBindings = new IdentityHashMap<>();
    private final AABBd batchBounds = new AABBd();
    private VsShipEmitterList batchedEmitters;
    private boolean hasVisibleGeometry;

    private final ShipTransformStorage transformStorage = new ShipTransformStorage();
    private static final int SHIP_TRANSFORMS_TEXTURE_UNIT = 4;

    private final Matrix4d localToCameraRelScratch = new Matrix4d();
    private final Matrix4f localToCameraRelFloat = new Matrix4f();

    private static final int MAX_BATCH_REMESH_PER_FRAME = 4;
    // A soft limit checked between batches; one eight-section compile may exceed it.
    private static final long REMESH_BUDGET_NANOS = 2_000_000L;

    private static final class ShaderBindings {
        final int transforms, shipIndex, lightSections, lightLut, emitters, emitterCount, emitterIndices, origin;
        ShaderBindings(final ShaderInstance shader) {
            final int program = shader.getId();
            transforms = GL20.glGetUniformLocation(program, "ShipTransforms");
            shipIndex = GL20.glGetUniformLocation(program, "ShipIndex");
            lightSections = GL20.glGetUniformLocation(program, "u_VsLightSections");
            lightLut = GL20.glGetUniformLocation(program, "u_VsLightLut");
            emitters = GL20.glGetUniformLocation(program, "u_VsShipEmitters");
            emitterCount = GL20.glGetUniformLocation(program, "u_VsShipEmitterCount");
            emitterIndices = GL20.glGetUniformLocation(program, "u_VsShipEmitterIndices[0]");
            origin = GL20.glGetUniformLocation(program, "u_VsRenderOrigin");
        }
    }

    private static final class ShipFrameData {
        final Matrix4f modelView = new Matrix4f();
        double camShipX, camShipY, camShipZ;
        boolean visible;
        // Slot of this ship's matrix in transformStorage (= the ShipIndex uniform value).
        int transformIndex;
        final ArrayList<ShipMesh> visibleBatches = new ArrayList<>();
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

    public void onShipUnload(final ClientShip ship) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> onShipUnload(ship));
            return;
        }
        final ShipRenderObject removed;
        synchronized (ships) {
            // A queued unload of the old instance must not evict an already reloaded ship.
            removed = ships.removeInstance(ship);
        }
        if (removed != null) {
            drawOrder.remove(removed);
            removed.close();
            preparedFrameToken = -1;
        }
    }

    public void markColumnDirty(final ClientLevel level, final long shipId, final int x, final int z) {
        synchronized (ships) {
            final ShipRenderObject object = ships.get(shipId);
            if (object != null) object.markColumnDirty(level, x, z);
        }
    }

    public void beginFrame(final ClientLevel level) {
        RenderSystem.assertOnRenderThread();
        currentFrameToken++;
        if (level == null) {
            freeAll();
            return;
        }

        presentScratch.clear();
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!ShipRendererKt.getUsesBatchedRenderer(ship)) {
                continue;
            }
            presentScratch.add(ship.getId());
            ShipRenderObject renderObject;
            synchronized (ships) {
                renderObject = ships.getOrCreate(ship);
            }
            renderObject.pollChunks(level);
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
        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        final long deadline = System.nanoTime() + REMESH_BUDGET_NANOS;
        int compiled = 0;
        int idle = 0;
        boolean attempted = false;
        while (!drawOrder.isEmpty() && compiled < MAX_BATCH_REMESH_PER_FRAME && idle < drawOrder.size()) {
            if (attempted && System.nanoTime() >= deadline) break;
            attempted = true;
            remeshCursor = Math.floorMod(remeshCursor, drawOrder.size());
            final ShipRenderObject object = drawOrder.get(remeshCursor++);
            if (object.compileNextBatch(level, dispatcher, compiler, deadline)) {
                compiled++;
                idle = 0;
            } else {
                idle++;
            }
        }
        if (drawOrder.isEmpty()) return;
        // The terrain renderer owns the shared emitter list. Keep our batch indices stable
        // across layers without overwriting emitters belonging to terrain-rendered ships.
        if (batchedEmitters == null) batchedEmitters = new VsShipEmitterList();
        batchedEmitters.beginFrame();
        for (final ShipRenderObject object : drawOrder) {
            for (final ShipMesh mesh : object.getMeshes()) {
                batchedEmitters.appendShipEmitters(object.ship, mesh.emitters);
            }
        }
        batchedEmitters.upload();
    }

    public void drawLayer(final RenderType renderType, final PoseStack poseStack,
        final double camX, final double camY, final double camZ, final Matrix4f projectionMatrix,
        final Frustum frustum) {

        final int layerIndex = ShipSectionMesh.layerIndex(renderType);
        if (layerIndex < 0 || ships.isEmpty()) {
            return;
        }
        RenderSystem.assertOnRenderThread();

        prepareFrameData(poseStack, camX, camY, camZ, projectionMatrix, frustum);
        if (!hasVisibleGeometry) return;

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

        final ShaderBindings bindings = usingBatchedShader
            ? shaderBindings.computeIfAbsent(shader, ShaderBindings::new) : null;
        final int shipIndexLoc = bindings == null ? -1 : bindings.shipIndex;
        if (bindings != null) {
            transformStorage.bind(SHIP_TRANSFORMS_TEXTURE_UNIT);
            setSampler(bindings.transforms, SHIP_TRANSFORMS_TEXTURE_UNIT);
            VsDynamicLight.getLightStorage().bind(
                VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT, VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);
            setSampler(bindings.lightSections, VsDynamicLight.LIGHT_SECTIONS_TEXTURE_UNIT);
            setSampler(bindings.lightLut, VsDynamicLight.LIGHT_LUT_TEXTURE_UNIT);
            batchedEmitters.bind(VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            setSampler(bindings.emitters, VsDynamicLight.SHIP_EMITTER_LIST_TEXTURE_UNIT);
            if (bindings.origin >= 0) {
                GL20.glUniform3i(bindings.origin, (int) Math.floor(camX), (int) Math.floor(camY),
                    (int) Math.floor(camZ));
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
                    setBatchEmitters(bindings, mesh.owner);
                    buffer.bind();
                    buffer.draw();
                }
            } else {
                boolean shipTransformSet = false;
                for (final ShipMesh mesh : data.visibleBatches) {
                    final VertexBuffer buffer = mesh.getOpaque(layerIndex);
                    if (buffer == null) continue;
                    if (!shipTransformSet) {
                        setShipTransform(data, usingBatchedShader, shipIndexLoc, modelViewUniform);
                        shipTransformSet = true;
                    }
                    if (chunkOffsetUniform != null) {
                        chunkOffsetUniform.set((float) (mesh.refX - data.camShipX),
                            (float) (mesh.refY - data.camShipY), (float) (mesh.refZ - data.camShipZ));
                        chunkOffsetUniform.upload();
                    }
                    setBatchEmitters(bindings, mesh);
                    buffer.bind();
                    buffer.draw();
                }
            }
        }

        if (chunkOffsetUniform != null) {
            chunkOffsetUniform.set(0.0f, 0.0f, 0.0f);
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

    private static void setSampler(final int location, final int unit) {
        if (location >= 0) GL20.glUniform1i(location, unit);
    }

    private static void setBatchEmitters(final ShaderBindings bindings, final ShipMesh mesh) {
        if (bindings == null) return;
        if (bindings.emitterCount >= 0) GL20.glUniform1i(bindings.emitterCount, mesh.selectedEmitterCount);
        if (bindings.emitterIndices >= 0 && mesh.selectedEmitterCount > 0) {
            GL20.glUniform1iv(bindings.emitterIndices, mesh.selectedEmitters);
        }
    }

    private void prepareFrameData(final PoseStack levelPoseStack, final double camX, final double camY,
        final double camZ, final Matrix4f projection, final Frustum suppliedFrustum) {
        if (preparedFrameToken == currentFrameToken) {
            return;
        }
        preparedFrameToken = currentFrameToken;
        hasVisibleGeometry = false;

        while (frameData.size() < drawOrder.size()) {
            frameData.add(new ShipFrameData());
        }
        while (frameData.size() > drawOrder.size()) {
            frameData.remove(frameData.size() - 1);
        }

        // Use this render pass's camera on both vanilla and Sodium/Embeddium.
        final Frustum frustum = suppliedFrustum == null
            ? new Frustum(levelPoseStack.last().pose(), projection) : suppliedFrustum;
        if (suppliedFrustum == null) frustum.prepare(camX, camY, camZ);
        final ClientLevel level = Minecraft.getInstance().level;
        final var worldLight = VsDynamicLight.getLightStorage();
        worldLight.beginFrame();
        VsDynamicLight.requestTerrainShipLight(level);
        transformStorage.beginFrame();
        final Vector3d camScratch = new Vector3d();
        final PoseStack poseStack = levelPoseStack;
        for (int i = 0; i < drawOrder.size(); i++) {
            final ShipRenderObject renderObject = drawOrder.get(i);
            final ShipFrameData data = frameData.get(i);
            data.translucentOrder.clear();
            data.visibleBatches.clear();

            final ClientShip ship = renderObject.ship;
            if (renderObject.isEmpty()
                || !frustum.isVisible(VectorConversionsMCKt.toMinecraft(ship.getRenderAABB()).inflate(1.0))) {
                data.visible = false;
                continue;
            }
            data.visible = true;

            final ShipTransform transform = ship.getRenderTransform();
            camScratch.set(camX, camY, camZ).sub(transform.getPosition());
            transform.getRotation().transformInverse(camScratch);
            final Vector3dc scaling = transform.getScaling();
            camScratch.x /= scaling.x();
            camScratch.y /= scaling.y();
            camScratch.z /= scaling.z();
            camScratch.add(transform.getPositionInModel());
            data.camShipX = camScratch.x;
            data.camShipY = camScratch.y;
            data.camShipZ = camScratch.z;

            poseStack.pushPose();
            VSClientGameUtils.transformRenderWithShip(transform, poseStack,
                data.camShipX, data.camShipY, data.camShipZ, camX, camY, camZ);
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

            for (final ShipMesh mesh : renderObject.getMeshes()) {
                if (mesh.isEmpty()) continue;
                final var shipBounds = ship.getShipAABB();
                if (shipBounds == null) continue;
                if (mesh.refX >= shipBounds.maxX() || mesh.refY >= shipBounds.maxY()
                    || mesh.refZ >= shipBounds.maxZ()
                    || mesh.refX + ShipMeshBatches.BLOCKS_PER_AXIS <= shipBounds.minX()
                    || mesh.refY + ShipMeshBatches.BLOCKS_PER_AXIS <= shipBounds.minY()
                    || mesh.refZ + ShipMeshBatches.BLOCKS_PER_AXIS <= shipBounds.minZ()) continue;
                batchBounds.setMin(Math.max(mesh.refX, shipBounds.minX()) - 1.0,
                    Math.max(mesh.refY, shipBounds.minY()) - 1.0,
                    Math.max(mesh.refZ, shipBounds.minZ()) - 1.0);
                batchBounds.setMax(Math.min(mesh.refX + ShipMeshBatches.BLOCKS_PER_AXIS, shipBounds.maxX()) + 1.0,
                    Math.min(mesh.refY + ShipMeshBatches.BLOCKS_PER_AXIS, shipBounds.maxY()) + 1.0,
                    Math.min(mesh.refZ + ShipMeshBatches.BLOCKS_PER_AXIS, shipBounds.maxZ()) + 1.0);
                batchBounds.transform(transform.getShipToWorld());
                if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(batchBounds))) continue;
                data.visibleBatches.add(mesh);
                hasVisibleGeometry = true;
                data.translucentOrder.addAll(mesh.translucentSections.values());
                mesh.selectedEmitterCount = batchedEmitters.selectForBounds(batchBounds, mesh.selectedEmitters);
                worldLight.requestSectionsInAabb(level, batchBounds.minX(), batchBounds.minY(), batchBounds.minZ(),
                    batchBounds.maxX(), batchBounds.maxY(), batchBounds.maxZ());
            }
            final double cx = data.camShipX;
            final double cy = data.camShipY;
            final double cz = data.camShipZ;
            data.translucentOrder.sort((a, b) ->
                Double.compare(sectionCenterDistSq(b, cx, cy, cz), sectionCenterDistSq(a, cx, cy, cz)));

        }
        worldLight.pruneUnused();
        worldLight.upload();
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
            if (shipIdx < frameData.size() && !frameData.get(shipIdx).visible) {
                continue;
            }
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
        frameData.clear();
        preparedFrameToken = -1;
        remeshCursor = 0;
        shaderBindings.clear();
        if (batchedEmitters != null) {
            batchedEmitters.delete();
            batchedEmitters = null;
        }
    }
}
