package org.valkyrienskies.mod.mixin.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ScreenHandler.class)
public class MixinScreenHandler {

    // targeting lambdas is weird, thankfully there is only one usage of #squaredDistanceTo
    // lets you use crafting tables
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;squaredDistanceTo(DDD)D"
        )
    )
    private static double includeShipsInDistanceCheck(
        final PlayerEntity receiver, final double x, final double y, final double z) {
        return VSGameUtilsKt.squaredDistanceToInclShips(receiver, x, y, z);
    }

}
