package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends Entity implements TraceableEntity {

    @Shadow
    protected abstract boolean canHitEntity(Entity entity);

    public MixinProjectile(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * If the projectile hit the ship, sends it to the shipyard.
     * This makes arrows/tridents/fishing hooks stuck on a ship render at correct position.
     */
    @Inject(
        method = "onHit",
        at = @At("HEAD")
    )
    private void sendToShipyard(HitResult hitResult, CallbackInfo ci) {
        Ship shipHit;
        if (hitResult instanceof BlockHitResult blockHitResult && (shipHit = VSGameUtilsKt.getShipManagingPos(level(), blockHitResult.getBlockPos())) != null) {
            Vector3d shipyardPos = shipHit.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(this.position()));
            Vector3d relativePos = VectorConversionsMCKt.toJOML(this.position()).sub(shipHit.getTransform().getPositionInWorld());
            Vector3d shipPosVelocity = new Vector3d(shipHit.getVelocity())
                .add(new Vector3d(shipHit.getAngularVelocity()).cross(relativePos))
                .mul(0.05);
            Vector3d relativeDeltaOnShip = VectorConversionsMCKt.toJOML(this.getDeltaMovement()).sub(shipPosVelocity);
            shipHit.getWorldToShip().transformDirection(relativeDeltaOnShip);
            this.setPos(shipyardPos.x, shipyardPos.y, shipyardPos.z);
            this.setDeltaMovement(VectorConversionsMCKt.toMinecraft(relativeDeltaOnShip));
            Vector3d hitLocation = shipHit.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(blockHitResult.location));
            blockHitResult.location = VectorConversionsMCKt.toMinecraft(hitLocation);
            this.xo = shipyardPos.x - relativeDeltaOnShip.x;
            this.yo = shipyardPos.y - relativeDeltaOnShip.y;
            this.zo = shipyardPos.z - relativeDeltaOnShip.z;
            if((Object)this instanceof AbstractHurtingProjectile ahp) {
                Vector3d power = new Vector3d(ahp.xPower, ahp.yPower, ahp.zPower);
                shipHit.getTransform().getWorldToShip().transformDirection(power);
                ahp.xPower = power.x;
                ahp.yPower = power.y;
                ahp.zPower = power.z;
                ProjectileUtil.rotateTowardsMovement(ahp, 1.0f);
            }
        }
    }

    /**
     * If the projectile is no longer touching any block on shipyard, return it to the world.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void returnFromShipyard(CallbackInfo ci) {
        final Ship ship;
        if((ship = VSGameUtilsKt.getShipManaging(this)) == null) return;
        HitResult result = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if(result.getType() == Type.MISS) {
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(this, ship);
        }
    }
}
