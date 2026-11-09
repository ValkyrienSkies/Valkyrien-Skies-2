package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.ShipDraggingMath;
import org.valkyrienskies.mod.common.util.EntityRenderPosition;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

import java.util.IdentityHashMap;
import java.util.Map;

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

    @Unique
    private final Map<Entity, EntityRenderPosition.Snapshot> vs$renderPositionSnapshots = new IdentityHashMap<>();

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
            entity.level(),
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
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    public HitResult modifyCrosshairTargetBlocks(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids, final Operation<HitResult> pick) {

        HitResult result = pick.call(receiver, maxDistance, tickDelta, includeFluids);
        HitResult noTransform;
        if(result instanceof BlockHitResult blockHitResult) {
            noTransform = new BlockHitResult(
                VSGameUtilsKt.toShipRenderCoordinates(Minecraft.getInstance().level, blockHitResult.getBlockPos().getCenter(), blockHitResult.location),
                blockHitResult.getDirection(),
                blockHitResult.getBlockPos(),
                blockHitResult.isInside()
            );
        } else noTransform = result;
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(noTransform);
        return result;
    }

    @WrapOperation(
        method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double correctDistanceChecks(final Vec3 instance, final Vec3 vec3, final Operation<Double> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.minecraft.level, instance, vec3, original);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Update ship render transforms
            final VsiClientShipWorld shipWorld =
                IShipObjectWorldClientProvider.class.cast(this.minecraft).getShipObjectWorld();
            if (shipWorld == null) {
                return;
            }

            final float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            shipWorld.updateRenderTransforms(partialTick);

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                if (!EntityDragger.isDraggable(entity)) {
                    continue;
                }
                // The position we want to render [entity] at for this frame
                // This is set when an entity is mounted to a ship, or an entity is being dragged by a ship
                Vector3dc entityShouldBeHere = null;

                // First, try getting the ship the entity is mounted to, if one exists
                final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTick);

                if (shipMountedToData != null) {
                    final ClientShip shipMountedTo = (ClientShip) shipMountedToData.getShipMountedTo();
                    // If the entity is mounted to a ship then update their position
                    final Vector3dc passengerPos = shipMountedToData.getMountPosInShip();
                    entityShouldBeHere = shipMountedTo.getRenderTransform().getShipToWorld()
                        .transformPosition(passengerPos, new Vector3d());
                    vs$moveEntityToRenderPosition(entity, entityShouldBeHere);
                    continue;
                }

                final EntityDraggingInformation entityDraggingInformation =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                final Long lastShipStoodOn = entityDraggingInformation.getLastShipStoodOn();
                // Then try getting [entityShouldBeHere] from [entityDraggingInformation]
