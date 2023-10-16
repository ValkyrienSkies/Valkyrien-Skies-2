package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.simibubi.create.content.logistics.depot.EjectorBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(EjectorBlockEntity.class)
public abstract class MixinEjectorTileEntity {
    @ModifyVariable(method = "*", at = @At("STORE"), name = "ejectVec")
    private Vec3 modEjectVec(Vec3 ejectVec) {
        Vec3 result = ejectVec;
        Level level = ((EjectorBlockEntity) (Object) this).getLevel();
        if (level != null) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, ((EjectorBlockEntity) (Object) this).getBlockPos());
            if (ship != null) {
                Vector3d tempVec = new Vector3d();
                ship.getTransform().getShipToWorld().transformPosition(result.x, result.y, result.z, tempVec);
                result = new Vec3(tempVec.x, tempVec.y, tempVec.z);
            }
        }
        return result;
    }

    @ModifyVariable(method = "*", at = @At("STORE"), name = "ejectMotionVec")
    private Vec3 modEjectMotionVec(Vec3 ejectMotionVec) {
        Vec3 result = ejectMotionVec;
        Level level = ((EjectorBlockEntity) (Object) this).getLevel();
        if (level != null) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, ((EjectorBlockEntity) (Object) this).getBlockPos());
            if (ship != null) {
                Vector3d tempVec = new Vector3d();
                ship.getTransform().getShipToWorld().transformDirection(result.x, result.y, result.z, tempVec);
                result = new Vec3(tempVec.x, tempVec.y, tempVec.z);
            }
        }
        return result;
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> redirectGetEntitiesOfClass(Level instance, Class<Entity> aClass, AABB aabb) {
        return instance.getEntitiesOfClass(aClass, VSGameUtilsKt.transformAabbToWorld(instance, aabb));
    }

    @Redirect(method = "activateDeferred", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"
    ))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level, ((EjectorBlockEntity) (Object) this).getBlockPos());
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
