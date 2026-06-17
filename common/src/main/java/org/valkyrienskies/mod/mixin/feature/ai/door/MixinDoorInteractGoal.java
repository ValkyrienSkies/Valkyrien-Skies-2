package org.valkyrienskies.mod.mixin.feature.ai.door;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

// Two ship-compat gaps in DoorInteractGoal.canUse:
//   1. The 1.5-block distance gate compares mob.world vs doorPos.shipyard for InShip-frame
//      paths — squared distance is ~10¹⁴ and every node is skipped. Project doorPos to
//      world before the distanceToSqr call.
//   2. For World-frame paths overlaying a ship door, vanilla's getBlockState read goes to
//      the world cell (air) rather than the ship's chunk, so the door-tag check fails and
//      the mob walks into the (solid via PathNavigationRegion overlay) door instead of
//      opening/breaking it. Inject a fallback: scan the same path-node window for a
//      ship-mounted wooden door, then set this.doorPos to the shipyard cell so downstream
//      BreakDoorGoal.tick's level.removeBlock writes to the correct chunk.
@Mixin(DoorInteractGoal.class)
public abstract class MixinDoorInteractGoal {

    @Shadow protected Mob mob;
    @Shadow protected BlockPos doorPos;
    @Shadow protected boolean hasDoor;

    @WrapOperation(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;distanceToSqr(DDD)D"
        )
    )
    private double vs$canUseDistanceProjected(
        final Mob instance, final double x, final double y, final double z,
        final Operation<Double> original
    ) {
        if (!VSGameConfig.SERVER.getAiOnShips()) return original.call(instance, x, y, z);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(instance.level(), this.doorPos);
        if (ship == null) return original.call(instance, x, y, z);
        final Vector3d worldPos = ship.getTransform().getShipToWorld().transformPosition(
            new Vector3d(this.doorPos.getX() + 0.5, this.doorPos.getY() + 0.5, this.doorPos.getZ() + 0.5),
            new Vector3d()
        );
        return original.call(instance, worldPos.x, y, worldPos.z);
    }

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void vs$canUseShipDoorFallback(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (!VSGameConfig.SERVER.getAiOnShips()) return;
        if (!GoalUtils.hasGroundPathNavigation(this.mob)) return;
        if (!this.mob.horizontalCollision) return;

        final GroundPathNavigation gpn = (GroundPathNavigation) this.mob.getNavigation();
        final Path path = gpn.getPath();
        if (path == null || path.isDone() || !gpn.canOpenDoors()) return;

        final Level level = this.mob.level();
        final int upper = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
        for (int i = 0; i < upper; i++) {
            final Node node = path.getNode(i);
            final BlockPos worldDoorPos = new BlockPos(node.x, node.y + 1, node.z);
            final double cx = worldDoorPos.getX() + 0.5;
            final double cy = worldDoorPos.getY() + 0.5;
            final double cz = worldDoorPos.getZ() + 0.5;
            final AABBd probe = new AABBd(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5);
            final Vector3d worldCenter = new Vector3d(cx, cy, cz);
            for (final Ship ship : VSGameUtilsKt.getShipsIntersecting(level, probe)) {
                if (!ship.getWorldAABB().containsPoint(worldCenter)) continue;
                final Vector3d local = ship.getTransform().getWorldToShip()
                    .transformPosition(worldCenter, new Vector3d());
                final BlockPos shipLocal = BlockPos.containing(local.x, local.y, local.z);
                final BlockState state = level.getBlockState(shipLocal);
                if (!DoorBlock.isWoodenDoor(state)) continue;
                // Mirror vanilla's 1.5-block (sqrt(2.25)) horizontal radius from the mob.
                final double dx = cx - this.mob.getX();
                final double dz = cz - this.mob.getZ();
                if (dx * dx + dz * dz > 2.25) continue;
                this.doorPos = shipLocal;
                this.hasDoor = true;
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
