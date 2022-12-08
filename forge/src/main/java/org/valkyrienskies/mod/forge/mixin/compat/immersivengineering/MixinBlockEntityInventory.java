package org.valkyrienskies.mod.forge.mixin.compat.immersivengineering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.gui.BlockEntityInventory")
public class MixinBlockEntityInventory {

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        ),
        method = "isValidForPlayer"
    )
    private static double redirectDistanceCheck(final Vec3 b, final Vec3 p, final Operation<Double> distanceToSqr,
        final BlockEntity entity, final Player player) {
        if (entity.getLevel() != null) {
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(entity.getLevel(), b.x, b.y, b.z, p.x, p.y, p.z);
        }

        return distanceToSqr.call(b, p);
    }

}
