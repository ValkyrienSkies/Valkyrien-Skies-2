package org.valkyrienskies.mod.common.render.batched;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.valkyrienskies.core.api.ships.ClientShip;

/**
 * Draws one ship from a camera of the caller's choosing, into whatever framebuffer is bound.
 *
 * <p>For anything that wants a ship as a picture rather than as part of the world: a monitor showing
 * the ship it is bolted to, a map, an icon. Everything else that draws a ship does so from the
 * world's one camera and has nowhere to put a second — the vanilla path goes through Minecraft's
 * chunk renderer and the Sodium path through Sodium's, and neither takes a camera as an argument.
 *
 * <p>This uses the improved render API's canonical {@link ShipBatchRenderer} geometry. The same
 * compiled object backs world views and portraits, so renderer selection never creates a second
 * terrain pipeline and registered ship materials remain compatible with the official programs.
 *
 * <p>Drawn with the ordinary chunk shaders rather than the batched ones. Those want a ship transform
 * texture, the dynamic light storage, an emitter list and a render origin, all of which exist to put
 * a ship in the right place in a world; a portrait is not in a world, and what is wanted from it is
 * the geometry.
 */
public final class ShipPortraitRenderer {

    private ShipPortraitRenderer() {
    }

    /**
     * Draws a ship as seen from a given point in its own shipyard coordinates.
     *
     * <p>Must be called on the render thread with a framebuffer bound, which is to say from inside
     * the level render rather than from a screen.
     *
     * @param modelView  the view matrix, already including any rotation the caller wants
     * @param projection the projection matrix
     * @param camX       the camera position, in the ship's own shipyard coordinates
     * @param opaqueOnly skip the translucent layer, which has no meaningful sort order without a
     *                   world to sort it against
     * @param fullBright light the ship as though the sun were on it, rather than by the block and
     *                   sky light of wherever in the shipyard it happens to live. A shipyard is
     *                   sealed and unlit, so a portrait taken by its own light is a silhouette
     * @return whether anything was drawn
     */
    public static boolean render(final ClientLevel level, final ClientShip ship,
        final Matrix4f modelView, final Matrix4f projection,
        final double camX, final double camY, final double camZ, final boolean opaqueOnly,
        final boolean fullBright) {

        RenderSystem.assertOnRenderThread();
        if (level == null || ship == null) {
            return false;
        }

        final ShipRenderObject object =
            ShipBatchRenderer.INSTANCE.acquirePortraitObject(level, ship);

        final ShipMesh mesh = object.getMesh();
        if (mesh == null) {
            return false;
        }

        // A portrait has no distance to fade over, and inheriting the world's fog would lay the
        // sky's colour over a ship being drawn a few inches from a virtual camera.
        final float fogStart = RenderSystem.getShaderFogStart();
        final float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        boolean drew = false;
        try {
            for (int layer = 0; layer < ShipSectionMesh.CHUNK_LAYERS.length; layer++) {
                final RenderType renderType = ShipSectionMesh.CHUNK_LAYERS[layer];
                if (opaqueOnly && renderType == RenderType.translucent()) {
                    continue;
                }
                final VertexBuffer buffer = mesh.getOpaque(layer);
                if (buffer == null) {
                    continue;
                }
                renderType.setupRenderState();
                if (fullBright) {
                    // Over the lightmap the render type just bound. The vertices carry the light
                    // of the shipyard, which is a sealed region a long way underground with no sky
                    // and no torches in it: rendered by it, every ship is a black shape.
                    RenderSystem.setShaderTexture(2, studioLight().getId());
                }
                final ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    renderType.clearRenderState();
                    continue;
                }
                if (shader.CHUNK_OFFSET != null) {
                    // The merged opaque buffers hold their vertices relative to the ship's own
                    // reference point, so one offset covers the whole ship.
                    shader.CHUNK_OFFSET.set(
                        (float) (mesh.refX - camX),
                        (float) (mesh.refY - camY),
                        (float) (mesh.refZ - camZ));
                }
                buffer.bind();
                buffer.drawWithShader(modelView, projection, shader);
                VertexBuffer.unbind();
                renderType.clearRenderState();
                drew = true;
            }
            drew |= renderBlockEntities(level, object, modelView, projection, camX, camY, camZ,
                fullBright);
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
        return drew;
    }

