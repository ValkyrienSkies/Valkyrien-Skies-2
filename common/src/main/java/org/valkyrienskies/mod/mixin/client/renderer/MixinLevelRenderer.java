package org.valkyrienskies.mod.mixin.client.renderer;

import static org.valkyrienskies.mod.common.VSClientGameUtils.transformRenderWithShip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.api.render.ShipRenderView;
import org.valkyrienskies.mod.client.render.ShipRenderViews;
import org.valkyrienskies.mod.common.render.batched.SplitShipRenderer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.render.ShipRenderConfig;
import org.valkyrienskies.mod.common.render.batched.ShipBatchRenderer;
import org.valkyrienskies.mod.common.render.batched.ShipRenderFrustum;
import org.valkyrienskies.mod.common.render.light.VsDynamicLight;
import org.valkyrienskies.mod.mixinducks.client.render.NestedShipView;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
    @Shadow private Frustum cullingFrustum;
    @Shadow private @Nullable Frustum capturedFrustum;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void vs$releaseShipMeshes(final ClientLevel next, final CallbackInfo ci) {
        if (level != null && level != next) ShipBatchRenderer.INSTANCE.releaseLevel(level);
    }

    @WrapMethod(method = "renderLevel")
    private void vs$renderView(final PoseStack poses, final float partialTick, final long finishNanoTime,
        final boolean blockOutline, final Camera camera, final GameRenderer renderer, final LightTexture light,
        final Matrix4f projection, final Operation<Void> original) {
        final var outer = ShipRenderViews.getCurrent();
        Frustum frustum = capturedFrustum != null ? capturedFrustum : cullingFrustum;
        if (outer != null || frustum == null) {
            final Vec3 pos = camera.getPosition();
            frustum = ShipRenderFrustum.create(poses.last().pose(), projection, pos.x, pos.y, pos.z);
        }
        final ShipRenderView view = new ShipRenderView(level, camera, new Matrix4f(poses.last().pose()),
            new Matrix4f(projection), frustum, partialTick);
        final Runnable draw = () ->
            original.call(poses, partialTick, finishNanoTime, blockOutline, camera, renderer, light, projection);
        try {
            ShipRenderViews.render(view, () -> VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips(), () -> {
                if (outer != null && (Object) this instanceof NestedShipView nested) {
                    nested.vs$renderNestedShipView(draw);
                } else {
                    draw.run();
                }
            });
        } finally {
            if (outer != null) VsDynamicLight.prepareFrame(outer.getLevel());
        }
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDD)V"))
    private void vs$renderShipOverlays(final PoseStack poses, final float partialTick, final long finishNanoTime,
        final boolean blockOutline, final Camera camera, final GameRenderer renderer, final LightTexture light,
        final Matrix4f projection, final CallbackInfo ci) {
        ShipRenderViews.renderOverlays(poses);
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void vs$resetShipBackend(final CallbackInfo ci) {
        ShipRenderConfig.onRendererReload();
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vs$prepareShipShadows(final CallbackInfo ci) {
        VsDynamicLight.prepareFrame(this.level);
    }

    @Shadow
    private @Nullable ClientLevel level;

    @Unique private PoseStack matrixStack;
    @Unique private Vec3 camera;

    @Shadow
    private static void renderShape(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final VoxelShape voxelShape, final double d, final double e, final double f, final float red, final float green,
        final float blue, final float alpha) {
        throw new AssertionError();
    }

    /**
     * @reason This mixin forces the game to always render block damage.
     */
    @ModifyConstant(
        method = "renderLevel",
        constant = @Constant(
            doubleValue = 1024,
            ordinal = 0
        ))
    private double disableBlockDamageDistanceCheck(final double originalBlockDamageDistanceConstant) {
        return Double.MAX_VALUE;
    }

    /**
     * @reason mojank developers who wrote this don't quite understand what a matrixstack is apparently
     * @author Rubydesic
     */
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void preRenderHitOutline(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final Entity entity, final double camX, final double camY, final double camZ, final BlockPos blockPos,
        final BlockState blockState, final CallbackInfo ci) {
        ci.cancel();
        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, blockPos);
        if (ship != null) {
            if (!ShipRenderViews.shouldRenderShip(ship)) return;
            var pieces = VSGameUtilsKt.getAllShips(level).getSplitPieces(ship.getId());
            if (!pieces.isEmpty()) {
                SplitShipRenderer.drawOutline(pieces, blockState.getShape(level, blockPos, CollisionContext.of(entity)),
                    blockPos, matrixStack, vertexConsumer, camX, camY, camZ);
                return;
            }
            matrixStack.pushPose();
            transformRenderWithShip(ship.getRenderTransform(), matrixStack, blockPos, camX, camY, camZ);
            renderShape(matrixStack, vertexConsumer,
                blockState.getShape(this.level, blockPos, CollisionContext.of(entity)),
                0d, 0d, 0d, 0.0F, 0.0F, 0.0F, 0.4F);
            matrixStack.popPose();
        } else {
            // vanilla
            renderShape(matrixStack, vertexConsumer,
                blockState.getShape(this.level, blockPos, CollisionContext.of(entity)),
                (double) blockPos.getX() - camX,
                (double) blockPos.getY() - camY,
                (double) blockPos.getZ() - camZ,
                0.0F, 0.0F, 0.0F, 0.4F);
        }
    }


    /**
     * This mixin makes block damage render on ships.
     */
    /*
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderDispatcher blockRenderManager, final BlockState state,
        final BlockPos blockPos, final BlockAndTintGetter blockRenderWorld, final PoseStack matrix,
        final VertexConsumer vertexConsumer, final Operation<Void> renderBreakingTexture) {


        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.popPose();

            // Add the VS render transform
            matrixStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = methodCamera.getPosition();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            final Matrix3f newNormalMatrix = matrixStack.last().normal().copy();
            final Matrix4f newModelMatrix = matrixStack.last().pose().copy();

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final SheetedDecalTextureGenerator newVertexConsumer =
                new SheetedDecalTextureGenerator(((OverlayVertexConsumerAccessor) vertexConsumer).getDelegate(),
                    newModelMatrix, newNormalMatrix);

            // Finally, invoke the render damage function.
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix,
                newVertexConsumer);
        } else {
            // Vanilla behavior
            renderBreakingTexture.call(blockRenderManager, state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
    }

     */

    /**
     * If an entity, for example an arrow stuck on a ship, is attached outside the border of the ship's chunk claim,
     * it won't be rendered because the chunk isn't compiled.
     * This injector bypasses that if the entity is in shipyard next to the compiled chunk.
     */
    @WrapOperation(
        method = "renderLevel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isChunkCompiled(Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean isInShipyardBorder(LevelRenderer instance, BlockPos blockPos, Operation<Boolean> original){
        if(original.call(instance, blockPos)) return true;
        if(VSGameUtilsKt.isBlockInShipyard(level, blockPos)) {
            if (VSGameUtilsKt.getLoadedShipManagingPos(level, blockPos) != null) {
                return true;
            }
            BlockPos blockPos1 = blockPos.offset(-1, -1, -1);
            BlockPos blockPos2 = blockPos.offset(1, 1, 1);
            for(BlockPos neighbor : BlockPos.betweenClosed(blockPos1, blockPos2)) {
                if (original.call(instance, neighbor)) return true;
            }
        }
        return false;
    }
}
