package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(MoveToBlockGoal.class)
public class MixinMoveToBlockGoal {

    @Shadow
    @Final
    protected PathfinderMob mob;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"))
    private boolean onCloserToCenterThan(BlockPos instance, Position position, double v, Operation<Boolean> original) {
        return original.call(BlockPos.containing(VSGameUtilsKt.toWorldCoordinates(this.mob.level(), instance)), position, v);
    }

    @Redirect(
        method = "findNearestBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipyardSearchOrigin(final PathfinderMob mob) {
        final BlockPos worldPos = mob.blockPosition();
        if (!VSGameConfig.SERVER.getAiOnShips()) return worldPos;
        final Level level = mob.level();
        if (level == null) return worldPos;
        final Ship ship = vs$mobShip(level, mob, worldPos);
        if (ship == null) return worldPos;
        if (ship.getChunkClaim().contains(worldPos.getX() >> 4, worldPos.getZ() >> 4)) return worldPos;
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            new Vector3d(mob.getX(), mob.getY(), mob.getZ()), new Vector3d()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }

    @WrapOperation(
        method = "findNearestBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/PathfinderMob;isWithinRestriction(Lnet/minecraft/core/BlockPos;)Z"
        )
    )
    private boolean vs$projectRestrictionCheck(
        final PathfinderMob mob, final BlockPos candidate, final Operation<Boolean> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(mob, candidate);
        final Level level = mob.level();
        if (level == null) return original.call(mob, candidate);
        final Ship ship = vs$mobShip(level, mob, mob.blockPosition());
        if (ship == null) return original.call(mob, candidate);
        final Vector3d world = ship.getShipToWorld().transformPosition(
            new Vector3d(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5),
            new Vector3d()
        );
        return original.call(mob, BlockPos.containing(world.x, world.y, world.z));
    }

    @Unique
    private static Ship vs$mobShip(final Level level, final PathfinderMob mob, final BlockPos worldPos) {
        final Ship shipyardShip = VSGameUtilsKt.getShipManagingPos(level, worldPos);
        if (shipyardShip != null) return shipyardShip;
        if (mob instanceof IEntityDraggingInformationProvider provider) {
            final EntityDraggingInformation info = provider.getDraggingInformation();
            if (info.isEntityBeingDraggedByAShip()) {
                final Long shipId = info.getLastShipStoodOn();
                if (shipId != null) {
                    return VSGameUtilsKt.getAllShips(level).getById(shipId);
                }
            }
        }
        return null;
    }
}
