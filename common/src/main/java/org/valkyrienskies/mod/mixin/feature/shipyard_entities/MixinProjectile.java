package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends Entity implements TraceableEntity {

    @Shadow
    protected abstract void updateRotation();

    public MixinProjectile(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * If the projectile hit the ship, sends it to the shipyard.
     * This makes arrows/tridents/fishing hooks stuck on a ship render at correct position.
     */
    @Inject(
        method = "onHitBlock",
        at = @At("HEAD")
    )
    private void sendToShipyard(BlockHitResult blockHitResult, CallbackInfo ci) {
        Ship ship;
        if ((ship = VSGameUtilsKt.getShipManagingPos(level(), blockHitResult.getBlockPos())) != null) {
            Vector3d hitLocation = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(blockHitResult.location));
            blockHitResult.location = VectorConversionsMCKt.toMinecraft(hitLocation);
            DefaultShipyardEntityHandler.INSTANCE.moveEntityFromWorldToShipyard(this, ship);
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
        Iterable<VoxelShape> result = level().getBlockCollisions(this, this.getBoundingBox().inflate(0.1));
        if(!result.iterator().hasNext()) {
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(this, ship);
        }
    }
}
