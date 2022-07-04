package org.valkyrienskies.mod.mixin.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

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
    public HitResult modifyCrosshairTarget(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(original);

        return receiver.pick(maxDistance, tickDelta, includeFluids);
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
            final ShipObjectClientWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(clientWorld);

            for (final ShipObjectClient shipObjectClient : shipWorld.getShipObjects().values()) {
                shipObjectClient.updateRenderShipTransform(tickDelta);
            }

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.entitiesForRendering()) {
                final EntityDraggingInformation vsEntity =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                final Long lastShipStoodOn = vsEntity.getLastShipStoodOn();
                if (lastShipStoodOn != null) {
                    final ShipObjectClient shipObject =
                        VSGameUtilsKt.getShipObjectWorld(clientWorld).getShipObjects().get(lastShipStoodOn);
                    if (shipObject != null) {
                        vsEntity.setCachedLastPosition(new Vector3d(entity.xo, entity.yo, entity.zo));
                        vsEntity.setRestoreCachedLastPosition(true);

                        // The velocity added to the entity by ship dragging
                        final Vector3dc entityAddedVelocity = vsEntity.getAddedMovementLastTick();

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

                        // Move [entityShouldBeHerePreTransform] with the ship, using the prev transform and the current
                        // render transform
                        final Vector3dc entityShouldBeHere = shipObject.getRenderTransform().getShipToWorldMatrix()
                            .transformPosition(
                                shipObject.getShipData().getPrevTickShipTransform().getWorldToShipMatrix()
                                    .transformPosition(entityShouldBeHerePreTransform, new Vector3d()));

                        // Update the entity last tick positions such that the entity's render position will be
                        // interpolated to be [entityShouldBeHere]
                        entity.xo = (entityShouldBeHere.x() - (entity.getX() * tickDelta)) / (1 - tickDelta);
                        entity.yo = (entityShouldBeHere.y() - (entity.getY() * tickDelta)) / (1 - tickDelta);
                        entity.zo = (entityShouldBeHere.z() - (entity.getZ() * tickDelta)) / (1 - tickDelta);
                    }
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
}
