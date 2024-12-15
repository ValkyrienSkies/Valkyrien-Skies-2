package org.valkyrienskies.mod.mixin.feature.screen_distance_check;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(AbstractContainerMenu.class)
public class MixinScreenHandler {

    // targeting lambdas is weird, thankfully there is only one usage of #squaredDistanceTo
    // lets you use crafting tables
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;distanceToSqr(DDD)D"
        ),
        require = 0
    )
    private static double includeShipsInDistanceCheck(
        final Player receiver, final double x, final double y, final double z) {
        double realX = x;
        double realY = y;
        double realZ = z;
        if (receiver instanceof IEntityDraggingInformationProvider dragProvider && dragProvider.getDraggingInformation().isEntityBeingDraggedByAShip()) {
            if (dragProvider.getDraggingInformation().getServerRelativePlayerPosition() != null) {
                realX = dragProvider.getDraggingInformation().getServerRelativePlayerPosition().x();
                realY = dragProvider.getDraggingInformation().getServerRelativePlayerPosition().y();
                realZ = dragProvider.getDraggingInformation().getServerRelativePlayerPosition().z();
            }
        }
        return VSGameUtilsKt.squaredDistanceToInclShips(receiver, realX, realY, realZ);
    }

}
