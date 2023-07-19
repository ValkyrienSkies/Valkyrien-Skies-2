package org.valkyrienskies.mod.mixin.feature.entity_collision;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Player.class)
public abstract class MixinPlayer implements IEntityDraggingInformationProvider {
    // Allow players to crouch walk on ships
    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void preMaybeBackOffFromEdge(final Vec3 vec3, final MoverType moverType,
        final CallbackInfoReturnable<Vec3> callbackInfoReturnable) {
        if (getDraggingInformation().isEntityBeingDraggedByAShip()) {
            callbackInfoReturnable.setReturnValue(vec3);
        }
    }
}
