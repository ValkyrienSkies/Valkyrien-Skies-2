package org.valkyrienskies.mod.mixin.client.render;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
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
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixin.accessors.client.render.BuiltChunkStorageAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.OverlayVertexConsumerAccessor;
import org.valkyrienskies.mod.mixin.accessors.client.render.WorldRendererChunkInfoAccessor;

/**
 * This mixin allows {@link WorldRenderer} to render ship chunks.
 */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {

    @Shadow
    @Final
    private ObjectList<WorldRenderer.ChunkInfo> visibleChunks;
    @Shadow
    private ClientWorld world;
    @Shadow
    private BuiltChunkStorage chunks;
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private BufferBuilderStorage bufferBuilders;

    @Shadow
    @Nullable
    protected abstract Particle spawnParticle(ParticleEffect parameters, boolean alwaysSpawn, boolean canSpawnOnMinimal,
        double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    @Shadow
    private static void drawShapeOutline(final MatrixStack matrixStack, final VertexConsumer vertexConsumer,
        final VoxelShape voxelShape, final double d, final double e, final double f, final float red, final float green,
        final float blue, final float alpha) {
        throw new AssertionError();
    }

    /**
     * Render particles in-world. The {@link org.valkyrienskies.mod.mixin.client.particle.ParticleMixin} is not
     * sufficient because this method includes a distance check, but this mixin is also not sufficient because not every
     * particle is spawned using this method.
     */
    @Inject(
        method = "spawnParticle(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spawnParticleInWorld(final ParticleEffect parameters, final boolean alwaysSpawn,
        final boolean canSpawnOnMinimal,
        final double x, final double y, final double z, final double velocityX, final double velocityY,
        final double velocityZ,
        final CallbackInfoReturnable<Particle> cir
    ) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(world, (int) x >> 4, (int) z >> 4);

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
        cir.setReturnValue(spawnParticle(parameters, alwaysSpawn, canSpawnOnMinimal, p.x, p.y, p.z, v.x, v.y, v.z));
        cir.cancel();
    }

    /**
     * This mixin tells the {@link WorldRenderer} to render ship chunks.
     */
    @Inject(
        method = "setupTerrain",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectList;iterator()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;"
        )
    )
    private void addShipVisibleChunks(
        final Camera camera, final Frustum frustum, final boolean hasForcedFrustum, final int frame,
        final boolean spectator, final CallbackInfo ci) {
        final WorldRenderer self = WorldRenderer.class.cast(this);
        final BlockPos.Mutable tempPos = new BlockPos.Mutable();
        final BuiltChunkStorageAccessor chunkStorageAccessor = (BuiltChunkStorageAccessor) chunks;
        for (final ShipObjectClient shipObject : VSGameUtilsKt.getShipObjectWorld(world).getShipObjects().values()) {
            shipObject.getShipData().getShipActiveChunksSet().iterateChunkPos((x, z) -> {
                for (int y = 0; y < 16; y++) {
                    tempPos.set(x << 4, y << 4, z << 4);
                    final ChunkBuilder.BuiltChunk renderChunk = chunkStorageAccessor.callGetRenderedChunk(tempPos);
                    if (renderChunk != null) {
                        final WorldRenderer.ChunkInfo newChunkInfo =
                            WorldRendererChunkInfoAccessor.vs$new(self, renderChunk, null, 0);
                        visibleChunks.add(newChunkInfo);
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
        method = "render",
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
    private void drawBlockOutline(final MatrixStack matrixStack, final VertexConsumer vertexConsumer,
        final Entity entity, final double camX, final double camY, final double camZ, final BlockPos blockPos,
        final BlockState blockState) {

        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(world, blockPos);
        if (ship != null) {
            matrixStack.push();
            transformRenderWithShip(ship.getRenderTransform(), matrixStack, blockPos, camX, camY, camZ);
            drawShapeOutline(matrixStack, vertexConsumer,
                blockState.getOutlineShape(this.world, blockPos, ShapeContext.of(entity)),
                0d, 0d, 0d, 0.0F, 0.0F, 0.0F, 0.4F);
            matrixStack.pop();
        } else {
            // vanilla
            drawShapeOutline(matrixStack, vertexConsumer,
                blockState.getOutlineShape(this.world, blockPos, ShapeContext.of(entity)),
                (double) blockPos.getX() - camX,
                (double) blockPos.getY() - camY,
                (double) blockPos.getZ() - camZ,
                0.0F, 0.0F, 0.0F, 0.4F);
        }

    }

    /**
     * This mixin makes block damage render on ships.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderDamage(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"))
    private void renderBlockDamage(final BlockRenderManager blockRenderManager, final BlockState state,
        final BlockPos blockPos,
        final BlockRenderView blockRenderWorld, final MatrixStack matrix, final VertexConsumer vertexConsumer,
        final MatrixStack matrixStack, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightmapTextureManager methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {
        final ShipObjectClient ship = VSGameUtilsKt.getShipObjectManagingPos(world, blockPos);
        if (ship != null) {
            // Remove the vanilla render transform
            matrixStack.pop();

            // Add the VS render transform
            matrixStack.push();

            final ShipTransform renderTransform = ship.getRenderTransform();
            final Vec3d cameraPos = methodCamera.getPos();

            transformRenderWithShip(renderTransform, matrixStack, blockPos, cameraPos.x, cameraPos.y, cameraPos.z);

            // Then update the matrices in vertexConsumer (I'm guessing vertexConsumer is responsible for mapping
            // textures, so we need to update its matrices otherwise the block damage texture looks wrong)
            final OverlayVertexConsumerAccessor vertexConsumerAccessor = (OverlayVertexConsumerAccessor) vertexConsumer;

            final Matrix3f newNormalMatrix = matrixStack.peek().getNormal().copy();
            newNormalMatrix.invert();

            final Matrix4f newModelMatrix = matrixStack.peek().getModel().copy();
            // newModelMatrix.invert(); // DISABLED because Matrix4f.invert() doesn't work! Mojang code SMH >.<
            final Matrix4d newModelMatrixAsJoml = VectorConversionsMCKt.toJOML(newModelMatrix);
            newModelMatrixAsJoml.invert();
            VectorConversionsMCKt.set(newModelMatrix, newModelMatrixAsJoml);

            vertexConsumerAccessor.setNormalMatrix(newNormalMatrix);
            vertexConsumerAccessor.setTextureMatrix(newModelMatrix);

            // Finally, invoke the render damage function.
            blockRenderManager.renderDamage(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        } else {
            // Vanilla behavior
            blockRenderManager.renderDamage(state, blockPos, blockRenderWorld, matrix, vertexConsumer);
        }
    }

    /**
     * This mixin tells the game where to render chunks; which allows us to render ship chunks in arbitrary positions.
     */
    @Inject(method = "renderLayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/VertexBuffer;bind()V"),
        locals = LocalCapture.CAPTURE_FAILHARD)
    private void renderShipChunk(final RenderLayer renderLayer, final MatrixStack matrixStack,
        final double playerCameraX,
        final double playerCameraY, final double playerCameraZ, final CallbackInfo ci,
        final boolean bl, final ObjectListIterator<?> objectListIterator, final WorldRenderer.ChunkInfo chunkInfo2,
        final ChunkBuilder.BuiltChunk builtChunk, final VertexBuffer vertexBuffer) {
        final BlockPos renderChunkOrigin = builtChunk.getOrigin();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(world, renderChunkOrigin);
        if (shipObject != null) {
            matrixStack.pop();
            matrixStack.push();
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
    @Redirect(method = "renderLayer",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V"))
    private void cancelDefaultTransform(final MatrixStack matrixStack, final double x, final double y, final double z) {
        // Do nothing
    }

    /**
     * This mixin makes {@link BlockEntity} in the ship render in the correct place.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderDispatcher;render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V"))
    private void renderShipChunkBlockEntity(final BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        final BlockEntity blockEntity, final float tickDelta, final MatrixStack matrix,
        final VertexConsumerProvider vertexConsumerProvider,
        final MatrixStack methodMatrices, final float methodTickDelta, final long methodLimitTime,
        final boolean methodRenderBlockOutline,
        final Camera methodCamera, final GameRenderer methodGameRenderer,
        final LightmapTextureManager methodLightmapTextureManager,
        final Matrix4f methodMatrix4f) {
        final BlockPos blockEntityPos = blockEntity.getPos();
        final ShipObjectClient shipObject = VSGameUtilsKt.getShipObjectManagingPos(world, blockEntityPos);
        if (shipObject != null) {
            final Vec3d cam = methodCamera.getPos();
            matrix.pop();
            matrix.push();
            transformRenderWithShip(shipObject.getRenderTransform(), matrix, blockEntityPos,
                cam.getX(), cam.getY(), cam.getZ());
        }
        blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrix, vertexConsumerProvider);
    }

    /**
     * @param renderTransform The ship's render transform
     * @param matrix          The {@link MatrixStack} we are multiplying
     * @param blockPos        The position of the block in question
     * @param camX            Player camera X
     * @param camY            Player camera Y
     * @param camZ            Player camera Z
     */
    @Unique
    private void transformRenderWithShip(final ShipTransform renderTransform, final MatrixStack matrix,
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
