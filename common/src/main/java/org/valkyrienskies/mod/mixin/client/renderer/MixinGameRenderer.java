package org.valkyrienskies.mod.mixin.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import java.util.function.Predicate;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.game.ships.ShipObjectClient;
import org.valkyrienskies.core.game.ships.ShipObjectClientWorld;
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

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    public HitResult modifyCrosshairTargetBlocks(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(original);

        return receiver.pick(maxDistance, tickDelta, includeFluids);
    }

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
        )
    )
    public @Nullable EntityHitResult modifyCrosshairTargetEntities(
        final Entity shooter,
        final Vec3 startVec, final Vec3 endVec,
        final AABB boundingBox, final Predicate<Entity> filter,
        final double distance) {
        return RaycastUtilsKt.raytraceEntities(shooter.level, shooter, startVec, endVec, boundingBox, filter, distance);
    }

    @Redirect(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double correctDistanceChecks(final Vec3 instance, final Vec3 vec) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.minecraft.level,
            vec.x, vec.y, vec.z,
            instance.x, instance.y, instance.z);
    }

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
            (ClientLevel) entity.level,
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

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.level;
        if (clientWorld != null) {
            // Update ship render transforms
            final ShipObjectClientWorld shipWorld =
                IShipObjectWorldClientProvider.class.cast(this.minecraft).getShipObjectWorld();
            if (shipWorld == null) {
                return;
            }

            for (final ShipObjectClient shipObjectClient : shipWorld.getShipObjects().values()) {
                shipObjectClient.updateRenderShipTransform(tickDelta);
            }

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                // The position we want to render [entity] at for this frame
                // This is set when an entity is mounted to a ship, or an entity is being dragged by a ship
                Vector3dc entityShouldBeHere = null;

                // First, try getting [entityShouldBeHere] from [shipMountedTo]
                final ShipObjectClient shipMountedTo =
                    VSGameUtilsKt.getShipObjectEntityMountedTo(clientWorld, entity);

                if (shipMountedTo != null) {
                    entityShouldBeHere = shipMountedTo.getRenderTransform().getShipToWorldMatrix()
                        .transformPosition(VSGameUtilsKt.getPassengerPos(entity.getVehicle(), tickDelta),
                            new Vector3d());
                }

                if (entityShouldBeHere == null) {
                    final EntityDraggingInformation entityDraggingInformation =
                        ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                    final Long lastShipStoodOn = entityDraggingInformation.getLastShipStoodOn();
                    // Then try getting [entityShouldBeHere] from [entityDraggingInformation]
                    if (lastShipStoodOn != null && entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                        final ShipObjectClient shipObject =
                            VSGameUtilsKt.getShipObjectWorld(clientWorld).getShipObjects().get(lastShipStoodOn);
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
                                    shipObject.getShipData().getPrevTickShipTransform().getWorldToShipMatrix()
                                        .transformPosition(entityShouldBeHerePreTransform, new Vector3d()));
                        }
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

    // region Mount the camera to the ship
    @Shadow
    @Final
    private LightTexture lightTexture;
    @Shadow
    @Final
    private Camera mainCamera;
    @Shadow
    private float renderDistance;
    @Shadow
    private int tick;
    @Shadow
    private boolean renderHand;

    @Shadow
    public abstract void pick(float partialTicks);

    @Shadow
    protected abstract boolean shouldRenderBlockOutline();

    @Shadow
    public abstract Matrix4f getProjectionMatrix(Camera camera, float f, boolean bl);

    @Shadow
    protected abstract void bobHurt(PoseStack matrixStack, float partialTicks);

    @Shadow
    protected abstract void bobView(PoseStack matrixStack, float partialTicks);

    @Shadow
    public abstract void resetProjectionMatrix(Matrix4f matrix);

    @Shadow
    protected abstract void renderItemInHand(PoseStack matrixStack, Camera activeRenderInfo, float partialTicks);

    // FIXME i think this replaces too much, this might make allot of mod compat issues
    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void preRenderLevel(final float partialTicks, final long finishTimeNano, final PoseStack matrixStack,
        final CallbackInfo ci) {
        final ClientLevel clientLevel = minecraft.level;
        final Entity player = minecraft.player;
        if (clientLevel == null || player == null) {
            return;
        }
        final ShipObjectClient playerShipMountedTo =
            VSGameUtilsKt.getShipObjectEntityMountedTo(clientLevel, player);
        if (playerShipMountedTo == null) {
            return;
        }

        // Replace the original logic to mount the player camera to the ship
        ci.cancel();
        final Vector3dc inShipPos = VSGameUtilsKt.getPassengerPos(player.getVehicle(), partialTicks);

        this.lightTexture.updateLightTexture(partialTicks);
        if (this.minecraft.getCameraEntity() == null) {
            this.minecraft.setCameraEntity(this.minecraft.player);
        }

        this.pick(partialTicks);
        this.minecraft.getProfiler().push("center");
        final boolean bl = this.shouldRenderBlockOutline();
        this.minecraft.getProfiler().popPush("camera");
        final Camera camera = this.mainCamera;
        this.renderDistance = (float) (this.minecraft.options.renderDistance * 16);
        final PoseStack poseStack = new PoseStack();
        poseStack.last().pose().multiply(this.getProjectionMatrix(camera, partialTicks, true));
        this.bobHurt(poseStack, partialTicks);
        if (this.minecraft.options.bobView) {
            this.bobView(poseStack, partialTicks);
        }

        final float f = Mth.lerp(partialTicks, this.minecraft.player.oPortalTime, this.minecraft.player.portalTime)
            * this.minecraft.options.screenEffectScale
            * this.minecraft.options.screenEffectScale;
        if (f > 0.0F) {
            final int i = this.minecraft.player.hasEffect(MobEffects.CONFUSION) ? 7 : 20;
            float g = 5.0F / (f * f + 5.0F) - f * 0.04F;
            g *= g;
            final Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            poseStack.mulPose(vector3f.rotationDegrees(((float) this.tick + partialTicks) * (float) i));
            poseStack.scale(1.0F / g, 1.0F, 1.0F);
            final float h = -((float) this.tick + partialTicks) * (float) i;
            poseStack.mulPose(vector3f.rotationDegrees(h));
        }

        final Matrix4f matrix4f = poseStack.last().pose();
        this.resetProjectionMatrix(matrix4f);
        ((IVSCamera) camera).setupWithShipMounted(
            this.minecraft.level,
            this.minecraft.getCameraEntity() == null ? this.minecraft.player :
                this.minecraft.getCameraEntity(),
            !this.minecraft.options.getCameraType().isFirstPerson(),
            this.minecraft.options.getCameraType().isMirrored(),
            partialTicks,
            playerShipMountedTo,
            inShipPos
        );
        final Quaternion invShipRenderRotation = VectorConversionsMCKt.toMinecraft(
            playerShipMountedTo.getRenderTransform().getShipCoordinatesToWorldCoordinatesRotation()
                .conjugate(new Quaterniond()));
        matrixStack.mulPose(Vector3f.XP.rotationDegrees(camera.getXRot()));
        matrixStack.mulPose(Vector3f.YP.rotationDegrees(camera.getYRot() + 180.0F));
        matrixStack.mulPose(invShipRenderRotation);
        this.minecraft.levelRenderer.renderLevel(matrixStack, partialTicks, finishTimeNano, bl, camera,
            GameRenderer.class.cast(this), this.lightTexture, matrix4f);
        this.minecraft.getProfiler().popPush("hand");
        if (this.renderHand) {
            RenderSystem.clear(256, Minecraft.ON_OSX);
            this.renderItemInHand(matrixStack, camera, partialTicks);
        }

        this.minecraft.getProfiler().pop();
    }
    // endregion
}
