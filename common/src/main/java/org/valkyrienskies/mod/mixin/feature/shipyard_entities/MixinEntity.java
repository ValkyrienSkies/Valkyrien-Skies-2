package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.entity.handling.WorldEntityHandler;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract void setPosRaw(double d, double e, double f);

    @Shadow
    public abstract void positionRider(Entity entity);

    @Shadow
    public abstract void teleportTo(double d, double e, double f);

    @Shadow
    public abstract boolean teleportTo(ServerLevel serverLevel, double d, double e, double f, Set<RelativeMovement> set, float g, float h);

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
    private boolean isModifyingSetPos = false;

    /**
     * @author ewoudje
     * @reason use vs2 entity handler to handle this method
     */
    @Inject(method = "setPosRaw", at = @At(value = "HEAD"), cancellable = true)
    private void handlePosSet(final double x, final double y, final double z, final CallbackInfo ci) {
        final Level level = level();
        //noinspection ConstantValue
        if (!Player.class.isInstance(this) || level == null || isModifyingSetPos ||
            !VSGameUtilsKt.isBlockInShipyard(level, x, y, z)) {
            return;
        }

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, x, y, z);
        if (ship != null) {
            isModifyingSetPos = true;
            WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(Entity.class.cast(this), ship, x, y, z);
            isModifyingSetPos = false;
            ci.cancel();
        }
    }

    @Unique
    private boolean isModifyingTeleport = false;

    @Inject(
        at = @At("HEAD"),
        method = "teleportTo(DDD)V",
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

    @Inject(
        at = @At("HEAD"),
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
        cancellable = true
    )
    private void beforeTeleportTo(ServerLevel serverLevel, double d, double e, double f, Set<RelativeMovement> set, float g, float h, final CallbackInfoReturnable<Boolean> ci) {
        if (isModifyingTeleport) {
            return;
        }

        ci.cancel();
        isModifyingTeleport = true;
        final Vector3d pos = VSEntityManager.INSTANCE.getHandler(Entity.class.cast(this))
            .getTeleportPos(Entity.class.cast(this), new Vector3d(d, e, f));
        teleportTo(serverLevel, pos.x, pos.y, pos.z, set, g, h);
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
    private void preSetRemoved(final RemovalReason removalReason, final CallbackInfo ci) {
        final Entity thisAsEntity = Entity.class.cast(this);
        final LoadedShip ship = VSGameUtilsKt.getShipObjectManagingPos(thisAsEntity.level(),
            VectorConversionsMCKt.toJOML(thisAsEntity.position()));
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(thisAsEntity).entityRemovedFromShipyard(thisAsEntity, ship);
        }
    }

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    public Level level;
}
