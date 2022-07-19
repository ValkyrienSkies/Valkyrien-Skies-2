package org.valkyrienskies.mod.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.DynamicLighting;
import org.valkyrienskies.mod.mixin.accessors.client.render.BuiltChunkStorageAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.OverlayVertexConsumerAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.RenderChunkInfoAccessor;
import org.valkyrienskies.mod.mixin.client.particle.MixinParticle;

/**
 * This mixin allows {@link LevelRenderer} to render ship chunks.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Shadow
    @Final
    private ObjectList<LevelRenderer.RenderChunkInfo> renderChunks;
    @Shadow
    private ClientLevel level;
    @Shadow
    private ViewArea viewArea;

    @Shadow
    @Nullable
    protected abstract Particle addParticleInternal(ParticleOptions parameters, boolean alwaysSpawn,
        boolean canSpawnOnMinimal,
        double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Shadow
    private static void renderShape(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final VoxelShape voxelShape, final double d, final double e, final double f, final float red, final float green,
        final float blue, final float alpha) {
        throw new AssertionError();
    }

    @Inject(
        method = "setupRender",
        at = @At("HEAD")
    )
    private void onSetupRender(final Camera activeRenderInfo, final Frustum camera, final boolean debugCamera,
        final int frameCount, final boolean playerSpectator, final CallbackInfo ci) {

        if (VSConfig.getEnableDynamicLights()) {
            DynamicLighting.updateChunkLighting(level, activeRenderInfo, camera);
        }
    }

    /**
     * Includes nearby ships into the check for nearby chunks so that those nearby renders are prioritized and you don't
     * see ships without rendered blocks right next to you
     */
    @Redirect(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
        )
    )
    private double includeShipChunksInNearChunks(final BlockPos b, final Vec3i v) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(
            level, b.getX(), b.getY(), b.getZ(), v.getX(), v.getY(), v.getZ());
    }

    @Inject(
        method = {
            "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"},
        at = {@At("TAIL")},
        cancellable = true
    )
    private static void onGetLightmapCoordinates(final BlockAndTintGetter world, final BlockState state,
        final BlockPos pos,
        final CallbackInfoReturnable<Integer> cir) {

        if (VSConfig.getEnableDynamicLights()) {
            cir.setReturnValue(DynamicLighting.getLightColor(world, state, pos, cir.getReturnValue()));
        }

    }

    /**
     * Render particles in-world. The {@link MixinParticle} is not sufficient because this method includes a distance
     * check, but this mixin is also not sufficient because not every particle is spawned using this method.
     */
    @Inject(
        method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spawnParticleInWorld(final ParticleOptions parameters, final boolean alwaysSpawn,
        final boolean canSpawnOnMinimal,
        final double x, final double y, final double z, final double velocityX, final double velocityY,
        final double velocityZ,
        final CallbackInfoReturnable<Particle> cir
    ) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, (int) x >> 4, (int) z >> 4);

        if (ship == null) {
            // vanilla behaviour
            return;
        }

        final Matrix4dc transform = ship.getRenderTransform().getShipToWorldMatrix();
        // in-world position
        final Vector3d p = transform.transformPosition(new Vector3d(x, y, z));

        // in-world velocity
        final Vector3d v = transform
            // Rotate velocity wrt ship transform
            .transformDirection(new Vector3d(velocityX, velocityY, velocityZ))
            // Tack on the ships linear velocity (no angular velocity param unfortunately)
            .add(ship.getShipData().getPhysicsData().getLinearVelocity());

        // Return and re-call this method with new coords
        cir.setReturnValue(
            addParticleInternal(parameters, alwaysSpawn, canSpawnOnMinimal, p.x, p.y, p.z, v.x, v.y, v.z));
        cir.cancel();
    }

    /**
     * This mixin tells the {@link LevelRenderer} to render ship chunks.
     */
    @Inject(
        method = "setupRender",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
        )
    )
    private void addShipVisibleChunks(
        final Camera camera, final Frustum frustum, final boolean hasForcedFrustum, final int frame,
        final boolean spectator, final CallbackInfo ci) {

        final LevelRenderer self = LevelRenderer.class.cast(this);
        final BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();
        final BuiltChunkStorageAccessor chunkStorageAccessor = (BuiltChunkStorageAccessor) viewArea;
        for (final ShipObjectClient shipObject : VSGameUtilsKt.getShipObjectWorld(level).getShipObjects().values()) {
            // Don't bother rendering the ship if its AABB isn't visible to the frustum
            if (!frustum.isVisible(VectorConversionsMCKt.toMinecraft(shipObject.getRenderAABB()))) {
                continue;
            }

            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkRenderDispatcher.RenderChunk renderChunk =
                        chunkStorageAccessor.callGetRenderChunkAt(tempPos);
                    if (renderChunk != null) {
                        final LevelRenderer.RenderChunkInfo newChunkInfo =
                            RenderChunkInfoAccessor.vs$new(self, renderChunk, null, 0);
                        renderChunks.add(newChunkInfo);
                    }
                }
                return null;
            });
        }
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
     * mojank developers who wrote this don't quite understand what a matrixstack is apparently
     *
     * @author Rubydesic
     */
    @Overwrite
    private void renderHitOutline(final PoseStack matrixStack, final VertexConsumer vertexConsumer,
        final Entity entity, final double camX, final double camY, final double camZ, final BlockPos blockPos,
        final BlockState blockState) {

        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
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
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBreakingTexture(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderDispatcher blockRenderManager, final BlockState state,
        final BlockPos blockPos,
        final BlockAndTintGetter blockRenderWorld, final PoseStack matrix, final VertexConsumer vertexConsumer,
        final PoseStack matrixStack, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightTexture methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {

        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(level, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.popPose();

            // Add the VS render transform
            matrixStack.pushPose();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3 cameraPos = methodCamera.getPosition();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final OverlayVertexConsumerAccessor vertexConsumerAccessor = (OverlayVertexConsumerAccessor) vertexConsumer;

            final Matrix3f newNormalMatrix = matrixStack.last().normal().copy();
            newNormalMatrix.invert();

            final Matrix4f newModelMatrix = matrixStack.last().pose().copy();
            // newModelMatrix.invert(); // DISABLED because Matrix4f.invert() doesn't work! Mojang code SMH >.<
            final Matrix4d newModelMatrixAsJoml = VectorConversionsMCKt.toJOML(newModelMatrix);
            newModelMatrixAsJoml.invert();
            VectorConversionsMCKt.set(newModelMatrix, newModelMatrixAsJoml);

            vertexConsumerAccessor.setNormalMatrix(newNormalMatrix);
            vertexConsumerAccessor.setTextureMatrix(newModelMatrix);

            // Finally, invoke the render damage function.
            blockRenderManager.renderBreakingTexture(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        } else {
            // Vanilla behavior
            blockRenderManager.renderBreakingTexture(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
    }

    /**
     * This mixin tells the game where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;bind()V"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    @SuppressWarnings("InvalidInjectorMethodSignature")
    private void renderShipChunk(final RenderType renderLayer, final PoseStack matrixStack,
        final double playerCameraX,
        final double playerCameraY, final double playerCameraZ, final CallbackInfo ci,
        final boolean bl, final ObjectListIterator<?> objectListIterator,
        final LevelRenderer.RenderChunkInfo chunkInfo2,
        final ChunkRenderDispatcher.RenderChunk builtChunk, final VertexBuffer vertexBuffer) {

        final int playerChunkX = ((int) playerCameraX) >> 4;
        final int playerChunkZ = ((int) playerCameraZ) >> 4;
        // Don't apply the ship render transform if the player is in the shipyard
        final boolean isPlayerInShipyard = ChunkAllocator.isChunkInShipyard(playerChunkX, playerChunkZ);

        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, renderChunkOrigin);
        if (!isPlayerInShipyard && shipObject != null) {
            // matrixStack.pop(); so while checking for bugs this seems unusual?
            // matrixStack.push(); but it doesn't fix sadly the bug im searching for
            transformRenderWithShip(shipObject.getRenderTransform(), matrixStack, renderChunkOrigin,
                playerCameraX, playerCameraY, playerCameraZ);
        } else {
            // Restore MC default behavior (that was removed by cancelDefaultTransform())
            matrixStack.translate(renderChunkOrigin.getX() - playerCameraX, renderChunkOrigin.getY() - playerCameraY,
                renderChunkOrigin.getZ() - playerCameraZ);
        }
    }

    /**
     * This mixin removes the vanilla code that determines where each chunk renders.
     */
    @Redirect(method = "renderChunkLayer",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private void cancelDefaultTransform(final PoseStack matrixStack, final double x, final double y, final double z) {
        // Do nothing
    }

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        final BlockEntity blockEntity, final float tickDelta, final PoseStack matrix,
        final MultiBufferSource vertexConsumerProvider,
        final PoseStack methodMatrices, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightTexture methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {

        final BlockPos blockEntityPos = blockEntity.getBlockPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(level, blockEntityPos);
        if (shipObject != null) {
            final Vec3 cam = methodCamera.getPosition();
            matrix.popPose();
            matrix.pushPose();
            transformRenderWithShip(shipObject.getRenderTransform(), matrix, blockEntityPos,
                cam.x(), cam.y(), cam.z());
        }
        blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrix, vertexConsumerProvider);
    }

    /**
     * @param renderTransform The ship's render transform
     * @param matrix          The {@link PoseStack} we are multiplying
     * @param blockPos        The position of the block in question
     * @param camX            Player camera X
     * @param camY            Player camera Y
     * @param camZ            Player camera Z
     */
    @Unique
    private void transformRenderWithShip(final ShipTransform renderTransform, final PoseStack matrix,
        final BlockPos blockPos,
        final double camX, final double camY, final double camZ) {

        final Matrix4dc shipToWorldMatrix = renderTransform.getShipToWorldMatrix();

        // Create the render matrix from the render transform and player position
        final Matrix4d renderMatrix = new Matrix4d();
        renderMatrix.translate(-camX, -camY, -camZ);
        renderMatrix.mul(shipToWorldMatrix);
        renderMatrix.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());

        // Apply the render matrix to the
        VectorConversionsMCKt
            .multiply(matrix, renderMatrix, renderTransform.getShipCoordinatesToWorldCoordinatesRotation());
    }

}
