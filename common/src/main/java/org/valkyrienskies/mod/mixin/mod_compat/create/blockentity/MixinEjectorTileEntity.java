package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(EjectorBlockEntity.class)
public abstract class MixinEjectorTileEntity {

    @WrapOperation(method = {"activateDeferred", "nudgeEntities"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> redirectGetEntitiesOfClass(Level instance, Class aClass, AABB aabb, Operation<List<Entity>> original) {
        // Getting shipyard entities positioned in ship coordinates and so not needing a transformed AABB.
        List<Entity> entities = original.call(instance, aClass, aabb);
        Ship ship = VSGameUtilsKt.getShipManagingPos(instance, ((EjectorBlockEntity) (Object) this).getBlockPos());
        if (ship != null) {
            AABB worldAABB = VectorConversionsMCKt.toMinecraft(
                VectorConversionsMCKt.toJOML(aabb).transform(ship.getShipToWorld())
            ).inflate(0, 0.2, 0); // Some slack space because ejectors have a weird hitbox.
            // Also adding world entities to the same list. Because of dragging they will be handled differently anyway.
            entities.addAll(
                original.call(instance, aClass, worldAABB)
            );
        }
        return entities;
    }

    @Redirect(method = "activateDeferred", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"
    ))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level(), ((EjectorBlockEntity) (Object) this).getBlockPos());
        if (ship != null) {
            BlockPos temp = ((EjectorBlockEntity) (Object) this).getBlockPos();
            Vector3d tempVec = new Vector3d(temp.getX() + .5, temp.getY() + 1, temp.getZ() + .5);
            ship.getTransform().getShipToWorld().transformPosition(tempVec, tempVec);
            instance.setPos(tempVec.x, tempVec.y, tempVec.z);
        } else {
            instance.setPos(x, y, z);
        }
    }
}
