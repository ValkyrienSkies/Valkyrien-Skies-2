package org.valkyrienskies.mod.mixin.feature.ai.goal.turtle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Turtles remember a HOME BlockPos (spawn or egg-laying site) — vanilla assumes world coords. For a turtle on a ship the home should be shipyard so the natural sand tile tracks the ship's motion. setHomePos projects world→shipyard if the turtle is on a ship; getHomePos projects shipyard→current-world per-read so callers see a position that tracks the ship.
@Mixin(Turtle.class)
public abstract class MixinTurtle {

    @ModifyVariable(method = "setHomePos", at = @At("HEAD"), argsOnly = true)
    private BlockPos vs$projectHomePosToShipyard(final BlockPos worldPos) {
        final Turtle turtle = (Turtle) (Object) this;
        // Geometric (not dragger) — setHomePos is called at finalizeSpawn before the dragger is populated.
        final Ship ship = VSGameUtilsKt.getShipStoodOn(turtle);
        if (ship == null) return worldPos;
        // Egg-laying flow already stores a shipyard pos directly via MixinMoveToBlockGoal — leave alone.
        if (ship.getChunkClaim().contains(worldPos.getX() >> 4, worldPos.getZ() >> 4)) {
            return worldPos;
        }
        final Vector3d local = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
            new Vector3d()
        );
        return BlockPos.containing(local.x, local.y, local.z);
    }

    @Inject(method = "getHomePos", at = @At("RETURN"), cancellable = true)
    private void vs$projectHomePosToWorld(final CallbackInfoReturnable<BlockPos> cir) {
        final BlockPos stored = cir.getReturnValue();
        if (stored == null) return;
        final Turtle turtle = (Turtle) (Object) this;
        // Vec3 overload of toWorldCoordinates is corner-safe; no-op for non-shipyard positions.
        final Vec3 worldVec = VSGameUtilsKt.toWorldCoordinates(turtle.level(), Vec3.atCenterOf(stored));
        // Identity (not in a ship claim): leave the original BlockPos to avoid allocation.
        if (worldVec.x == stored.getX() + 0.5 && worldVec.y == stored.getY() + 0.5 && worldVec.z == stored.getZ() + 0.5) {
            return;
        }
        cir.setReturnValue(BlockPos.containing(worldVec.x, worldVec.y, worldVec.z));
    }
}
