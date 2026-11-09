package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Farmer villagers harvest by iterating world cells around villager.getX/Y/Z; ship-mounted farmland lives in shipyard chunks. Project the search origin so the iteration covers shipyard cells; tick's proximity check projects the shipyard target back to world.
@Mixin(HarvestFarmland.class)
public abstract class MixinHarvestFarmland {

    @Unique
    private static final ThreadLocal<Vector3d> VS$ORIGIN =
        ThreadLocal.withInitial(() -> new Vector3d(Double.NaN, Double.NaN, Double.NaN));

    @Inject(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
        at = @At("HEAD")
    )
    private void vs$cacheShipyardOrigin(final ServerLevel level, final Villager villager,
        final CallbackInfoReturnable<Boolean> cir) {
        final Ship ship = VSGameUtilsKt.getEnclosingShip(villager);
        final Vector3d origin = VS$ORIGIN.get();
        if (ship == null) {
            origin.x = Double.NaN;
            return;
        }
        ship.getTransform().getWorldToShip().transformPosition(
            origin.set(villager.getX(), villager.getY(), villager.getZ()));
    }

    @Redirect(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;getX()D")
    )
    private double vs$shipyardSearchX(final Villager villager) {
        final Vector3d origin = VS$ORIGIN.get();
        return Double.isNaN(origin.x) ? villager.getX() : origin.x;
    }

    @Redirect(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;getY()D")
    )
    private double vs$shipyardSearchY(final Villager villager) {
        final Vector3d origin = VS$ORIGIN.get();
        return Double.isNaN(origin.x) ? villager.getY() : origin.y;
    }

    @Redirect(
        method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;getZ()D")
    )
    private double vs$shipyardSearchZ(final Villager villager) {
        final Vector3d origin = VS$ORIGIN.get();
        return Double.isNaN(origin.x) ? villager.getZ() : origin.z;
    }

    // Project the shipyard cell's CENTER (not the corner) to world so the proximity gate matches where the cell actually renders. toWorldCoordinates(Vec3) is a no-op for world coords, so the villager-side projection is safe whether the villager is in world or shipyard frame.
    @WrapOperation(
        method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
        )
    )
    private boolean vs$projectFarmlandPosForProximity(
        final BlockPos instance, final Position position, final double dist,
        final Operation<Boolean> original,
        @Local(argsOnly = true) final Villager villager
    ) {
        final Vec3 cellCenterWorld = VSGameUtilsKt.toWorldCoordinates(
            villager.level(), Vec3.atCenterOf(instance));
        final Vec3 villagerWorld = VSGameUtilsKt.toWorldCoordinates(
            villager.level(), new Vec3(position.x(), position.y(), position.z()));
        final double dx = cellCenterWorld.x - villagerWorld.x;
        final double dy = cellCenterWorld.y - villagerWorld.y;
        final double dz = cellCenterWorld.z - villagerWorld.z;
        return dx * dx + dy * dy + dz * dz < dist * dist;
    }
}
