package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.joml.primitives.AABBic;
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
        final LoadedShip ship = VSGameUtilsKt.getLoadedShipManagingPos(thisAsEntity.level(),
            VectorConversionsMCKt.toJOML(thisAsEntity.position()));
        if (ship != null) {
            VSEntityManager.INSTANCE.getHandler(thisAsEntity).entityRemovedFromShipyard(thisAsEntity, ship);
        }
    }

    @Inject(
        method = "move",
        at = @At("HEAD")
    )
    void leaveShipyard(CallbackInfo ci) {
        Entity e = Entity.class.cast(this);
        Ship ship = VSGameUtilsKt.getShipManaging(e);
        if (ship != null) {
            AABBic shipAABBi = ship.getShipAABB();
            if (shipAABBi == null || !shipAABBi.isValid()) {
                // Only happens after the ship was broken. Move entities to world unconditionally.
                WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(Entity.class.cast(this), ship);
                return;
            }

            // JANK: If the ship does not protrude vertically, entities like minecarts might end up on top of the ship,
            // and thus outside its bounding box. If we expand the bounding box too much, our minecarts will dismount
            // the ship rather late. We can't use e.isOnRails() as it's reset like once a second. Combined with the
            // minecarts joining the ships once they are close enough, an early dismount will result in an infinite loop
            // which is really the worst case, especially if some other entity is riding the minecart.

            // Right now the compromise is to extend the ship AABB just a bit and do other checks.

            AABB eBB = e.getBoundingBox();
            AABB shipAABB = new AABB(
                shipAABBi.minX(), shipAABBi.minY(), shipAABBi.minZ(),
                shipAABBi.maxX(), shipAABBi.maxY(), shipAABBi.maxZ()
            ).inflate(-0.25, 1.5, -0.25);

            if (!shipAABB.intersects(eBB) && e.flyDist > 0.05) {
                WorldEntityHandler.INSTANCE.moveEntityFromShipyardToWorld(Entity.class.cast(this), ship);
            }
        }
    }

    @Shadow
    protected abstract void positionRider(Entity passenger, Entity.MoveFunction callback);

    @Shadow
    public Level level;

    /**
     * @author Bunting_chj
     * @reason Allow entities to be loaded to shipyard
     */
    @WrapMethod(
        method = "load"
    )
    private void loadShipyard(CompoundTag compoundTag, Operation<Void> original){
        isModifyingSetPos = true;
        original.call(compoundTag);
        isModifyingSetPos = false;
    }
}
