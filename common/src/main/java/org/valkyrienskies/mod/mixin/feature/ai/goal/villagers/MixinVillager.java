package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Villager.spawnGolemIfNeeded calls SpawnUtil.trySpawnMob(IRON_GOLEM, ..., villager.blockPosition(), ...) — for a ship-mounted villager the seed is a world coord, world cells are air, and the iron golem never spawns. Redirect blockPosition to the villager's shipyard projection so SpawnUtil searches the ship's chunks.
@Mixin(Villager.class)
public abstract class MixinVillager {

    @Unique
    private static final ThreadLocal<Vector3d> VS$IN = ThreadLocal.withInitial(Vector3d::new);
    @Unique
    private static final ThreadLocal<Vector3d> VS$OUT = ThreadLocal.withInitial(Vector3d::new);

    @Redirect(
        method = "spawnGolemIfNeeded",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/Villager;blockPosition()Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos vs$shipyardSpawnSeed(final Villager villager) {
        final Ship ship = VSGameUtilsKt.getEnclosingShip(villager);
        if (ship == null) return villager.blockPosition();
        final Vector3d shipyard = ship.getTransform().getWorldToShip().transformPosition(
            VS$IN.get().set(villager.getX(), villager.getY(), villager.getZ()), VS$OUT.get()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }
}
