package org.valkyrienskies.mod.mixin.feature.ai.poi_sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import net.minecraft.world.entity.npc.Villager;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

// SecondaryPoiSensor scans 9×5×9 around the villager for blocks in profession.secondaryPoi() (FARMLAND for vanilla FARMER, plus modded extras) and writes matches to MEMORY_SECONDARY_JOB_SITE; for a ship-mounted farmer next to ship-mounted farmland the world cells around it are air, no FARMLAND found, downstream brain behaviors that need "where can I farm?" don't fire. Redirect blockPosition to project to shipyard when on a ship so the iteration covers the ship's cells.
@Mixin(SecondaryPoiSensor.class)
public abstract class MixinSecondaryPoiSensor {

    @Redirect(
        method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;)V",
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
            new Vector3d(villager.getX(), villager.getY(), villager.getZ()), new Vector3d()
        );
        return BlockPos.containing(shipyard.x, shipyard.y, shipyard.z);
    }
}
