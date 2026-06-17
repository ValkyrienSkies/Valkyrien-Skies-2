package org.valkyrienskies.mod.mixin.feature.ai.goal.allay;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// shouldStopDancing's distance check compares shipyard jukeboxPos to world allay pos and
// instantly stops; project both via the Vec3 overload of toWorldCoordinates (corner-safe —
// vanilla's BlockPos.containing(toWorldCoordinates(level, blockPos)) lands on the wrong
// cell for non-integer-offset / rotated ships) and do a manual squared-distance check.
@Mixin(Allay.class)
public abstract class MixinAllay {

    @WrapOperation(
        method = "shouldStopDancing",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$projectJukeboxDistance(
        final BlockPos instance, final Position position, final double range,
        final Operation<Boolean> original
    ) {
        final Level level = ((Allay) (Object) this).level();
        final Vec3 jukeboxWorld = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(instance));
        final Vec3 allayWorld = VSGameUtilsKt.toWorldCoordinates(
            level, new Vec3(position.x(), position.y(), position.z()));
        final double dx = jukeboxWorld.x - allayWorld.x;
        final double dy = jukeboxWorld.y - allayWorld.y;
        final double dz = jukeboxWorld.z - allayWorld.z;
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
