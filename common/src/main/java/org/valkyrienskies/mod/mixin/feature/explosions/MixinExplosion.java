package org.valkyrienskies.mod.mixin.feature.explosions;

import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.GameTickForceApplier;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Explosion.class)
public abstract class MixinExplosion {

    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    @Mutable
    private double x;

    @Shadow
    @Final
    @Mutable
    private double y;
    @Shadow
    @Final
    @Mutable
    private double z;
    @Shadow
    @Final
    @Mutable
    private float radius;
    @Unique
    private boolean isModifyingExplosion = false;

    @Shadow
    public abstract void explode();

    @Inject(at = @At("TAIL"), method = "explode")
    private void afterExplode(final CallbackInfo ci) {
        if (isModifyingExplosion) {
            // Custom forces
            final Vector3d originPos = new Vector3d(this.x, this.y, this.z);
            final BlockPos explodePos = new BlockPos(originPos.x(), originPos.y(), originPos.z());
            final int radius = (int) Math.ceil(this.radius);
            for (int x = radius; x >= -radius; x--) {
                for (int y = radius; y >= -radius; y--) {
                    for (int z = radius; z >= -radius; z--) {
                        final BlockHitResult result = level.clip(
                            new ClipContext(Vec3.atCenterOf(explodePos),
                                Vec3.atCenterOf(explodePos.offset(x, y, z)),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE, null));
                        if (result.getType() == Type.BLOCK) {
                            final BlockPos blockPos = result.getBlockPos();
                            final ServerShip ship =
                                (ServerShip) VSGameUtilsKt.getShipObjectManagingPos(this.level, blockPos);
                            if (ship != null) {
                                final Vector3d forceVector =
                                    VectorConversionsMCKt.toJOML(
                                        Vec3.atCenterOf(explodePos)); //Start at center position
                                final Double distanceMult = Math.max(0.5, 1.0 - (this.radius /
                                    forceVector.distance(VectorConversionsMCKt.toJOML(Vec3.atCenterOf(blockPos)))));
                                final Double powerMult = Math.max(0.1, this.radius / 4); //TNT blast radius = 4

                                forceVector.sub(VectorConversionsMCKt.toJOML(
                                    Vec3.atCenterOf(blockPos))); //Subtract hit block pos to get direction
                                forceVector.normalize();
                                forceVector.mul(-1 *
                                    VSGameConfig.SERVER.getExplosionBlastForce()); //Multiply by blast force at center position. Negative because of how we got the direction.
                                forceVector.mul(distanceMult); //Multiply by distance falloff
                                forceVector.mul(powerMult); //Multiply by radius, roughly equivalent to power

                                final GameTickForceApplier forceApplier =
                                    ship.getAttachment(GameTickForceApplier.class);
                                final Vector3dc shipCoords = ship.getShipTransform().getShipPositionInShipCoordinates();
                                if (forceVector.isFinite()) {
                                    forceApplier.applyInvariantForceToPos(forceVector,
                                        VectorConversionsMCKt.toJOML(Vec3.atCenterOf(blockPos)).sub(shipCoords));
                                }
                            }
                        }
                    }
                }
            }
            return;
        }

        isModifyingExplosion = true;

        final double origX = this.x;
        final double origY = this.y;
        final double origZ = this.z;

        VSGameUtilsKt.transformToNearbyShipsAndWorld(this.level, this.x, this.y, this.z, this.radius, (x, y, z) -> {
            this.x = x;
            this.y = y;
            this.z = z;
            this.explode();
        });

        this.x = origX;
        this.y = origY;
        this.z = origZ;

        isModifyingExplosion = false;
    }

    @Inject(at = @At("TAIL"), method = "finalizeExplosion")
    private void afterFinalizeExplosion(final boolean spawnParticles, final CallbackInfo ci) {
        if (this.level.isClientSide) {
            return;
        }

    }

    // Don't raytrace the shipyard
    // getEntities already gives shipyard entities
    @Redirect(method = "explode",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"
        )
    )
    private List<Entity> noRayTrace(final Level instance, final Entity entity, final AABB aabb) {
        if (isModifyingExplosion) {
            return Collections.emptyList();
        } else {
            return instance.getEntities(entity, aabb);
        }
    }

}
