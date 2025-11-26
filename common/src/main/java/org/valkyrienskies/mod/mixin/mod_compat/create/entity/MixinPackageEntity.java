package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.box.PackageEntity;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(PackageEntity.class)
public abstract class MixinPackageEntity extends LivingEntity {

    protected MixinPackageEntity(EntityType<? extends LivingEntity> entityType,
        Level level) {
        super(entityType, level);
    }

    /**
     * Sets the position of the Package Entity to world and get dragged by the ship in case it was spawned in shipyard.
     */
    @WrapMethod(
        method = "fromItemStack"
    )
    private static PackageEntity wrapFromItemStack(Level world, Vec3 position, ItemStack itemstack,
        Operation<PackageEntity> original){
        final Ship ship = VSGameUtilsKt.getShipManagingPos(world, position.x, position.y, position.z);
        if(ship != null) {
            final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(position));
            PackageEntity result = original.call(world, VectorConversionsMCKt.toMinecraft(worldPos), itemstack);
            ((IEntityDraggingInformationProvider)result).getDraggingInformation().setLastShipStoodOn(ship.getId());
            return result;
        }
        return original.call(world, position, itemstack);
    }

    /**
     * Sets the position of the Package Entity to world and get dragged by the ship in case it was spawned from an ItemEntity in shipyard.
     */
    @WrapMethod(
        method = "fromDroppedItem"
    )
    private static PackageEntity wrapFromDroppedItem(Level world, Entity originalEntity, ItemStack itemstack,
        Operation<PackageEntity> original){
        final Ship ship = VSGameUtilsKt.getShipManaging(originalEntity);
        if(ship != null) {
            final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(originalEntity.position()));
            originalEntity.setPos(VectorConversionsMCKt.toMinecraft(worldPos));
            PackageEntity result = original.call(world, originalEntity, itemstack);
            ((IEntityDraggingInformationProvider)result).getDraggingInformation().setLastShipStoodOn(ship.getId());
            return result;
        }
        return original.call(world, originalEntity, itemstack);
    }

    @WrapMethod(
        method = "centerPackage"
    )
    private static boolean wrapCenterPackageToShip(Entity entity, Vec3 target, Operation<Boolean> original){
        final Ship ship = VSGameUtilsKt.getShipManagingPos(entity.level(), target.x, target.y, target.z);
        if(ship != null) {
            final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(target));
            ((IEntityDraggingInformationProvider)entity).getDraggingInformation().setLastShipStoodOn(ship.getId());
            return original.call(entity, VectorConversionsMCKt.toMinecraft(worldPos));
        }
        else return original.call(entity, target);
    }

    @WrapOperation(
        method = "travel",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/logistics/box/PackageEntity;collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;")
    )
    private Vec3 redirectToShipCollision(final Entity entity, final Vec3 motion, final AABB aabb, final Level level, final List<VoxelShape> list,
        Operation<Vec3> collideOriginal) {
        final EntityDraggingInformation entityDraggingInformation = ((IEntityDraggingInformationProvider)entity).getDraggingInformation();
        final Long shipId = entityDraggingInformation.getLastShipStoodOn();
        final Vec3 worldMotion = collideOriginal.call(entity, motion, aabb, level, list);
        final Vec3 adjustedMotion = EntityShipCollisionUtils.INSTANCE.adjustEntityMovementForShipCollisions(entity, worldMotion, aabb, level);
        entityDraggingInformation.setLastShipStoodOn(shipId);
        return adjustedMotion;
    }
}
