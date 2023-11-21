package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.apigame.world.ClientShipWorldCore;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;
    // region Mount the camera to the ship
    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    protected abstract double getFov(Camera camera, float f, boolean bl);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double d);

    /**
     * {@link Entity#pick(double, float, boolean)} except the hit pos is not transformed
     */
    @Unique
    private static HitResult entityRaycastNoTransform(
        final Entity entity, final double maxDistance, final float tickDelta, final boolean includeFluids) {
        final Vec3 vec3d = entity.getEyePosition(tickDelta);
        final Vec3 vec3d2 = entity.getViewVector(tickDelta);
        final Vec3 vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        return RaycastUtilsKt.clipIncludeShips(
            entity.level,
            new ClipContext(
                vec3d,
                vec3d3,
                ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
            ),
            false
        );
    }

    @WrapOperation(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    public HitResult modifyCrosshairTargetBlocks(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids, final Operation<HitResult> pick) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(original);

        return pick.call(receiver, maxDistance, tickDelta, includeFluids);
    }

    @WrapOperation(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double correctDistanceChecks(final Vec3 instance, final Vec3 vec, final Operation<Vec3> distanceToSqr) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.minecraft.level,
            vec.x, vec.y, vec.z,
            instance.x, instance.y, instance.z);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Update ship render transforms
            final ClientShipWorldCore shipWorld =
                IShipObjectWorldClientProvider.class.cast(this.minecraft).getShipObjectWorld();
            if (shipWorld == null) {
                return;
            }

            shipWorld.updateRenderTransforms(tickDelta);

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                // The position we want to render [entity] at for this frame
                // This is set when an entity is mounted to a ship, or an entity is being dragged by a ship
                Vector3dc entityShouldBeHere = null;

                // First, try getting [entityShouldBeHere] from [shipMountedTo]
                final ClientShip shipMountedTo =
                    VSGameUtilsKt.getShipObjectEntityMountedTo(clientWorld, entity);

                if (shipMountedTo != null) {
                    // If the entity is mounted to a ship then update their position
                    final Vector3dc passengerPos =
                        VSGameUtilsKt.getPassengerPos(entity.getVehicle(), entity.getMyRidingOffset(), tickDelta);
                    entityShouldBeHere = shipMountedTo.getRenderTransform().getShipToWorld()
                        .transformPosition(passengerPos, new Vector3d());
                    entity.setPos(entityShouldBeHere.x(), entityShouldBeHere.y(), entityShouldBeHere.z());
                    entity.xo = entityShouldBeHere.x();
                    entity.yo = entityShouldBeHere.y();
                    entity.zo = entityShouldBeHere.z();
                    entity.xOld = entityShouldBeHere.x();
                    entity.yOld = entityShouldBeHere.y();
                    entity.zOld = entityShouldBeHere.z();
                    continue;
                }

                final EntityDraggingInformation entityDraggingInformation =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                final Long lastShipStoodOn = entityDraggingInformation.getLastShipStoodOn();
                // Then try getting [entityShouldBeHere] from [entityDraggingInformation]
                if (lastShipStoodOn != null && entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                    final ClientShip shipObject =
                        VSGameUtilsKt.getShipObjectWorld(clientWorld).getLoadedShips().getById(lastShipStoodOn);
                    if (shipObject != null) {
                        entityDraggingInformation.setCachedLastPosition(
                            new Vector3d(entity.xo, entity.yo, entity.zo));
                        entityDraggingInformation.setRestoreCachedLastPosition(true);

                        // The velocity added to the entity by ship dragging
                        final Vector3dc entityAddedVelocity = entityDraggingInformation.getAddedMovementLastTick();

                        // The velocity of the entity before we added ship dragging
                        final double entityMovementX = entity.getX() - entityAddedVelocity.x() - entity.xo;
                        final double entityMovementY = entity.getY() - entityAddedVelocity.y() - entity.yo;
                        final double entityMovementZ = entity.getZ() - entityAddedVelocity.z() - entity.zo;

                        // Without ship dragging, the entity would've been here
                        final Vector3dc entityShouldBeHerePreTransform = new Vector3d(
                            entity.xo + entityMovementX * tickDelta,
                            entity.yo + entityMovementY * tickDelta,
                            entity.zo + entityMovementZ * tickDelta
                        );

                        // Move [entityShouldBeHerePreTransform] with the ship, using the prev transform and the
                        // current render transform
                        entityShouldBeHere = shipObject.getRenderTransform().getShipToWorldMatrix()
                            .transformPosition(
                                shipObject.getPrevTickShipTransform().getWorldToShipMatrix()
                                    .transformPosition(entityShouldBeHerePreTransform, new Vector3d()));
                    }
                }

                // Apply entityShouldBeHere, if its present
                //
                // Also, don't run this if [tickDelta] is too small, getting so close to dividing by 0 could mess
                // something up
                if (entityShouldBeHere != null && tickDelta < .99999) {
                    // Update the entity last tick positions such that the entity's render position will be
                    // interpolated to be [entityShouldBeHere]
                    entity.xo = (entityShouldBeHere.x() - (entity.getX() * tickDelta)) / (1.0 - tickDelta);
                    entity.yo = (entityShouldBeHere.y() - (entity.getY() * tickDelta)) / (1.0 - tickDelta);
                    entity.zo = (entityShouldBeHere.z() - (entity.getZ() * tickDelta)) / (1.0 - tickDelta);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Restore the entity last tick positions that were replaced during this frame
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                final EntityDraggingInformation vsEntity =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                if (vsEntity.getRestoreCachedLastPosition()) {
                    vsEntity.setRestoreCachedLastPosition(false);
                    final Vector3dc cachedLastPosition = vsEntity.getCachedLastPosition();
                    if (cachedLastPosition != null) {
                        entity.xo = cachedLastPosition.x();
                        entity.yo = cachedLastPosition.y();
                        entity.zo = cachedLastPosition.z();
                    } else {
                        System.err.println("How was cachedLastPosition was null?");
                    }
                }
            }
        }
    }

    /**
     * Mount the player's camera to the ship they are mounted on.
     */
    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lcom/mojang/math/Matrix4f;)V"
        )
    )
    private void setupCameraWithMountedShip(final LevelRenderer instance, final PoseStack ignore, final Vec3 vec3,
        final Matrix4f matrix4f, final Operation<Void> prepareCullFrustum, final float partialTicks,
        final long finishTimeNano, final PoseStack matrixStack) {

        final ClientLevel clientLevel = minecraft.level;
        final Entity player = minecraft.player;
        if (clientLevel == null || player == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }
        final ClientShip playerShipMountedTo =
            VSGameUtilsKt.getShipObjectEntityMountedTo(clientLevel, player);
        if (playerShipMountedTo == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }
        final Entity playerVehicle = player.getVehicle();
        if (playerVehicle == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        // Update [matrixStack] to mount the camera to the ship
        final Vector3dc inShipPos =
            VSGameUtilsKt.getPassengerPos(playerVehicle, player.getMyRidingOffset(), partialTicks);

        final Camera camera = this.mainCamera;
        if (camera == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        ((IVSCamera) camera).setupWithShipMounted(
            this.minecraft.level,
            this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity(),
            !this.minecraft.options.getCameraType().isFirstPerson(),
            this.minecraft.options.getCameraType().isMirrored(),
            partialTicks,
            playerShipMountedTo,
            inShipPos
        );

        // Apply the ship render transform to [matrixStack]
        final Quaternion invShipRenderRotation = VectorConversionsMCKt.toMinecraft(
            playerShipMountedTo.getRenderTransform().getShipToWorldRotation().conjugate(new Quaterniond()));
        matrixStack.mulPose(invShipRenderRotation);

        // We also need to recompute [inverseViewRotationMatrix] after updating [matrixStack]
        {
            final Matrix3f matrix3f = matrixStack.last().normal().copy();
            if (matrix3f.invert()) {
                RenderSystem.setInverseViewRotationMatrix(matrix3f);
            }
        }

        // Camera FOV changes based on the position of the camera, so recompute FOV to account for the change of camera
        // position.
        final double fov = this.getFov(camera, partialTicks, true);

        // Use [camera.getPosition()] instead of [vec3] because mounting the player to the ship has changed the camera
        // position.
        prepareCullFrustum.call(instance, matrixStack, camera.getPosition(),
            this.getProjectionMatrix(Math.max(fov, this.minecraft.options.fov)));
    }
    // endregion
}
