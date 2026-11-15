package org.valkyrienskies.mod.mixin.feature.ship_walk_animation;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.mixinducks.client.ShipWalkAnimation;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements ShipWalkAnimation {
    @Shadow
    protected abstract void updateWalkAnimation(float distance);

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void vs$deferShipWalkAnimation(final boolean includeVertical, final CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        final EntityDraggingInformation info = ((IEntityDraggingInformationProvider) self).getDraggingInformation();
        if (info.getUsesShipInterpolation() && info.isEntityBeingDraggedByAShip()
            && !self.isControlledByLocalInstance() && EntityDragger.isDraggable(self)) {
            // LivingEntity.calculateEntityAnimation runs during the entity tick (for example,
            // RemotePlayer.tick calls it after super.tick). VS applies the replacement position
            // interpolation later, in Minecraft.tick's tail, so measuring displacement here
            // would miss the entity's walk across the deck.
            info.setPendingWalkAnimation(includeVertical);
            ci.cancel();
        }
    }

    @Override
    public void vs$updateShipWalkAnimation(final float distance) {
        updateWalkAnimation(distance);
    }
}
