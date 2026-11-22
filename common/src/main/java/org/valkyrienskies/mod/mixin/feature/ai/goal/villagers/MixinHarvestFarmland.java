package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Farmer villagers harvest by iterating world cells around villager.getX/Y/Z; ship-mounted farmland lives in shipyard chunks. Project the search origin so the iteration covers shipyard cells.
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

}
