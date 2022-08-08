package org.valkyrienskies.mod.mixin.core.dispenser;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(DefaultDispenseItemBehavior.class)
public class MixinDefaultDispenseItemBehavior {

    @Inject(at = @At("HEAD"), method = "spawnItem", cancellable = true)
    private static void beforeSpawnItem(final Level level, final ItemStack stack, final int speed,
        final Direction facing, final Position position, final CallbackInfo ci) {

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, position);

        if (ship != null) {
            final double x = position.x();
            // vanilla code subtracts 0.125 if the dispenser is facing up/down. Don't know what to do with that though
            final double y = position.y() - 0.15625;
            final double z = position.z();

            final Vector3d posInWorld = ship.getShipToWorld().transformPosition(new Vector3d(x, y, z));

            final ItemEntity item = new ItemEntity(level, posInWorld.x, posInWorld.y, posInWorld.z, stack);
            final double randomSpeed = level.random.nextDouble() * 0.1 + 0.2;

            final Vector3d facingInWorld = VectorConversionsMCKt.transformDirection(ship.getShipToWorld(), facing);
            final Vector3dc speedInWorld = ship.getVelocity();

            item.setDeltaMovement((level.random.nextGaussian() * 0.0075F * (double) speed) + speedInWorld.x()
                    + (facingInWorld.x() * randomSpeed),
                (level.random.nextGaussian() * 0.0075F * (double) speed) + speedInWorld.y()
                    + (facingInWorld.y() * randomSpeed) + 0.2F,
                (level.random.nextGaussian() * 0.0075F * (double) speed) + speedInWorld.z()
                    + (facingInWorld.z() * randomSpeed));

            level.addFreshEntity(item);

            ci.cancel();
        }
    }

}
