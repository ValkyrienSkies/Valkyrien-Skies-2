package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.Ship;
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
    public void positionRider(final Entity instance, final Entity passengerI, final Entity.MoveFunction callback) {
        this.positionRider(passengerI,
            (passenger, x, y, z) -> VSEntityManager.INSTANCE.getHandler(passenger.getType())
                .positionSetFromVehicle(passenger, Entity.class.cast(this), x, y, z));
    }

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
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

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
    @Redirect(method = "setLocationFromBoundingbox", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setPosRaw(DDD)V"))
    public void setPosHandling2(final Entity instance, final double x, final double y, final double z) {
        final Vector3d pos = new Vector3d(x, y, z);
        final Ship ship;

        if ((ship = VSGameUtilsKt.getShipObjectManagingPos(level, pos)) != null) {
            VSEntityManager.INSTANCE.getHandler(instance.getType()).onEntityMove(instance, ship, pos);
        } else {
            instance.setPosRaw(x, y, z);
        }
    }

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    public Level level;
}