    private static MultiBufferSource.BufferSource buffers;

    /**
     * The ship's block entities, drawn after its blocks.
     *
     * <p>Without this a ship is only the part of itself that meshes: on a car, the instruments, the
     * head unit, the seat and the wheels are all block entity renderers, and a portrait of one came
     * out as a body shell with holes where everything worth looking at should have been.
     *
     * <p>Drawn through {@code BlockEntityRenderer} directly rather than through the dispatcher.
     * The dispatcher measures each block entity against the camera it was prepared with, which is
     * the one looking at the world; a ship's blocks in the shipyard are tens of millions of blocks
     * from that camera, so every one of them would be judged too far away to draw. Going straight
     * to the renderer also means the light value is ours to choose, which is what lets a portrait
     * be lit the same way its blocks are.
     */
    private static boolean renderBlockEntities(final ClientLevel level,
        final ShipRenderObject object, final Matrix4f modelView, final Matrix4f projection,
        final double camX, final double camY, final double camZ, final boolean fullBright) {

        final List<BlockEntity> blockEntities = object.getBlockEntities(level);
        if (blockEntities.isEmpty()) {
            return false;
        }
        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new BufferBuilder(2048));
        }

        final Matrix4f previousProjection = RenderSystem.getProjectionMatrix();
        final VertexSorting previousSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        // A block entity renderer writes vertices already transformed by the pose stack it is
        // given, and whatever is in RenderSystem's own model-view is then applied on top at draw
        // time. During a level pass that is the identity and the pose stack carries the camera --
        // but it is the world's to set, not ours, and anything that leaves a camera transform there
        // would be applied to a portrait that has a camera of its own, so the ship would swing
        // about with the viewer's head. It is pinned rather than assumed.
        final PoseStack systemModelView = RenderSystem.getModelViewStack();
        systemModelView.pushPose();
        systemModelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        final PoseStack poseStack = new PoseStack();
        poseStack.mulPoseMatrix(modelView);
        final float partialTick = Minecraft.getInstance().getFrameTime();
        boolean drew = false;
        try {
            for (int i = 0; i < blockEntities.size(); i++) {
                final BlockEntity blockEntity = blockEntities.get(i);
                final BlockEntityRenderer<BlockEntity> renderer =
                    Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(
                        blockEntity);
                if (renderer == null) {
                    continue;
                }
                final BlockPos pos = blockEntity.getBlockPos();
                final int light = fullBright ? LightTexture.FULL_BRIGHT
                    : LevelRenderer.getLightColor(level, pos);
                poseStack.pushPose();
                poseStack.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);
                renderer.render(blockEntity, partialTick, poseStack, buffers, light,
                    OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
                drew = true;
            }
        } finally {
            buffers.endBatch();
            systemModelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
        return drew;
    }

    private static DynamicTexture studioLight;

    /** A lightmap that is bright everywhere, so a portrait is lit wherever the ship is kept. */
    private static DynamicTexture studioLight() {
        if (studioLight == null) {
            final NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
            image.setPixelRGBA(0, 0, 0xFFFFFFFF);
            studioLight = new DynamicTexture(image);
            studioLight.upload();
        }
        return studioLight;
    }

    /**
     * Stops keeping a ship's geometry alive for a portrait.
     *
     * <p>Does not free it: the world may still be drawing the ship. It only means that when the
     * world stops, nothing here is holding on.
     */
    public static void forget(final long shipId) {
        ShipBatchRenderer.INSTANCE.forgetPortrait(shipId);
    }
}
