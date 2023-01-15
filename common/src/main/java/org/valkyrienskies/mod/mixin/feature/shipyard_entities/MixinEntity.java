package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract void setPosRaw(double d, double e, double f);

    @Shadow
    public abstract void positionRider(Entity entity);

    @Shadow
    public abstract void teleportTo(double d, double e, double f);

    @Shadow
    public abstract EntityType<?> getType();

    /**
     * @author ewoudje
     * @reason use vs2 handler to handle this method
     */
    @WrapOperation(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V"))
    private void positionRider(final Entity instance, final Entity passengerI, final Entity.MoveFunction callback,
        final Operation<Void> positionRider) {
        positionRider.call(instance, passengerI,
            (Entity.MoveFunction) (passenger, x, y, z) -> VSEntityManager.INSTANCE.getHandler(passenger)
                .positionSetFromVehicle(passenger, Entity.class.cast(this), x, y, z));
    }

    @Unique
    private boolean isModifyingTeleport = false;

    @Inject(
        at = @At("HEAD"),
        method = "teleportTo",
        cancellable = true
    )
    private void beforeTeleportTo(final double d, final double e, final double f, final CallbackInfo ci) {
        if (isModifyingTeleport) {
            return;
        }

        ci.cancel();
        isModifyingTeleport = true;
        final Vector3d pos = VSEntityManager.INSTANCE.getHandler(Entity.class.cast(this))
            .getTeleportPos(Entity.class.cast(this), new Vector3d(d, e, f));
        teleportTo(pos.x, pos.y, pos.z);
        isModifyingTeleport = false;
    }

    /**
     * Prevent [saveWithoutId] from saving the vehicle's position as passenger's position when that vehicle is in the
     * shipyard.
     * <p>
     * This fixes players falling through the world when they load into the world and were mounting an entity on a
     * ship.
     */
    @ModifyExpressionValue(method = "saveWithoutId", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;"))
    private Entity preventSavingVehiclePosAsOurPos(final Entity originalVehicle) {
        // Only check this if [originalVehicle] != null
        if (originalVehicle == null) {
            return null;
        }

        final int vehicleChunkX = ((int) originalVehicle.position().x()) >> 4;
        final int vehicleChunkZ = ((int) originalVehicle.position().z()) >> 4;

        // Don't store the vehicle's position if the vehicle is in the shipyard
        final boolean isVehicleInShipyard = VSGameUtilsKt.isChunkInShipyard(level, vehicleChunkX, vehicleChunkZ);
        if (isVehicleInShipyard) {
            return null;
        } else {
            return originalVehicle;
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void preSetRemoved() {
        final Entity thisAsEntity = Entity.class.cast(this);
        final Ship ship = VSGameUtilsKt.getShipManaging(thisAsEntity);
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(thisAsEntity).entityRemovedFromShipyard(thisAsEntity, ship);
        }
    }

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    public Level level;
}
