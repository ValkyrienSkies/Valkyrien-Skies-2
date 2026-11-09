package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Per-tick break-progress branch in RemoveBlockGoal.tick scans 6 cells around mob.blockPosition() for the target block; for a mob next to a ship-mounted target the world cells are air and the helper returns null forever. Project mob.blockPosition() to ship-local when the target is ship-claimed.
@Mixin(RemoveBlockGoal.class)
public abstract class MixinRemoveBlockGoal extends MoveToBlockGoal {

    public MixinRemoveBlockGoal(final PathfinderMob mob, final double speed, final int range) {
        super(mob, speed, range);
    }

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$projectMobPosForShipScan(
        final Mob mob, final Operation<BlockPos> original
    ) {
        final BlockPos worldPos = original.call(mob);
        final BlockPos target = this.blockPos;
        if (target == null) return worldPos;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(mob.level(), target);
        if (ship == null) return worldPos;
        final Vector3d shipLocal = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(mob.getX(), mob.getY(), mob.getZ()), VS$OUT.get()
        );
        return BlockPos.containing(shipLocal.x, shipLocal.y, shipLocal.z);
    }
}
