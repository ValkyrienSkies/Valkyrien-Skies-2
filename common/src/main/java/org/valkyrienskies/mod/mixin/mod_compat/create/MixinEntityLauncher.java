package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.logistics.depot.EntityLauncher;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(EntityLauncher.class)
public abstract class MixinEntityLauncher {

    @Unique
    private BlockPos launcher;

    @Inject(method = "getGlobalPos", at = @At("HEAD"))
    private void harvestBlockPos(double t, Direction d, BlockPos launcher, CallbackInfoReturnable<Vec3> cir) {
        this.launcher = launcher;
    }

    @ModifyVariable(method = "getGlobalPos", at = @At("STORE"), name = "start")
    private Vec3 modStart(Vec3 value) {
        return new Vec3(launcher.getX() + .5, launcher.getY() + .5, launcher.getZ() + .5);
    }

    @Redirect(method = "applyMotion", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"))
    private void redirectSetDeltaMovement(Entity instance, double x, double y, double z) {
        instance.setDeltaMovement(outMotion(instance, new Vec3(x, y, z)));
    }

    @Unique
    private Vec3 outMotion(Entity entity, Vec3 motion) {
        if (entity instanceof final IEntityDraggingInformationProvider dragProvider) {
            if (dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
                long shipId = Objects.requireNonNull(dragProvider.getDraggingInformation().getLastShipStoodOn(), "what");
                // this is safe, as isEntityBeingDraggedByAShip will not return true if the ship id is null
                final Ship ship = ValkyrienSkies.getShipById(entity.level(), shipId);
                if (ship != null) {
                    Vector3d tempVec = ValkyrienSkies.toJOML(motion);
                    ship.getTransform().getShipToWorld().transformDirection(tempVec);
                    motion = ValkyrienSkies.toMinecraft(tempVec);
                }
            }
        }
        return motion;
    }
}
