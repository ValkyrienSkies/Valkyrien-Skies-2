package org.valkyrienskies.mod.mixin.feature.container_distance_check;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(RandomizableContainerBlockEntity.class)
public class MixinRandomizableContainerBlockEntity {

    @Redirect(
        method = "stillValid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private double includeShipsInDistanceCheck(
        final Player receiver, final double x, final double y, final double z) {
        return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
    }

}
