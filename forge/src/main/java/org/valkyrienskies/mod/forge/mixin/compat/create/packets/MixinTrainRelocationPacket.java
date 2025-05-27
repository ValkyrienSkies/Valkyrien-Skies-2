package org.valkyrienskies.mod.forge.mixin.compat.create.packets;

import com.simibubi.create.content.trains.entity.TrainRelocationPacket;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(TrainRelocationPacket.class)
public abstract class MixinTrainRelocationPacket {
    @Unique
    private Level level;

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean redirectCloserThan(final Vec3 instance, final Position arg, final double d) {
        Vec3 newVec3 = (Vec3) arg;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(this.level, arg);
        if (ship != null) {
            newVec3 = VSGameUtilsKt.toWorldCoordinates(ship, (Vec3) arg);
        }
        return instance.closerThan(newVec3, d);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;"))
    private Entity stealLevel(Level instance, int i) {
        return (level = instance).getEntity(i);
    }
}
