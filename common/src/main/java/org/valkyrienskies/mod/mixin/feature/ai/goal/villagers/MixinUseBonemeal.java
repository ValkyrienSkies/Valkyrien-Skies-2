package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Farmer villagers use bonemeal by iterating positions around villager.blockPosition() in WORLD coords looking for crop blocks; ship-mounted crops live in shipyard so the world search finds nothing. Redirect blockPosition to the villager's shipyard projection; the proximity check in tick is also corner-aware-projected.
@Mixin(UseBonemeal.class)
public abstract class MixinUseBonemeal {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Redirect(
        method = "pickNextTarget",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/Villager;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipyardSearchOrigin(final Villager villager) {
        final BlockPos worldPos = villager.blockPosition();
        final Ship ship = VSGameUtilsKt.getEnclosingShip(villager);
        if (ship == null) return worldPos;
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(villager.getX(), villager.getY(), villager.getZ()), VS$OUT.get()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }

    // Project the shipyard cell's CENTER (not the corner) and the villager position via toWorldCoordinates(Vec3) for the proximity check; vanilla's BlockPos.containing → closerToCenterThan re-centering of the corner-only projection lands the proximity reference on a world cell adjacent to where the actual crop renders for non-integer-offset or rotated ships (the recurring corner issue).
    @WrapOperation(
        method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$projectCropPosForProximity(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final Villager villager
    ) {
        final Level level = villager.level();
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(level, Vec3.atCenterOf(instance));
        final Vec3 villagerWorld = VSGameUtilsKt.toWorldCoordinates(
            level, new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - villagerWorld.x;
        final double dy = cellCenterWorld.y - villagerWorld.y;
        final double dz = cellCenterWorld.z - villagerWorld.z;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }
}
