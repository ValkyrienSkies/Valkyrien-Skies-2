package org.valkyrienskies.mod.mixin.feature.ai.goal.parrot;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Client-side companion to MixinLevelRendererParrotDance: Parrot.aiStep clears partyParrot when jukeboxPos.closerToCenterThan(parrot.position(), 3.46) fails — for a ship-mounted jukebox the shipyard pos vs world parrot is millions of blocks apart and the dance stops immediately.
@Mixin(Parrot.class)
public abstract class MixinParrot {

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$projectJukeboxDistance(
        final BlockPos instance, final Position position, final double range,
        final Operation<Boolean> original
    ) {
        final Parrot self = (Parrot) (Object) this;
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(self.level(), Vec3.atCenterOf(instance));
        final Vec3 parrotWorld = VSGameUtilsKt.toWorldCoordinates(
            self.level(), new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - parrotWorld.x;
        final double dy = cellCenterWorld.y - parrotWorld.y;
        final double dz = cellCenterWorld.z - parrotWorld.z;
        return dx * dx + dy * dy + dz * dz < range * range;
    }
}