//                if (!entityDraggingInformation.shouldUseClientPrediction(entity)) {
//                    continue;
//                }
                if (lastShipStoodOn != null && entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                    final ClientShip shipObject =
                        VSGameUtilsKt.getShipObjectWorld(clientWorld).getLoadedShips().getById(lastShipStoodOn);
                    if (shipObject != null) {
                        entityShouldBeHere = ShipDraggingMath.renderPosition(
                            new Vector3d(entity.xo, entity.yo, entity.zo),
                            new Vector3d(entity.getX(), entity.getY(), entity.getZ()),
                            shipObject.getPrevTickTransform().getWorldToShip(),
                            shipObject.getTransform().getWorldToShip(),
                            shipObject.getRenderTransform().getShipToWorld(), partialTick);
                    }
                }

                if (entityShouldBeHere != null) {
                    vs$moveEntityToRenderPosition(entity, entityShouldBeHere);
                }
            }
        }
    }

    @Unique
    private void vs$moveEntityToRenderPosition(final Entity entity, final Vector3dc renderPosition) {
        vs$renderPositionSnapshots.computeIfAbsent(entity, EntityRenderPosition::capture);
        EntityRenderPosition.setWithoutSectionUpdate(
            entity,
            renderPosition.x(),
            renderPosition.y(),
            renderPosition.z()
        );
        entity.xo = renderPosition.x();
        entity.yo = renderPosition.y();
        entity.zo = renderPosition.z();
        entity.xOld = renderPosition.x();
        entity.yOld = renderPosition.y();
        entity.zOld = renderPosition.z();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postRender(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            vs$renderPositionSnapshots.forEach((entity, snapshot) -> snapshot.restore(entity));
            vs$renderPositionSnapshots.clear();

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
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
        )
    )
    private void setupCameraWithMountedShip(LevelRenderer instance, Vec3 vec3, Matrix4f rotationMatrix, Matrix4f matrix4f2,
        Operation<Void> prepareCullFrustum, final DeltaTracker deltaTracker) {
        final Camera camera = this.mainCamera;

        if (camera == null) {
            prepareCullFrustum.call(instance, vec3, rotationMatrix, matrix4f2);
            return;
        }

        ((IVSCamera) camera).resetShipMountedRenderTransform();

        final ClientLevel clientLevel = minecraft.level;
        final Entity player = minecraft.player;
        if (clientLevel == null || player == null) {
            prepareCullFrustum.call(instance, vec3, rotationMatrix, matrix4f2);
            return;
        }

        final float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(player, partialTicks);
        if (shipMountedToData == null) {
            prepareCullFrustum.call(instance, vec3, rotationMatrix, matrix4f2);
            return;
        }

        final Entity playerVehicle = player.getVehicle();
        if (playerVehicle == null && !(player instanceof LivingEntity living && living.isSleeping())) {
            prepareCullFrustum.call(instance, vec3, rotationMatrix, matrix4f2);
            return;
        }

        final ClientShip clientShip = (ClientShip) shipMountedToData.getShipMountedTo();

        ((IVSCamera) camera).setupWithShipMounted(
            this.minecraft.level,
            this.minecraft.getCameraEntity() == null ? this.minecraft.player : this.minecraft.getCameraEntity(),
            !this.minecraft.options.getCameraType().isFirstPerson(),
            this.minecraft.options.getCameraType().isMirrored(),
            partialTicks,
            clientShip,
            shipMountedToData.getMountPosInShip()
        );

        // Set rotationMatrix to match the new camera rotation
        Quaternionf quaternionf = camera.rotation().conjugate(new Quaternionf());
        Matrix4f newRotationMatrix = (new Matrix4f()).rotation(quaternionf);
        rotationMatrix.set(newRotationMatrix);

        // Camera FOV changes based on the position of the camera, so recompute FOV to account for the change of camera
        // position.
        final double fov = this.getFov(camera, partialTicks, true);
        final Matrix4f projectionMatrixNew = this.getProjectionMatrix(Math.max(fov, (double) this.minecraft.options.fov().get()));
        prepareCullFrustum.call(instance, camera.getPosition(), rotationMatrix, projectionMatrixNew);
    }
    // endregion

    @ModifyReturnValue(method = "getDepthFar", at = @At("RETURN"))
    public float includeShipsIn(final float originalDepth) {
        float maxDistance = originalDepth;
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.getInstance()).getLoadedShips()) {
            Vec3 cameraPos = this.mainCamera.getPosition();
            AABBdc shipAABB = ship.getRenderAABB();
            // find the furthest distance from the camera to the ship AABB corners
            double furthestDistanceSq = 0;
            double dMinX = shipAABB.minX() - cameraPos.x();  
            double dMaxX = shipAABB.maxX() - cameraPos.x();  
            double dMinY = shipAABB.minY() - cameraPos.y();  
            double dMaxY = shipAABB.maxY() - cameraPos.y();  
            double dMinZ = shipAABB.minZ() - cameraPos.z();  
            double dMaxZ = shipAABB.maxZ() - cameraPos.z();  
            double furthestDist = Math.sqrt(Math.max(dMinX * dMinX, dMaxX * dMaxX) + Math.max(dMinY * dMinY, dMaxY * dMaxY) + Math.max(dMinZ * dMinZ, dMaxZ * dMaxZ));  
            maxDistance = Math.max(maxDistance, (float) furthestDist);  
        }

        return maxDistance;
    }
}
