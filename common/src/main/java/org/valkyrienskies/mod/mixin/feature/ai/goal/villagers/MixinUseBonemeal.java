package org.valkyrienskies.mod.mixin.feature.ai.goal.villagers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// Farmer villagers use bonemeal by iterating positions around villager.blockPosition() in WORLD coords looking for crop blocks; ship-mounted crops live in shipyard so the world search finds nothing. Redirect blockPosition to the villager's shipyard projection.
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

}
