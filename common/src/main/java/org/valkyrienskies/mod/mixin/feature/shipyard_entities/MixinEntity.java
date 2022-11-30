package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.core.game.ChunkAllocator;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;

@Mixin(Entity.class)
public abstract class MixinEntity {

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

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
    /*
    @Redirect(method = "setPos", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V"))
    public void setPosHandling1(final Entity instance, final double x, final double y, final double z) {
        final Vector3d pos = new Vector3d(x, y, z);
        final Ship ship;

        if ((ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos)) != null) {
            VSEntityManager.INSTANCE.getHandler(instance.getType()).onEntityMove(instance, ship, pos);
        } else {
            instance.setPosRaw(x, y, z);
        }
    }
     */

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
    @Redirect(method = "setLocationFromBoundingbox", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V"))
    private void setPosHandling2(final Entity instance, final double x, final double y, final double z) {
        final Vector3d pos = new Vector3d(x, y, z);
        final Ship ship;

        if ((ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos)) != null) {
            VSEntityManager.INSTANCE.getHandler(instance.getType()).onEntityMove(instance, ship, pos);
        } else {
            instance.setPosRaw(x, y, z);
        }
    }

    /**
     * Prevent [saveWithoutId] from saving the vehicle's position as passenger's position when that vehicle is in the
     * shipyard.
     *
     * This fixes players falling through the world when they load into the world and were mounting an entity on a ship.
     */
    @Redirect(method = "saveWithoutId", at = @At(value = "FIELD",
        target = "Lnet/minecraft/world/entity/Entity;vehicle:Lnet/minecraft/world/entity/Entity;"))
    private Entity preventSavingVehiclePosAsOurPos(final Entity originalVehicle) {
        // Only check this if [originalVehicle] != null
        if (originalVehicle == null) return null;

        final int vehicleChunkX = ((int) originalVehicle.position().x()) >> 4;
        final int vehicleChunkZ = ((int) originalVehicle.position().z()) >> 4;
        // Don't apply the ship render transform if the player is in the shipyard
        final boolean isVehicleInShipyard = ChunkAllocator.isChunkInShipyard(vehicleChunkX, vehicleChunkZ);
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
