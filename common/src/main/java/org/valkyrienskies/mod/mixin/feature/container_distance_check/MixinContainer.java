package org.valkyrienskies.mod.mixin.feature.container_distance_check;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Container.class)
public interface MixinContainer {
    @Redirect(
        method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;I)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        )
    )
    private static double includeShipsInDistanceCheck(
        final Player receiver, final double x, final double y, final double z) {
        return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
    }
}
