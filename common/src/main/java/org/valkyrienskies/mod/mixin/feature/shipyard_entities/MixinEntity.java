package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract void teleportTo(double d, double e, double f);

    @Shadow
    public abstract EntityType<?> getType();

    /**
     * @author ewoudje
     * @reason use vs2 handler to handle this method
     */
    @Redirect(method = "positionRider(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V"))
    private void positionRider(final Entity instance, final Entity passengerI, final Entity.MoveFunction callback) {
        this.positionRider(passengerI,
            (passenger, x, y, z) -> VSEntityManager.INSTANCE.getHandler(passenger.getType())
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
        final Vector3d pos = VSEntityManager.INSTANCE.getHandler(this.getType())
            .getTeleportPos(Entity.class.cast(this), new Vector3d(d, e, f));
        teleportTo(pos.x, pos.y, pos.z);
        isModifyingTeleport = false;
    }

    @Unique
    private static Vector3dc tempVec = null;

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
    @Inject(method = "setPosRaw", at = @At(value = "HEAD"))
    private void handlePosSet(final double x, final double y, final double z, final CallbackInfo ci) {
        final Vector3d pos = new Vector3d(x, y, z);
        final Ship ship;

        if ((ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos)) != null) {
            tempVec = VSEntityManager.INSTANCE.getHandler(getType()).onEntityMove(Entity.class.cast(this), ship, pos);
        } else {
            tempVec = null;
        }
    }

    @ModifyVariable(method = "setPosRaw", at = @At(value = "HEAD"), ordinal = 0, argsOnly = true)
    private double setX(final double x) {
        if (tempVec != null) {
            return tempVec.x();
        } else {
            return x;
        }
    }

    @ModifyVariable(method = "setPosRaw", at = @At(value = "HEAD"), ordinal = 1, argsOnly = true)
    private double setY(final double y) {
        if (tempVec != null) {
            return tempVec.y();
        } else {
            return y;
        }
    }

    @ModifyVariable(method = "setPosRaw", at = @At(value = "HEAD"), ordinal = 2, argsOnly = true)
    private double setZ(final double z) {
        if (tempVec != null) {
            return tempVec.z();
        } else {
            return z;
        }
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

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    public Level level;
}
